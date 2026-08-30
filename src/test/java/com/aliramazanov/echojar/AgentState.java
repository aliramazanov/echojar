package com.aliramazanov.echojar;

import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;
import com.aliramazanov.echojar.fake.Db;

final class AgentState {

    private AgentState() {
    }

    static void reset() {
        Ledger.reset();
        Diagnostics.resetWindow();
        Db.reset();
    }
}
