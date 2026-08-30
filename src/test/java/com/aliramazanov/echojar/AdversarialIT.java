package com.aliramazanov.echojar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aliramazanov.echojar.bootstrap.findings.Finding;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.fake.Db;
import com.aliramazanov.echojar.fake.ExplodingConnection;
import com.aliramazanov.echojar.fake.FakeConnection;
import com.aliramazanov.echojar.fake.FakeDriver;
import com.aliramazanov.echojar.fake.NonConformantPoolConnection;

import static org.junit.jupiter.api.Assertions.*;

class AdversarialIT {

    private static final String SQL = "SELECT * FROM adversarial_item WHERE order_id = ?";

    private static String report() {
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();

        assertTrue(
                com.aliramazanov.echojar.bootstrap.LiveReport.print(
                        new java.io.PrintStream(
                                sink,
                                true,
                                java.nio.charset.StandardCharsets.UTF_8
                        ), 2
                ), "no agent report is installed in this JVM"
        );

        return sink.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Finding only() {
        List<Finding> findings = Ledger.findings();
        assertEquals(1, findings.size(), "expected exactly one template, got " + findings.size());
        return findings.getFirst();
    }

    @BeforeEach
    void reset() {
        Ledger.reset();
        Db.reset();
    }

    @Test
    void batchThatJumpsPastTheThresholdStillResolvesACallSite() throws SQLException {
        try (Connection connection = FakeDriver.pooled()) {
            PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO adversarial_audit (note) VALUES (?)");

            for (int row = 0; row < 40; row++) {
                insert.setString(1, "row");
                insert.addBatch();
            }

            insert.executeBatch();
        }

        Finding finding = only();
        assertEquals(40, finding.peakPerLease());
        assertNotNull(
                finding.site(),
                "a batch crosses the threshold in one jump and must still get a call site"
        );
    }

    @Test
    void nonConformantWrapperIsNotCountedTwice() throws SQLException {
        try (Connection connection = new NonConformantPoolConnection(new FakeConnection())) {
            PreparedStatement statement = connection.prepareStatement(SQL);

            for (int query = 0; query < 8; query++) {
                statement.setInt(1, query);
                statement.executeQuery();
            }
        }

        assertEquals(8, Db.count(SQL), "the driver really ran eight queries");
        Finding finding = only();

        assertEquals(
                8,
                finding.peakPerLease(),
                "a pool that cannot answer isWrapperFor must not double the count"
        );

        assertEquals(1, finding.leases(), "one physical connection is one lease, not two");
        assertEquals(8, finding.totalExecutions(), "eight queries ran, so eight were counted");
    }

    @Test
    void clearBatchDiscardsPendingWork() throws SQLException {
        try (Connection connection = FakeDriver.pooled()) {
            PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO adversarial_audit (note) VALUES (?)");

            for (int row = 0; row < 10; row++) {
                insert.addBatch();
            }

            insert.clearBatch();
            insert.executeBatch();
        }

        assertEquals(0, Db.executed().size(), "the driver ran nothing");
        assertEquals(List.of(), Ledger.findings(), "a cleared batch must not be counted");
    }

    @Test
    void executeBatchWithNothingPendingCountsNothing() throws SQLException {
        try (Connection connection = FakeDriver.pooled()) {
            connection.prepareStatement(SQL).executeBatch();
        }

        assertEquals(List.of(), Ledger.findings());
    }

    @Test
    void failedExecutionsAreNotCounted() throws SQLException {
        try (Connection connection = new ExplodingConnection()) {
            PreparedStatement statement = connection.prepareStatement(SQL);

            for (int query = 0; query < 10; query++) {
                assertThrows(SQLException.class, statement::executeQuery);
            }
        }

        assertEquals(List.of(), Ledger.findings(), "a query that threw never reached the database");
    }

    @Test
    void closingAConnectionTwiceRecordsOneLease() throws SQLException {
        Connection connection = FakeDriver.pooled();
        PreparedStatement statement = connection.prepareStatement(SQL);

        for (int query = 0; query < 6; query++) {
            statement.executeQuery();
        }

        connection.close();
        connection.close();
        assertEquals(1, Ledger.leases(), "a double close must not record the lease twice");
        assertEquals(6, only().peakPerLease());
    }

    @Test
    void aConnectionSharedByTwoThreadsDoesNotLoseCounts() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Throwable> blewUp =
                new java.util.concurrent.atomic.AtomicReference<>();
        int perThread = 500;

        try (Connection connection = FakeDriver.pooled()) {
            PreparedStatement statement = connection.prepareStatement(SQL);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);

            for (int worker = 0; worker < 2; worker++) {
                new Thread(() -> {
                    try {
                        start.await();
                        for (int query = 0; query < perThread; query++) {
                            statement.executeQuery();
                        }
                    } catch (Throwable failure) {
                        blewUp.compareAndSet(null, failure);
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "workers did not finish");
        }

        assertNull(blewUp.get(), () -> "a worker blew up: " + blewUp.get());
        assertEquals(2 * perThread, Db.executed().size(), "the driver saw every query");
        assertEquals(2 * perThread, only().peakPerLease(), "no counts were lost to a race");
    }

    @Test
    void aStatementReusedAcrossTwoLeasesCountsSeparately() throws SQLException {
        FakeConnection physical = new FakeConnection();
        PreparedStatement statement = physical.prepareStatement(SQL);

        for (int query = 0; query < 4; query++) {
            statement.executeQuery();
        }

        physical.close();

        for (int query = 0; query < 7; query++) {
            statement.executeQuery();
        }

        physical.close();
        Finding finding = only();
        assertEquals(2, finding.leases(), "each close ends a lease");
        assertEquals(7, finding.peakPerLease(), "the peak is the busiest lease, not the sum");
        assertEquals(11, finding.totalExecutions());
    }

    @Test
    void aConnectionThatIsNeverClosedIsStillReported() throws SQLException {
        Connection connection = FakeDriver.pooled();
        PreparedStatement statement = connection.prepareStatement(SQL);

        for (int query = 0; query < 20; query++) {
            statement.executeQuery();
        }

        assertEquals(List.of(), Ledger.findings(), "the ledger holds finished leases only");
        String report = report();

        assertTrue(
                report.contains("20 executions in one connection lease"),
                "a pool that never closes the driver's own connection would otherwise be invisible:\n" + report
        );
    }

    @Test
    void anOpenLeaseIsNotCountedTwiceWhenItFinallyCloses() throws SQLException {
        Connection connection = FakeDriver.pooled();
        PreparedStatement statement = connection.prepareStatement(SQL);

        for (int query = 0; query < 20; query++) {
            statement.executeQuery();
        }

        assertTrue(report().contains("20 executions in one connection lease"));
        connection.close();

        Finding finding = only();
        assertEquals(20, finding.peakPerLease(), "reporting an open lease must not double it");
        assertEquals(20, finding.totalExecutions());
        assertEquals(
                1,
                finding.leases(),
                "one connection is one lease however often it was reported"
        );

        assertEquals(1, Ledger.leases());
    }

    @Test
    void anOpenLeaseKeepsGrowingBetweenReports() throws SQLException {
        Connection connection = FakeDriver.pooled();
        PreparedStatement statement = connection.prepareStatement(SQL);

        for (int query = 0; query < 6; query++) {
            statement.executeQuery();
        }

        assertTrue(report().contains("6 executions in one connection lease"));

        for (int query = 0; query < 5; query++) {
            statement.executeQuery();
        }

        String later = report();

        assertTrue(
                later.contains("11 executions in one connection lease"),
                "the second report shows the lease as it stands now:\n" + later
        );
    }

    @Test
    void aStatementRunFromInsideAnotherExecutionIsStillCounted() throws SQLException {
        try (Connection connection = new com.aliramazanov.echojar.fake.TriggeringConnection()) {
            try (PreparedStatement outer = connection.prepareStatement(SQL)) {
                for (int row = 0; row < 9; row++) {
                    outer.executeUpdate();
                }
            }
        }

        assertEquals(9, Db.count(SQL), "the driver ran the outer statement nine times");

        assertEquals(
                9,
                Db.count(com.aliramazanov.echojar.fake.TriggeringConnection.TRIGGERED),
                "and the triggered statement nine times"
        );

        Finding triggered = Ledger.findings().stream()
                .filter(finding -> finding.template().text().contains("fake_audit")).findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the triggered statement vanished, which is how a nested execute gets " +
                                "lost: " + Ledger.findings()));

        assertEquals(
                9,
                triggered.totalExecutions(),
                "every nested execution counts, not just the outermost frame"
        );
    }

    @Test
    void plainStatementBatchIsExpandedPerStatement() throws SQLException {
        try (Connection connection = FakeDriver.pooled()) {
            try (Statement statement = connection.createStatement()) {
                for (int row = 0; row < 12; row++) {
                    statement.addBatch(
                            "INSERT INTO adversarial_audit (note) VALUES ('row " + row + "')");
                }

                statement.executeBatch();
            }
        }
        assertEquals(12, Db.executed().size(), "the driver ran twelve statements");
        assertEquals(12, only().peakPerLease(), "a statement batch is twelve executions, not one");
    }

    @Test
    void manyConcurrentLeasesAggregateCorrectly() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Throwable> blewUp =
                new java.util.concurrent.atomic.AtomicReference<>();

        int workers = 8;
        int leasesPerWorker = 250;
        int queriesPerLease = 6;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);

        for (int worker = 0; worker < workers; worker++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int lease = 0; lease < leasesPerWorker; lease++) {
                        try (Connection connection = FakeDriver.pooled()) {
                            PreparedStatement statement = connection.prepareStatement(SQL);
                            for (int query = 0; query < queriesPerLease; query++) {
                                statement.executeQuery();
                            }
                        }
                    }
                } catch (Throwable failure) {
                    blewUp.compareAndSet(null, failure);
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(120, TimeUnit.SECONDS), "workers did not finish");
        assertNull(blewUp.get(), () -> "a worker blew up: " + blewUp.get());

        int expectedLeases = workers * leasesPerWorker;
        assertEquals(expectedLeases, Ledger.leases(), "every lease was recorded exactly once");
        Finding finding = only();
        assertEquals(expectedLeases, finding.leases());

        assertEquals(
                (long) expectedLeases * queriesPerLease,
                finding.totalExecutions(),
                "no execution was lost or double counted under contention"
        );

        assertEquals(
                queriesPerLease,
                finding.peakPerLease(),
                "leases must not bleed into each other across threads"
        );
    }

    @Test
    void anAbandonedBatchDoesNotLeakIntoTheNextLease() throws SQLException {
        String insert = "INSERT INTO adversarial_audit (note) VALUES (?)";
        com.aliramazanov.echojar.fake.CachingPoolConnection pool =
                new com.aliramazanov.echojar.fake.CachingPoolConnection(
                new FakeConnection());

        PreparedStatement first = pool.prepareStatement(insert);

        for (int row = 0; row < 3; row++) {
            first.addBatch();
        }

        pool.close();

        PreparedStatement second = pool.prepareStatement(insert);

        assertSame(first, second, "the pool handed back the cached statement");

        for (int row = 0; row < 2; row++) {
            second.addBatch();
        }

        second.executeBatch();
        pool.close();

        int actuallyExecuted = Db.executed().size();
        Finding finding = only();

        assertEquals(
                actuallyExecuted,
                finding.totalExecutions(),
                "echojar counted " + finding.totalExecutions() + " but the driver executed " +
                        actuallyExecuted
        );
    }

    @Test
    void aPoolThatCachesStatementsStillCountsEveryLease() throws SQLException {
        String sql = getSql();

        assertEquals(24, Db.count(sql), "the driver ran twenty four queries");

        Finding finding = only();

        assertEquals(6, finding.peakPerLease(), "each borrow is its own lease");
        assertEquals(4, finding.leases(), "a cached statement must not swallow later borrows");
        assertEquals(24, finding.totalExecutions());
    }

    private static String getSql() throws SQLException {
        String sql = "SELECT * FROM cached_item WHERE order_id = ?";

        com.aliramazanov.echojar.fake.CachingPoolConnection pool =
                new com.aliramazanov.echojar.fake.CachingPoolConnection(
                new FakeConnection());

        for (int borrow = 0; borrow < 4; borrow++) {
            PreparedStatement statement = pool.prepareStatement(sql);

            for (int query = 0; query < 6; query++) {
                statement.executeQuery();
            }

            pool.close();
        }

        return sql;
    }
}
