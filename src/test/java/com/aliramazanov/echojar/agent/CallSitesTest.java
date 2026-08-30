package com.aliramazanov.echojar.agent;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliramazanov.echojar.bootstrap.findings.CallSite;
import org.junit.jupiter.api.Test;

class CallSitesTest {

    private final CallSites sites = new CallSites(EchoConfig.parse("").frameworkPrefixes(), List.of(), 200);

    @Test
    void frameworkPackagesAreNotApplicationCode() {
        assertFalse(sites.isApplication("org.hibernate.loader.Loader"));
        assertFalse(sites.isApplication("org.springframework.jdbc.core.JdbcTemplate"));
        assertFalse(sites.isApplication("com.zaxxer.hikari.pool.ProxyConnection"));
        assertFalse(sites.isApplication("java.sql.DriverManager"));
    }

    @Test
    void hibernateLazyProxiesAreNotApplicationCode() {
        assertFalse(sites.isApplication("com.example.Customer$HibernateProxy$mFdBHtQ7"),
                "Hibernate writes its lazy proxy into the entity's own package");
    }

    @Test
    void springCglibSubclassesAreNotApplicationCode() {
        assertFalse(sites.isApplication("com.example.DashboardService$$SpringCGLIB$$0"));
        assertFalse(sites.isApplication("com.example.Repo$$EnhancerBySpringCGLIB$$abc"));
    }

    @Test
    void jdkProxiesAndLambdasAreNotApplicationCode() {
        assertFalse(sites.isApplication("jdk.proxy2.$Proxy45"));
        assertFalse(sites.isApplication("com.example.Service$$Lambda$14/0x1234"));
    }

    @Test
    void ordinaryApplicationClassesSurvive() {
        assertTrue(sites.isApplication("com.example.DashboardService"));
        assertTrue(sites.isApplication("com.example.OrderService"));
    }

    @Test
    void nestedClassesAreStillApplicationCode() {
        assertTrue(sites.isApplication("com.example.OrderService$Batch"),
                "a single dollar is an ordinary nested class, not generated code");
        assertTrue(sites.isApplication("com.example.Outer$Inner$Deep"));
    }

    @Test
    void resolvingReturnsAFrameThatPassedItsOwnFilter() {
        CallSites named = new CallSites(
                EchoConfig.parse("").frameworkPrefixes(), List.of("com.aliramazanov.echojar.agent."), 200);
        CallSite site = named.resolve();
        assertNotNull(site, "this test class is the application here");
        assertTrue(named.isApplication(site.declaringClass()),
                "resolve must never hand back a frame it would itself reject, got " + site);
    }

    @Test
    void aStackWithNoApplicationFrameResolvesToNothing() {
        assertNull(sites.resolve(),
                "every frame here is the agent or the test runner, so there is no call site to name");
    }

    @Test
    void namingTheApplicationBeatsTheFrameworkList() {
        CallSites named = new CallSites(EchoConfig.parse("").frameworkPrefixes(), List.of("com.acme."), 200);
        assertTrue(named.isApplication("com.acme.OrderService"));
        assertFalse(named.isApplication("com.example.OrderService"),
                "an unlisted package is framework once the application names itself");
        assertFalse(named.isApplication("org.thymeleaf.spring6.util.SpringSelectedValueComparator"),
                "no blocklist can keep up with every framework, so the allowlist decides");
    }

    @Test
    void generatedCodeInTheApplicationsOwnPackageIsStillNotTheCallSite() {
        CallSites named = new CallSites(EchoConfig.parse("").frameworkPrefixes(), List.of("demo."), 200);
        assertFalse(named.isApplication("demo.$Proxy116"),
                "a proxy of a package private interface is defined in that very package");
        assertFalse(named.isApplication("demo.Customer$HibernateProxy$aB3"),
                "Hibernate writes its lazy proxy into the entity's own package");
        assertFalse(named.isApplication("demo.OrderService$$SpringCGLIB$$0"),
                "and Spring writes its subclass into the bean's");
        assertTrue(named.isApplication("demo.OrderService"), "the class someone wrote still counts");
    }
}
