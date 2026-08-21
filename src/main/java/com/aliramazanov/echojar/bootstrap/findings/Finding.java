package com.aliramazanov.echojar.bootstrap.findings;

public final class Finding {

    private final SqlTemplate template;
    private int peakPerLease;
    private long totalExecutions;
    private int leases;
    private CallSite site;
    private long lastSeen;

    Finding(SqlTemplate template) {
        this.template = template;
    }

    public SqlTemplate template() {
        return template;
    }

    public int peakPerLease() {
        return peakPerLease;
    }

    public long totalExecutions() {
        return totalExecutions;
    }

    public int leases() {
        return leases;
    }

    public CallSite site() {
        return site;
    }

    public long lastSeen() {
        return lastSeen;
    }

    void merge(Echoes echoes) {
        lastSeen = System.currentTimeMillis();
        int executions = echoes.executions();
        if (executions > peakPerLease) {
            peakPerLease = executions;
        }
        totalExecutions += executions;
        leases++;
        if (site == null) {
            site = echoes.site();
        }
    }
}
