package com.aliramazanov.echojar.bootstrap;

import com.aliramazanov.echojar.bootstrap.findings.Echoes;
import com.aliramazanov.echojar.bootstrap.findings.Lease;
import com.aliramazanov.echojar.bootstrap.findings.SqlTemplate;

public interface EchoSink {

    EchoSink NOOP = new EchoSink() {

        @Override
        public SqlTemplate template(String rawSql) {
            return null;
        }

        @Override
        public void executed(Lease lease, Echoes echoes) {
        }

        @Override
        public void leaseClosed(Lease lease) {
        }
    };

    SqlTemplate template(String rawSql);

    void executed(Lease lease, Echoes echoes);

    void leaseClosed(Lease lease);
}
