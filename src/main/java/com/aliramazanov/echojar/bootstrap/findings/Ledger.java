package com.aliramazanov.echojar.bootstrap.findings;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Ledger {

    private static final Map<SqlTemplate, Finding> FINDINGS = new LinkedHashMap<>();
    private static long leases;

    private Ledger() {
    }

    public static void record(@NotNull Lease lease) {
        record(lease.echoes());
    }

    public static void record(@NotNull List<Echoes> closing) {
        synchronized (FINDINGS) {
            leases++;

            for (Echoes echoes : closing) {
                FINDINGS.computeIfAbsent(echoes.template(), Finding::new).merge(echoes);
            }
        }
    }

    @Contract(" -> new")
    public static @NotNull List<Finding> findings() {
        return findings(List.of());
    }

    @Contract("_ -> new")
    public static @NotNull List<Finding> findings(@NotNull List<Lease> pending) {
        synchronized (FINDINGS) {
            Map<SqlTemplate, Finding> view = new LinkedHashMap<>();
            FINDINGS.forEach((template, finding) -> view.put(template, finding.copy()));

            for (Lease lease : pending) {
                for (Echoes echoes : lease.echoes()) {
                    view.computeIfAbsent(echoes.template(), Finding::new).merge(echoes);
                }
            }

            return new ArrayList<>(view.values());
        }
    }

    public static long leases() {
        synchronized (FINDINGS) {
            return leases;
        }
    }

    public static long leases(int pending) {
        synchronized (FINDINGS) {
            return leases + pending;
        }
    }

    public static void reset() {
        synchronized (FINDINGS) {
            FINDINGS.clear();
            leases = 0;
        }
        OpenLeases.reset();
    }
}
