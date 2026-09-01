package com.aliramazanov.echojar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aliramazanov.echojar.bootstrap.findings.Finding;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.fake.Db;
import com.aliramazanov.echojar.fake.FakeDriver;

class VirtualThreadIT {

    private static final String SQL = "SELECT * FROM loom_item WHERE order_id = ?";

    @BeforeEach
    void reset() {
        AgentState.reset();
    }

    @Test
    void everyVirtualThreadGetsItsOwnLeaseAndTheCountsAreExact() throws Exception {
        int threads = 500;
        int queries = 20;

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int task = 0; task < threads; task++) {
                pool.submit(() -> work(queries));
            }
        }

        assertEquals(threads, Ledger.leases(), "one connection is one lease, on any kind of thread");

        Finding finding = only();

        assertEquals(queries, finding.peakPerLease());
        assertEquals((long) threads * queries, finding.totalExecutions());
        assertEquals(threads * queries, Db.executed().size(),
                "the agent must report exactly what the driver ran");
    }

    @Test
    void aLeaseThatMovesBetweenVirtualThreadsIsStillOneLease() throws Exception {
        try (Connection connection = FakeDriver.pooled()) {
            PreparedStatement statement = connection.prepareStatement(SQL);

            Thread first = Thread.ofVirtual().start(() -> run(statement, 10));
            first.join();

            Thread second = Thread.ofVirtual().start(() -> run(statement, 10));
            second.join();
        }

        assertEquals(1, Ledger.leases(),
                "work handed from one virtual thread to another is still one connection");

        assertEquals(20, only().peakPerLease(),
                "a counter kept on the thread would have split this in two");
    }

    @Test
    void aConnectionClosedOnADifferentVirtualThreadStillReports() throws Exception {
        Connection connection = FakeDriver.pooled();
        PreparedStatement statement = connection.prepareStatement(SQL);

        run(statement, 12);

        Thread closer = Thread.ofVirtual().start(() -> {
            try {
                connection.close();
            } catch (SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });

        assertTrue(closer.join(java.time.Duration.ofSeconds(30)), "the closer did not finish");

        assertEquals(1, Ledger.leases());
        assertEquals(12, only().peakPerLease());
    }

    @Test
    void thousandsOfVirtualThreadsDoNotLoseOrInventExecutions() throws Exception {
        int threads = 2_000;
        int queries = 6;

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int task = 0; task < threads; task++) {
                pool.submit(() -> work(queries));
            }
        }

        long counted = 0;

        for (Finding finding : Ledger.findings()) {
            counted += finding.totalExecutions();
        }

        assertEquals(Db.executed().size(), counted,
                "under many virtual threads the agent and the driver must still agree");

        assertEquals(threads, Ledger.leases());
    }

    private static void work(int queries) {
        try (Connection connection = FakeDriver.pooled()) {
            run(connection.prepareStatement(SQL), queries);
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void run(PreparedStatement statement, int queries) {
        try {
            for (int query = 0; query < queries; query++) {
                statement.setInt(1, query);
                statement.executeQuery();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Finding only() {
        List<Finding> findings = Ledger.findings();
        assertEquals(1, findings.size(), "expected one template, got " + findings.size());
        return findings.getFirst();
    }
}
