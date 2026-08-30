package com.aliramazanov.echojar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aliramazanov.echojar.bootstrap.findings.Finding;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.bootstrap.findings.OpenLeases;
import com.aliramazanov.echojar.fake.Db;
import com.aliramazanov.echojar.fake.FakeDriver;

class ConcurrencyAdversarialIT {

    private static final String SQL = "SELECT * FROM racy_item WHERE order_id = ?";

    private static long total() {
        long total = 0;

        for (Finding finding : Ledger.findings(OpenLeases.snapshot())) {
            total += finding.totalExecutions();
        }

        return total;
    }

    private static Finding only() {
        List<Finding> findings = Ledger.findings();
        assertEquals(1, findings.size(), "expected one template, got " + findings.size());
        return findings.getFirst();
    }

    @BeforeEach
    void reset() {
        Ledger.reset();
        Db.reset();
    }

    @Test
    void twoThreadsClosingTheSameConnectionRecordOneLease() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Throwable> surprise =
                new java.util.concurrent.atomic.AtomicReference<>();
        int rounds = 400;

        for (int round = 0; round < rounds; round++) {
            Connection connection = FakeDriver.pooled();
            PreparedStatement statement = connection.prepareStatement(SQL);

            for (int query = 0; query < 4; query++) {
                statement.executeQuery();
            }

            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);

            for (int closer = 0; closer < 2; closer++) {
                new Thread(() -> {
                    try {
                        start.await();
                        connection.close();
                    } catch (java.sql.SQLException expected) {
                    } catch (Throwable unexpected) {
                        surprise.compareAndSet(null, unexpected);
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "closers did not finish");
        }

        assertEquals(
                rounds, Ledger.leases(),
                "a connection closed by two threads at once is still one lease"
        );

        Finding finding = only();

        assertEquals(rounds, finding.leases());

        assertEquals(4L * rounds, finding.totalExecutions(), "no lease was counted twice");
    }

    @Test
    void executingWhileAnotherThreadClosesNeverInventsExecutions() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Throwable> surprise =
                new java.util.concurrent.atomic.AtomicReference<>();

        int rounds = 300;
        int queries = 20;

        for (int round = 0; round < rounds; round++) {
            Connection connection = FakeDriver.pooled();
            PreparedStatement statement = connection.prepareStatement(SQL);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);

            new Thread(() -> {
                try {
                    start.await();
                    for (int query = 0; query < queries; query++) {
                        statement.executeQuery();
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();

            new Thread(() -> {
                try {
                    start.await();
                    connection.close();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();

            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "workers did not finish");
            connection.close();
        }

        assertNull(
                surprise.get(),
                () -> "a worker hit something other than a SQLException: " + surprise.get()
        );

        long observed = total();

        assertEquals(
                Db.count(SQL), observed,
                "echojar must account for exactly what the driver ran, even while a close races"
        );
    }
}
