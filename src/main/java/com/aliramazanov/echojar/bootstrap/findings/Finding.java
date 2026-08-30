package com.aliramazanov.echojar.bootstrap.findings;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

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

    @Contract(pure = true)
    private Finding(@NotNull Finding other) {
        this.template = other.template;
        this.peakPerLease = other.peakPerLease;
        this.totalExecutions = other.totalExecutions;
        this.leases = other.leases;
        this.site = other.site;
        this.lastSeen = other.lastSeen;
    }

    @Contract(value = " -> new", pure = true)
    @NotNull Finding copy() {
        return new Finding(this);
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

    void merge(@NotNull Echoes echoes) {
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
