package com.aliramazanov.echojar.agent;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;


import com.aliramazanov.echojar.bootstrap.findings.Lease;
import com.aliramazanov.echojar.bootstrap.LeaseCarrier;
import com.aliramazanov.echojar.bootstrap.SqlCarrier;
import com.aliramazanov.echojar.bootstrap.findings.SqlTemplate;
import com.aliramazanov.echojar.bootstrap.StatementCarrier;
import java.util.List;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.RawMatcher;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.matcher.ElementMatcher;

final class JdbcInstrumentation {

    private static final String CONNECTION = "java.sql.Connection";
    private static final String STATEMENT = "java.sql.Statement";
    private static final String PREPARED = "java.sql.PreparedStatement";

    private static final String LEASE_FIELD = "echojar$lease";
    private static final String TEMPLATE_FIELD = "echojar$template";
    private static final String OWNER_FIELD = "echojar$owner";
    private static final String BATCH_FIELD = "echojar$batch";
    private static final String BATCH_SQL_FIELD = "echojar$batchSql";

    private JdbcInstrumentation() {
    }

    static AgentBuilder apply(AgentBuilder agent, EchoConfig config, Mode mode) {
        AgentBuilder built = agent;
        if (!mode.frozen()) {
            built = connectionFields(built, config);
            built = preparedFields(built, config);
            built = statementFields(built, config);
        }
        built = connectionAdvice(built, config);
        built = executeAdvice(built, config);
        built = preparedBatchAdvice(built, config);
        built = statementBatchAdvice(built, config);
        return built;
    }

    private static AgentBuilder connectionFields(AgentBuilder agent, EchoConfig config) {
        return agent
                .type(notYetLoaded(concrete(CONNECTION, config)))
                .transform((builder, type, loader, module, domain) -> builder
                        .implement(LeaseCarrier.class)
                        .defineField(LEASE_FIELD, Lease.class, Visibility.PRIVATE, FieldManifestation.VOLATILE)
                        .method(named("echojarLease"))
                        .intercept(FieldAccessor.ofField(LEASE_FIELD)));
    }

    private static AgentBuilder connectionAdvice(AgentBuilder agent, EchoConfig config) {
        return agent
                .type(declares(CONNECTION, config))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(JdbcAdvice.CloseConnection.class)
                                .on(named("close").and(takesNoArguments()).and(isPublic())))
                        .visit(Advice.to(JdbcAdvice.Prepare.class)
                                .on(namedOneOf("prepareStatement", "prepareCall")
                                        .and(takesArgument(0, String.class))
                                        .and(isPublic()))));
    }

    private static AgentBuilder preparedFields(AgentBuilder agent, EchoConfig config) {
        return agent
                .type(notYetLoaded(concrete(PREPARED, config)))
                .transform((builder, type, loader, module, domain) -> builder
                        .implement(SqlCarrier.class)
                        .defineField(TEMPLATE_FIELD, SqlTemplate.class, Visibility.PRIVATE)
                        .defineField(OWNER_FIELD, LeaseCarrier.class, Visibility.PRIVATE)
                        .defineField(BATCH_FIELD, int.class, Visibility.PRIVATE)
                        .method(named("echojarTemplate"))
                        .intercept(FieldAccessor.ofField(TEMPLATE_FIELD))
                        .method(named("echojarOwner"))
                        .intercept(FieldAccessor.ofField(OWNER_FIELD))
                        .method(named("echojarBatch"))
                        .intercept(FieldAccessor.ofField(BATCH_FIELD)));
    }

    private static AgentBuilder executeAdvice(AgentBuilder agent, EchoConfig config) {
        return agent
                .type(declares(PREPARED, config))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(JdbcAdvice.ExecutePrepared.class)
                                .on(namedOneOf("execute", "executeQuery", "executeUpdate", "executeLargeUpdate")
                                        .and(takesNoArguments())
                                        .and(isPublic()))))
                .type(declares(STATEMENT, config).and(not(hasSuperType(named(PREPARED)))))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(JdbcAdvice.ExecuteStatement.class)
                                .on(namedOneOf("execute", "executeQuery", "executeUpdate", "executeLargeUpdate")
                                        .and(takesArgument(0, String.class))
                                        .and(isPublic()))));
    }

    private static AgentBuilder preparedBatchAdvice(AgentBuilder agent, EchoConfig config) {
        return agent
                .type(declares(PREPARED, config))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(JdbcAdvice.AddBatchPrepared.class)
                                .on(named("addBatch").and(takesNoArguments()).and(isPublic())))
                        .visit(Advice.to(JdbcAdvice.ExecuteBatchPrepared.class)
                                .on(namedOneOf("executeBatch", "executeLargeBatch")
                                        .and(takesNoArguments())
                                        .and(isPublic())))
                        .visit(Advice.to(JdbcAdvice.ClearBatch.class)
                                .on(named("clearBatch").and(takesNoArguments()).and(isPublic()))));
    }

    private static AgentBuilder statementFields(AgentBuilder agent, EchoConfig config) {
        return agent
                .type(notYetLoaded(concrete(STATEMENT, config).and(not(hasSuperType(named(PREPARED))))))
                .transform((builder, type, loader, module, domain) -> builder
                        .implement(StatementCarrier.class)
                        .defineField(BATCH_SQL_FIELD, List.class, Visibility.PRIVATE)
                        .method(named("echojarBatchSql"))
                        .intercept(FieldAccessor.ofField(BATCH_SQL_FIELD)));
    }

    private static AgentBuilder statementBatchAdvice(AgentBuilder agent, EchoConfig config) {
        ElementMatcher.Junction<TypeDescription> plain =
                declares(STATEMENT, config).and(not(hasSuperType(named(PREPARED))));
        return agent
                .type(plain)
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(JdbcAdvice.AddBatchStatement.class)
                                .on(named("addBatch").and(takesArguments(1)).and(takesArgument(0, String.class)).and(isPublic())))
                        .visit(Advice.to(JdbcAdvice.ExecuteBatchStatement.class)
                                .on(namedOneOf("executeBatch", "executeLargeBatch")
                                        .and(takesNoArguments())
                                        .and(isPublic())))
                        .visit(Advice.to(JdbcAdvice.ClearBatch.class)
                                .on(named("clearBatch").and(takesNoArguments()).and(isPublic()))));
    }

    private static RawMatcher notYetLoaded(ElementMatcher<? super TypeDescription> types) {
        return (type, loader, module, loaded, domain) -> loaded == null && types.matches(type);
    }

    private static ElementMatcher.Junction<TypeDescription> concrete(String iface, EchoConfig config) {
        return declares(iface, config).and(not(isAbstract()));
    }

    private static ElementMatcher.Junction<TypeDescription> declares(String iface, EchoConfig config) {
        return hasSuperType(named(iface)).and(not(isInterface())).and(not(config.ignoredTypes()));
    }

    static ElementMatcher.Junction<TypeDescription> globalIgnores(EchoConfig config) {
        return nameStartsWith("com.aliramazanov.echojar.shaded.")
                .or(nameStartsWith("com.aliramazanov.echojar.bootstrap."))
                .or(nameStartsWith("com.aliramazanov.echojar.agent."))
                .or(nameStartsWith("com.sun.proxy."))
                .or(nameStartsWith("jdk.proxy"))
                .or(nameContains("$Proxy"))
                .or(nameStartsWith("net.bytebuddy."))
                .or(nameStartsWith("jdk.internal."))
                .or(nameStartsWith("sun.reflect."))
                .or(isSynthetic())
                .or(config.ignoredTypes());
    }
}
