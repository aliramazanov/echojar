package com.aliramazanov.echojar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliramazanov.echojar.bootstrap.watch.DiagnosticsMXBean;
import com.aliramazanov.echojar.fake.FakeDriver;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shop.OrderService;

class DiagnosticsBeanIT {

    private static final String NAME = "com.aliramazanov.echojar:type=Diagnostics";

    @BeforeEach
    void reset() {
        AgentState.reset();
    }

    @Test
    void theBeanIsRegisteredAndReadable() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName(NAME);

        assertTrue(server.isRegistered(name), "the agent should have registered " + NAME);

        long before = (Long) server.getAttribute(name, "Executions");

        try (Connection connection = FakeDriver.pooled()) {
            new OrderService(connection).summarise(9);
        }

        long after = (Long) server.getAttribute(name, "Executions");

        assertEquals(9, after - before, "the bean should see the nine queries just run");
    }

    @Test
    void everyAttributeIsReadableAndConsistent() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName(NAME);

        try (Connection connection = FakeDriver.pooled()) {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM bean_probe WHERE id = ?");

            for (int query = 0; query < 6; query++) {
                statement.executeQuery();
            }
        }

        long closed = (Long) server.getAttribute(name, "LeasesClosed");
        long open = (Long) server.getAttribute(name, "LeasesOpen");
        long templated = (Long) server.getAttribute(name, "StatementsTemplated");
        long transformed = (Long) server.getAttribute(name, "TypesTransformed");
        long walks = (Long) server.getAttribute(name, "StackWalks");
        long suppressed = (Long) server.getAttribute(name, "SuppressedFailures");

        boolean healthy = (Boolean) server.getAttribute(name, "Healthy");

        String[] detail = (String[]) server.getAttribute(name, "SuppressedDetail");

        assertTrue(closed >= 1, "at least the lease just closed");
        assertTrue(open >= 0);
        assertTrue(templated >= 1);
        assertTrue(transformed >= 1, "the fake driver had to be transformed to get here");
        assertTrue(walks >= 1, "resolving a call site walks the stack");
        assertEquals(0, suppressed, "nothing should have been suppressed in this run");
        assertTrue(healthy);
        assertEquals(0, detail.length);
    }

    @Test
    void theTypedProxyReadsTheSameNumbersAsTheAttributeNames() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName(NAME);

        DiagnosticsMXBean bean = JMX.newMXBeanProxy(server, name, DiagnosticsMXBean.class);

        try (Connection connection = FakeDriver.pooled()) {
            new OrderService(connection).summarise(4);
        }

        assertEquals(
                (Long) server.getAttribute(name, "Executions"),
                bean.getExecutions(),
                "a getter and its attribute name must reach the same counter"
        );

        assertEquals((Long) server.getAttribute(name, "LeasesClosed"), bean.getLeasesClosed());
        assertEquals((Long) server.getAttribute(name, "LeasesOpen"), bean.getLeasesOpen());
        assertEquals(
                (Long) server.getAttribute(name, "StatementsTemplated"),
                bean.getStatementsTemplated()
        );
        assertEquals(
                (Long) server.getAttribute(name, "TypesTransformed"),
                bean.getTypesTransformed()
        );
        assertEquals((Long) server.getAttribute(name, "StackWalks"), bean.getStackWalks());
        assertEquals(
                (Long) server.getAttribute(name, "SuppressedFailures"),
                bean.getSuppressedFailures()
        );

        assertEquals(server.getAttribute(name, "Healthy"), bean.isHealthy());
        assertEquals(
                ((String[]) server.getAttribute(name, "SuppressedDetail")).length,
                bean.getSuppressedDetail().length
        );
    }
}
