package com.aliramazanov.echojar.bootstrap.watch;

import java.util.ArrayList;
import java.util.List;

final class DiagnosticsBean implements DiagnosticsMXBean {

    @Override
    public long getExecutions() {
        return Diagnostics.snapshot().executions();
    }

    @Override
    public long getLeasesClosed() {
        return Diagnostics.snapshot().leasesClosed();
    }

    @Override
    public long getLeasesOpen() {
        return Diagnostics.snapshot().leasesOpen();
    }

    @Override
    public long getStatementsTemplated() {
        return Diagnostics.snapshot().statementsTemplated();
    }

    @Override
    public long getTypesTransformed() {
        return Diagnostics.snapshot().typesTransformed();
    }

    @Override
    public long getStackWalks() {
        return Diagnostics.snapshot().stackWalks();
    }

    @Override
    public long getSuppressedFailures() {
        return Diagnostics.snapshot().suppressedTotal();
    }

    @Override
    public boolean isHealthy() {
        return Diagnostics.snapshot().healthy();
    }

    @Override
    public String[] getSuppressedDetail() {
        Diagnostics.Snapshot health = Diagnostics.snapshot();
        List<String> detail = new ArrayList<>();
        health.suppressedBySite().forEach((site, count) ->
                detail.add(site + " x" + count + " first: " + health.firstFailureBySite().get(site)));
        return detail.toArray(String[]::new);
    }
}
