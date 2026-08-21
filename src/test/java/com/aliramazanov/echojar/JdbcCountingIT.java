package com.aliramazanov.echojar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliramazanov.echojar.bootstrap.findings.Finding;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.fake.Db;
import com.aliramazanov.echojar.fake.FakeConnection;
import com.aliramazanov.echojar.fake.FakeDriver;
import com.aliramazanov.echojar.fake.RogueConnection;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shop.OrderService;

class JdbcCountingIT {

    @BeforeEach
    void reset() {
        Ledger.reset();
        Db.reset();
    }

    @Test
    void agentIsActuallyInstalled() {
        assertEquals(null, Ledger.class.getClassLoader(), "ledger must come from the bootstrap loader");
    }

    @Test
    void preparedStatementExecutionsAreCountedOnce() throws SQLException {
        try (Connection connection = new FakeConnection()) {
            new OrderService(connection).summarise(7);
        }
        assertEquals(7, Db.count("SELECT * FROM order_item WHERE order_id = ?"), "driver saw 7 executions");
        assertEquals(7, only().peakPerLease());
    }

    @Test
    void poolWrapperDoesNotDoubleCount() throws SQLException {
        try (Connection connection = FakeDriver.pooled()) {
            new OrderService(connection).summarise(7);
        }
        assertEquals(7, Db.count("SELECT * FROM order_item WHERE order_id = ?"), "driver saw 7 executions");
        assertEquals(7, only().peakPerLease(), "a statement wrapped twice must be counted once");
    }

    @Test
    void batchExpandsToOneExecutionPerStatement() throws SQLException {
        try (Connection connection = FakeDriver.pooled()) {
            new OrderService(connection).insertBatch(9);
        }
        assertEquals(9, Db.executed().size(), "driver saw 9 batched executions");
        assertEquals(9, only().peakPerLease(), "a batch of 9 is 9 executions, not 1");
    }

    @Test
    void plainStatementLiteralsCollapseIntoOneTemplate() throws SQLException {
        try (Connection connection = new FakeConnection()) {
            new OrderService(connection).summariseWithLiterals(6);
        }
        Finding finding = only();
        assertEquals(6, finding.peakPerLease(), "inlined literals must group into one template");
        assertEquals("SELECT * FROM order_item WHERE order_id = ?", finding.template().text());
    }

    @Test
    void noiseQueriesAreSuppressed() throws SQLException {
        try (Connection connection = new FakeConnection()) {
            OrderService service = new OrderService(connection);
            for (int i = 0; i < 10; i++) {
                service.ping();
            }
        }
        assertEquals(10, Db.count("SELECT 1"), "the driver still ran them");
        assertEquals(List.of(), Ledger.findings(), "validation pings are not echoes");
    }

    @Test
    void leaseBoundaryResetsCounts() throws SQLException {
        for (int lease = 0; lease < 3; lease++) {
            try (Connection connection = FakeDriver.pooled()) {
                new OrderService(connection).summarise(4);
            }
        }
        Finding finding = only();
        assertEquals(4, finding.peakPerLease(), "each lease counts separately");
        assertEquals(3, finding.leases());
        assertEquals(12, finding.totalExecutions());
        assertEquals(3, Ledger.leases());
    }

    @Test
    void callSiteResolvesToApplicationCode() throws SQLException {
        try (Connection connection = FakeDriver.pooled()) {
            new OrderService(connection).summarise(7);
        }
        assertNotNull(only().site(), "a crossed threshold must produce a call site");
        assertEquals("shop.OrderService", only().site().declaringClass());
        assertEquals("summarise", only().site().methodName());
    }

    @Test
    void nonConformantWrapperThatThrowsIsSurvived() throws SQLException {
        try (Connection connection = new RogueConnection(RogueConnection.Mode.RETURNS_SELF)) {
            new OrderService(connection).summarise(5);
        }
        assertEquals(5, Db.count("SELECT * FROM order_item WHERE order_id = ?"));
        assertEquals(5, only().peakPerLease(), "a driver that unwraps to itself is not a wrapper");
    }

    @Test
    void reentrantUnwrapDoesNotRecurse() throws SQLException {
        try (Connection connection = new RogueConnection(RogueConnection.Mode.QUERIES_ON_UNWRAP)) {
            new OrderService(connection).summarise(5);
        }
        assertEquals(5, Db.count("SELECT * FROM order_item WHERE order_id = ?"),
                "the workload still ran to completion");
        assertTrue(Db.executed().size() < 20,
                "a driver that queries from unwrap must not drive the agent into recursion, saw "
                        + Db.executed().size() + " executions");
    }

    @Test
    void driverManagerAcquisitionIsCovered() throws SQLException {
        try (Connection connection = FakeDriver.connect()) {
            new OrderService(connection).summarise(6);
        }
        assertEquals(6, only().peakPerLease(), "connections from DriverManager are covered too");
    }

    private static Finding only() {
        List<Finding> findings = Ledger.findings();
        assertEquals(1, findings.size(), "expected exactly one template, got " + describe(findings));
        return findings.get(0);
    }

    private static String describe(List<Finding> findings) {
        StringBuilder text = new StringBuilder();
        for (Finding finding : findings) {
            text.append("[").append(finding.template().text()).append(" x").append(finding.peakPerLease()).append("]");
        }
        return text.toString();
    }
}
