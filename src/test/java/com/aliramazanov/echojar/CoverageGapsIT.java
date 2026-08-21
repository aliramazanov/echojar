package com.aliramazanov.echojar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliramazanov.echojar.bootstrap.findings.Finding;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.fake.Db;
import com.aliramazanov.echojar.fake.FakeConnection;
import com.aliramazanov.echojar.fake.FakeDriver;
import com.aliramazanov.echojar.fake.ProxyPool;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoverageGapsIT {

    @BeforeEach
    void reset() {
        Ledger.reset();
        Db.reset();
    }

    @Test
    void aPoolBuiltFromDynamicProxiesIsStillCounted() throws SQLException {
        String sql = "SELECT * FROM proxied_item WHERE order_id = ?";
        try (Connection connection = ProxyPool.connection()) {
            PreparedStatement statement = connection.prepareStatement(sql);
            for (int query = 0; query < 9; query++) {
                statement.executeQuery();
            }
        }
        assertEquals(9, Db.count(sql), "the driver ran nine queries behind the proxy");
        Finding finding = only();
        assertEquals(9, finding.peakPerLease(), "a dynamic proxy must not hide or double the count");
        assertEquals(1, finding.leases());
    }

    @Test
    void storedProcedureCallsAreCounted() throws SQLException {
        String sql = "{ call recalculate_totals(?) }";
        try (Connection connection = new FakeConnection()) {
            CallableStatement call = connection.prepareCall(sql);
            for (int invocation = 0; invocation < 7; invocation++) {
                call.setInt(1, invocation);
                call.executeQuery();
            }
        }
        assertEquals(7, Db.count(sql), "the driver ran the procedure seven times");
        assertEquals(7, only().peakPerLease(), "prepareCall is a statement source too");
    }

    @Test
    void aStatementUsedAfterItsConnectionClosedStartsAFreshLease() throws SQLException {
        String sql = "SELECT * FROM orphan_item WHERE order_id = ?";
        Connection connection = FakeDriver.pooled();
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int query = 0; query < 6; query++) {
            statement.executeQuery();
        }
        connection.close();
        for (int query = 0; query < 3; query++) {
            statement.executeQuery();
        }
        assertEquals(1, Ledger.leases(), "only the closed lease has been reported");
        assertEquals(6, only().peakPerLease(),
                "work after close belongs to a lease that has not finished yet");
    }

    @Test
    void emptySqlDoesNotProduceABlankFinding() throws SQLException {
        try (Connection connection = new FakeConnection()) {
            PreparedStatement statement = connection.prepareStatement("   ");
            for (int query = 0; query < 8; query++) {
                statement.executeQuery();
            }
        }
        for (Finding finding : Ledger.findings()) {
            assertTrue(!finding.template().text().isBlank(),
                    "a blank statement must not be reported as an echo");
        }
    }

    @Test
    void aConnectionPerQueryLoopIsStillSurfaced() throws SQLException {
        String sql = "SELECT total FROM churn_ledger WHERE sku = ?";
        for (int query = 0; query < 30; query++) {
            try (Connection connection = FakeDriver.pooled()) {
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setInt(1, query);
                statement.executeQuery();
            }
        }
        Finding finding = only();
        assertEquals(1, finding.peakPerLease(), "one query per lease, so never an echo");
        assertEquals(30, finding.leases());
        assertEquals(30, finding.totalExecutions());
        assertNotNull(finding.site(),
                "a statement that never repeats in a lease still needs a call site");
        assertEquals("com.aliramazanov.echojar.CoverageGapsIT", finding.site().declaringClass());
    }

    private static Finding only() {
        List<Finding> findings = Ledger.findings();
        assertEquals(1, findings.size(), "expected one template, got " + findings.size());
        return findings.get(0);
    }
}
