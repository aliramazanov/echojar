package com.aliramazanov.echojar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliramazanov.echojar.bootstrap.findings.Finding;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverCompatibilityIT {

    private static final String URL = "jdbc:h2:mem:compat;DB_CLOSE_DELAY=-1";
    private static final String LOOKUP = "SELECT sku FROM compat_item WHERE order_id = ?";

    @BeforeAll
    static void createSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS compat_item");
            statement.execute("CREATE TABLE compat_item (order_id INT, sku VARCHAR(32))");
            for (int order = 0; order < 40; order++) {
                statement.execute("INSERT INTO compat_item VALUES (" + order + ", 'SKU-" + order + "')");
            }
        }
    }

    @BeforeEach
    void reset() {
        Ledger.reset();
    }

    @Test
    void h2ThroughDriverManagerIsCounted() throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL)) {
            lookup(connection, 12);
        }
        Finding finding = only();
        assertEquals(12, finding.peakPerLease(), "a real H2 connection must count each execution once");
        assertEquals(1, finding.leases());
        assertNotNull(finding.site(), "the call site must resolve past the H2 driver frames");
        assertEquals("com.aliramazanov.echojar.DriverCompatibilityIT", finding.site().declaringClass());
    }

    @Test
    void h2BehindADbcp2PoolIsNotDoubleCounted() throws SQLException {
        BasicDataSource pool = pool();
        try (Connection connection = pool.getConnection()) {
            lookup(connection, 15);
        } finally {
            close(pool);
        }
        Finding finding = only();
        assertEquals(15, finding.peakPerLease(),
                "DBCP2 wraps every statement, and the wrapper must not be counted as well");
        assertEquals(1, finding.leases(), "one pooled lease, not one per delegating layer");
        assertEquals(15, finding.totalExecutions());
    }

    @Test
    void aPooledConnectionReturnedAndBorrowedAgainIsTwoLeases() throws SQLException {
        BasicDataSource pool = pool();
        try {
            for (int lease = 0; lease < 3; lease++) {
                try (Connection connection = pool.getConnection()) {
                    lookup(connection, 7);
                }
            }
        } finally {
            close(pool);
        }
        Finding finding = only();
        assertEquals(3, finding.leases(), "returning a connection to the pool ends its lease");
        assertEquals(7, finding.peakPerLease());
        assertEquals(21, finding.totalExecutions());
    }

    @Test
    void inlinedLiteralsAgainstARealDriverCollapseToOneTemplate() throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL)) {
            try (Statement statement = connection.createStatement()) {
                for (int order = 0; order < 9; order++) {
                    try (ResultSet rows = statement.executeQuery(
                            "SELECT sku FROM compat_item WHERE order_id = " + order)) {
                        while (rows.next()) {
                            rows.getString(1);
                        }
                    }
                }
            }
        }
        Finding finding = only();
        assertEquals(9, finding.peakPerLease(), "nine literal variants are one template");
        assertTrue(finding.template().text().endsWith("order_id = ?"), finding.template().text());
    }

    private static BasicDataSource pool() {
        BasicDataSource pool = new BasicDataSource();
        pool.setUrl(URL);
        pool.setInitialSize(1);
        pool.setMaxTotal(2);
        return pool;
    }

    private static void close(DataSource pool) throws SQLException {
        ((BasicDataSource) pool).close();
    }

    private static void lookup(Connection connection, int times) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOOKUP)) {
            for (int order = 0; order < times; order++) {
                statement.setInt(1, order);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        rows.getString(1);
                    }
                }
            }
        }
    }

    private static Finding only() {
        List<Finding> findings = Ledger.findings();
        assertEquals(1, findings.size(),
                "expected one template, got " + findings.stream().map(f -> f.template().text()).toList());
        return findings.get(0);
    }
}
