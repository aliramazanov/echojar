package com.aliramazanov.echojar.agent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class GlobalIgnoresTest {

    private final ElementMatcher<TypeDescription> ignores = JdbcInstrumentation.globalIgnores(EchoConfig.parse(""));

    @Test
    void mockedJdbcTypesAreNeverInstrumented() {
        assertTrue(ignored("org.mockito.codegen.PreparedStatement$MockitoMock$1234"),
                "a mock records every call, so rewriting one corrupts someone else's stubbing");
        assertTrue(ignored("java.sql.PreparedStatement$MockitoMock$77"));
        assertTrue(ignored("org.mockito.internal.creation.bytebuddy.MockAccess"));
        assertTrue(ignored("org.easymock.internal.ClassProxyFactory"));
        assertTrue(ignored("com.example.Repo$$EnhancerByMockitoWithCGLIB$$abc"));
        assertTrue(ignored("com.example.Repo_$$_javassist_1"));
    }

    @Test
    void generatedProxiesAndTheAgentItselfAreNeverInstrumented() {
        assertTrue(ignored("jdk.proxy2.$Proxy45"));
        assertTrue(ignored("com.sun.proxy.$Proxy3"));
        assertTrue(ignored("com.aliramazanov.echojar.bootstrap.Echo"));
        assertTrue(ignored("com.aliramazanov.echojar.shaded.bytebuddy.Anything"));
        assertTrue(ignored("net.bytebuddy.agent.Installer"));
    }

    @Test
    void realDriverAndPoolTypesAreStillInstrumented() {
        assertFalse(ignored("org.postgresql.jdbc.PgPreparedStatement"));
        assertFalse(ignored("com.zaxxer.hikari.pool.HikariProxyPreparedStatement"));
        assertFalse(ignored("org.sqlite.jdbc3.JDBC3PreparedStatement"));
        assertFalse(ignored("org.h2.jdbc.JdbcConnection"));
        assertFalse(ignored("com.example.OrderService"));
    }

    private boolean ignored(String name) {
        return ignores.matches(new TypeDescription.Latent(
                name, 0, TypeDescription.Generic.OfNonGenericType.ForLoadedType
                        .of(Object.class),
                List.of()));
    }
}
