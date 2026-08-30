package com.aliramazanov.echojar.bootstrap;

import com.aliramazanov.echojar.bootstrap.findings.Lease;
import com.aliramazanov.echojar.bootstrap.findings.OpenLeases;
import com.aliramazanov.echojar.bootstrap.findings.SqlTemplate;
import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Echo {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile EchoSink sink = EchoSink.NOOP;

    private Echo() {
    }

    public static boolean claimInstallation() {
        return INSTALLED.compareAndSet(false, true);
    }

    public static void install(EchoSink installed) {
        sink = installed;
    }

    public static boolean templated(Object statement, String sql) {
        try {
            SqlTemplate template = sink.template(sql);
            if (template == null) {
                return false;
            }

            SqlCarrier carrier = sqlState(statement, true);
            if (carrier == null) {
                return false;
            }

            carrier.echojarTemplate(template);
            Diagnostics.templated();

            return true;
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.PREPARE, failure);
            return false;
        }
    }

    public static void owned(Object statement, Object connection) {
        try {
            SqlCarrier carrier = sqlState(statement, false);

            if (carrier != null) {
                carrier.echojarOwner(leaseState(connection, true));
            }
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.PREPARE, failure);
        }
    }

    public static boolean executed(Object statement, int count) {
        if (count <= 0) {
            return true;
        }

        try {
            SqlCarrier carrier = sqlState(statement, false);
            if (carrier == null) {
                return true;
            }

            SqlTemplate template = carrier.echojarTemplate();
            if (template == null) {
                return true;
            }

            LeaseCarrier owner = carrier.echojarOwner();
            if (owner == null) {
                return false;
            }

            record(owner, template, count);

            return true;
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.EXECUTE, failure);
            return true;
        }
    }

    public static void executedOn(Object statement, Object connection, int count) {
        if (count <= 0) {
            return;
        }
        try {
            SqlCarrier carrier = sqlState(statement, false);
            if (carrier == null) {
                return;
            }

            SqlTemplate template = carrier.echojarTemplate();
            if (template == null) {
                return;
            }

            LeaseCarrier owner = leaseState(connection, true);
            if (owner == null) {
                return;
            }

            carrier.echojarOwner(owner);

            record(owner, template, count);
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.EXECUTE, failure);
        }
    }

    public static void executedSql(Object physicalConnection, String sql, int count) {
        if (count <= 0 || sql == null) {
            return;
        }
        try {
            LeaseCarrier owner = leaseState(physicalConnection, true);
            if (owner == null) {
                return;
            }

            SqlTemplate template = sink.template(sql);
            if (template != null) {
                record(owner, template, count);
            }
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.EXECUTE, failure);
        }
    }

    public static void batched(Object statement) {
        try {
            SqlCarrier carrier = sqlState(statement, false);
            if (carrier != null && carrier.echojarTemplate() != null) {
                carrier.echojarBatch(carrier.echojarBatch() + 1);
            }
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.BATCH, failure);
        }
    }

    public static int batchExecuted(Object statement) {
        try {
            SqlCarrier carrier = sqlState(statement, false);
            if (carrier == null) {
                return 0;
            }

            int pending = carrier.echojarBatch();
            carrier.echojarBatch(0);

            return executed(statement, pending) ? 0 : pending;
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.BATCH, failure);
            return 0;
        }
    }

    public static void batchedSql(Object statement, String sql) {
        try {
            if (sql == null) {
                return;
            }

            StatementCarrier carrier = batchState(statement, true);
            if (carrier == null) {
                return;
            }

            List<String> batch = carrier.echojarBatchSql();
            if (batch == null) {
                batch = new ArrayList<>();
                carrier.echojarBatchSql(batch);
            }

            batch.add(sql);
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.BATCH, failure);
        }
    }

    public static void batchExecutedSql(Object statement, Object physicalConnection) {
        try {
            StatementCarrier carrier = batchState(statement, false);
            if (carrier == null) {
                return;
            }

            List<String> batch = carrier.echojarBatchSql();
            if (batch == null || batch.isEmpty()) {
                return;
            }

            List<String> pending = new ArrayList<>(batch);
            batch.clear();

            for (String sql : pending) {
                executedSql(physicalConnection, sql, 1);
            }
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.BATCH, failure);
        }
    }

    public static void batchCleared(Object statement) {
        try {
            if (statement instanceof SqlCarrier prepared) {
                prepared.echojarBatch(0);
                return;
            }
            if (statement instanceof StatementCarrier plain) {
                clearBatchSql(plain);
                return;
            }

            SqlCarrier detached = Fallback.sql(statement, false);
            if (detached != null) {
                detached.echojarBatch(0);
                clearBatchSql((StatementCarrier) detached);
            }
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.BATCH, failure);
        }
    }

    private static void clearBatchSql(@NotNull StatementCarrier carrier) {
        List<String> batch = carrier.echojarBatchSql();
        if (batch != null) {
            batch.clear();
        }
    }

    public static void closed(Object physicalConnection) {
        try {
            LeaseCarrier carrier = leaseState(physicalConnection, false);
            if (carrier == null) {
                return;
            }

            Lease lease = carrier.echojarTakeLease();

            if (lease == null) {
                return;
            }

            Diagnostics.leaseClosed();
            sink.leaseClosed(lease);
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.CLOSE, failure);
        }
    }

    private static SqlCarrier sqlState(Object statement, boolean create) {
        if (statement instanceof SqlCarrier carrier) {
            return carrier;
        }

        return statement == null ? null : Fallback.sql(statement, create);
    }

    private static StatementCarrier batchState(Object statement, boolean create) {
        if (statement instanceof StatementCarrier carrier) {
            return carrier;
        }

        return statement == null ? null : Fallback.batch(statement, create);
    }

    private static LeaseCarrier leaseState(Object connection, boolean create) {
        if (connection instanceof LeaseCarrier carrier) {
            return carrier;
        }

        return connection == null ? null : Fallback.connection(connection, create);
    }

    static void unitClosed(Lease unit) {
        try {
            sink.leaseClosed(unit);
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.CLOSE, failure);
        }
    }

    private static void record(LeaseCarrier carrier, SqlTemplate template, int count) {
        Lease unit = Units.current();
        if (unit != null) {
            Diagnostics.execution(count);
            sink.executed(unit, unit.record(template, count));
            return;
        }

        if (carrier == null) {
            return;
        }

        Lease lease = carrier.echojarOpenLease();

        Diagnostics.execution(count);
        sink.executed(lease, lease.record(template, count));
    }
}
