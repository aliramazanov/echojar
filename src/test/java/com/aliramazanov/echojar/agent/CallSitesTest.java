package com.aliramazanov.echojar.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliramazanov.echojar.bootstrap.findings.CallSite;
import org.junit.jupiter.api.Test;

class CallSitesTest {

    private final CallSites sites = new CallSites(EchoConfig.parse("").frameworkPrefixes(), 200);

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
        CallSite site = sites.resolve();
        assertNotNull(site, "there is always some frame above the agent");
        assertTrue(sites.isApplication(site.declaringClass()),
                "resolve must never hand back a frame it would itself reject, got " + site);
    }
}
