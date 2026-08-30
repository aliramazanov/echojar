package com.aliramazanov.echojar.bootstrap;

import com.aliramazanov.echojar.bootstrap.findings.Lease;
import com.aliramazanov.echojar.bootstrap.findings.SqlTemplate;

import java.util.List;

public final class Fallback {

    private static final WeakIdentityMap<SqlCarrier> STATEMENTS = new WeakIdentityMap<>();
    private static final WeakIdentityMap<LeaseCarrier> CONNECTIONS = new WeakIdentityMap<>();

    private Fallback() {
    }

    static SqlCarrier sql(Object statement, boolean create) {
        return holder(statement, create);
    }

    static StatementCarrier batch(Object statement, boolean create) {
        return (StatementCarrier) holder(statement, create);
    }

    private static SqlCarrier holder(Object statement, boolean create) {
        return create ? STATEMENTS.computeIfAbsent(statement, DetachedSql::new) :
                STATEMENTS.get(statement);
    }

    static LeaseCarrier connection(Object connection, boolean create) {
        return create ? CONNECTIONS.computeIfAbsent(connection, DetachedLease::new) :
                CONNECTIONS.get(connection);
    }

    private static final class DetachedSql implements SqlCarrier, StatementCarrier {

        private SqlTemplate template;
        private LeaseCarrier owner;
        private int batch;
        private List<String> batchSql;

        @Override
        public SqlTemplate echojarTemplate() {
            return template;
        }

        @Override
        public void echojarTemplate(SqlTemplate template) {
            this.template = template;
        }

        @Override
        public LeaseCarrier echojarOwner() {
            return owner;
        }

        @Override
        public void echojarOwner(LeaseCarrier owner) {
            this.owner = owner;
        }

        @Override
        public int echojarBatch() {
            return batch;
        }

        @Override
        public void echojarBatch(int pending) {
            this.batch = pending;
        }

        @Override
        public List<String> echojarBatchSql() {
            return batchSql;
        }

        @Override
        public void echojarBatchSql(List<String> batch) {
            this.batchSql = batch;
        }
    }

    private static final class DetachedLease implements LeaseCarrier {

        private Lease lease;

        @Override
        public Lease echojarLease() {
            return lease;
        }

        @Override
        public void echojarLease(Lease lease) {
            this.lease = lease;
        }
    }
}
