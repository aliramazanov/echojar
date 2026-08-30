package com.aliramazanov.echojar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.aliramazanov.echojar.bootstrap.findings.Finding;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.bootstrap.findings.OpenLeases;
import com.aliramazanov.echojar.fake.CachingPoolConnection;
import com.aliramazanov.echojar.fake.Db;
import com.aliramazanov.echojar.fake.FakeConnection;
import com.aliramazanov.echojar.fake.NonConformantPoolConnection;
import com.aliramazanov.echojar.fake.PoolConnection;
import com.aliramazanov.echojar.fake.SubclassingConnection;
import com.aliramazanov.echojar.fake.TriggeringConnection;

class ConformanceFuzzIT {

    private static final int RUNS = 3_000;

    private static final int TABLES = 40;

    private static final List<Supplier<Connection>> SHAPES = List.of(
            FakeConnection::new,
            () -> new PoolConnection(new FakeConnection()),
            () -> new PoolConnection(new PoolConnection(new FakeConnection())),
            () -> new CachingPoolConnection(new FakeConnection()),
            () -> new NonConformantPoolConnection(new FakeConnection()),
            SubclassingConnection::new,
            () -> new PoolConnection(new SubclassingConnection()),
            TriggeringConnection::new,
            () -> new PoolConnection(new TriggeringConnection()));

    @Test
    void whateverTheDriverRanIsWhatEchojarCounted() throws SQLException {
        for (int run = 0; run < RUNS; run++) {
            long seed = run;
            Ledger.reset();
            OpenLeases.reset();
            Db.reset();
            Random random = new Random(seed);
            Supplier<Connection> shape = SHAPES.get(random.nextInt(SHAPES.size()));

            try (Connection connection = shape.get()) {
                exercise(connection, random, run);
            }

            Map<String, Long> counted = new HashMap<>();
            for (Finding finding : Ledger.findings(OpenLeases.snapshot())) {
                counted.merge(finding.template().text(), finding.totalExecutions(), Long::sum);
            }
            Map<String, Long> actual = new HashMap<>();
            for (String sql : Db.executed()) {
                actual.merge(sql, 1L, Long::sum);
            }

            assertEquals(actual, counted, () -> "seed " + seed
                    + ": echojar disagreed with the driver about what ran"
                    + "\n  driver  : " + new java.util.TreeMap<>(actual)
                    + "\n  echojar : " + new java.util.TreeMap<>(counted));
        }
    }

    private void exercise(Connection connection, Random random, int run) throws SQLException {
        int statements = 1 + random.nextInt(3);
        for (int index = 0; index < statements; index++) {
            int table = random.nextInt(TABLES);
            String sql = "SELECT c" + index + " FROM fuzz_" + table + " WHERE k = ?";
            String plainSql = "SELECT c" + index + " FROM fuzz_" + table;
            boolean usePrepared = random.nextBoolean();
            attempt(() -> {
                if (usePrepared) {
                    prepared(connection, sql, random);
                } else {
                    plain(connection, plainSql, random);
                }
            });
        }
    }

    private void prepared(Connection connection, String sql, Random random) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int rounds = 1 + random.nextInt(6);
            for (int round = 0; round < rounds; round++) {
                int pick = random.nextInt(4);
                attempt(() -> {
                    switch (pick) {
                        case 0 -> statement.executeQuery().close();
                        case 1 -> statement.executeUpdate();
                        case 2 -> statement.execute();
                        default -> statement.executeLargeUpdate();
                    }
                });
            }
            if (random.nextBoolean()) {
                int batched = 1 + random.nextInt(4);
                boolean discard = random.nextInt(4) == 0;
                attempt(() -> {
                    for (int row = 0; row < batched; row++) {
                        statement.addBatch();
                    }
                    if (discard) {
                        statement.clearBatch();
                    } else {
                        statement.executeBatch();
                    }
                });
            }
        }
    }

    private interface Work {
        void run() throws SQLException;
    }

    private void attempt(Work work) throws SQLException {
        try {
            work.run();
        } catch (UnsupportedOperationException | java.sql.SQLFeatureNotSupportedException refused) {
            return;
        }
    }

    private void plain(Connection connection, String sql, Random random) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int rounds = 1 + random.nextInt(5);
            for (int round = 0; round < rounds; round++) {
                int pick = random.nextInt(3);
                attempt(() -> {
                    switch (pick) {
                        case 0 -> statement.executeQuery(sql).close();
                        case 1 -> statement.executeUpdate(sql);
                        default -> statement.execute(sql);
                    }
                });
            }
            if (random.nextBoolean()) {
                int batched = 1 + random.nextInt(3);
                attempt(() -> {
                    for (int row = 0; row < batched; row++) {
                        statement.addBatch(sql);
                    }
                    statement.executeBatch();
                });
            }
        }
    }
}
