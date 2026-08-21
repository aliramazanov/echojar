package com.aliramazanov.echojar.agent;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class RequestInstrumentation {

    private static final String[] SERVLETS = {"jakarta.servlet.Servlet", "javax.servlet.Servlet"};
    private static final String[] FILTERS = {"jakarta.servlet.Filter", "javax.servlet.Filter"};

    private RequestInstrumentation() {
    }

    static AgentBuilder apply(AgentBuilder agent, EchoConfig config) {
        if (!config.units()) {
            return agent;
        }
        AgentBuilder built = agent;
        for (String servlet : SERVLETS) {
            built = boundary(built, servlet, config, "service", 2);
        }
        for (String filter : FILTERS) {
            built = boundary(built, filter, config, "doFilter", 3);
        }
        return built;
    }

    private static AgentBuilder boundary(
            AgentBuilder agent, String iface, EchoConfig config, String method, int arity) {
        return agent
                .type(declares(iface, config))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(RequestAdvice.Boundary.class)
                                .on(named(method).and(takesArguments(arity)).and(isPublic()))));
    }

    private static ElementMatcher.Junction<TypeDescription> declares(String iface, EchoConfig config) {
        return hasSuperType(named(iface)).and(not(isInterface())).and(not(config.ignoredTypes()));
    }
}
