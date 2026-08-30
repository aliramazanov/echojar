package com.aliramazanov.echojar.bootstrap.findings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Ledger {

    private static final Map<SqlTemplate, Finding> FINDINGS = new LinkedHashMap<>();
    private static long leases;

    private Ledger() {
    }

    public static void record(Lease lease) {
        record(lease.echoes());
    }

    public static void record(List<Echoes> closing) {
        synchronized (FINDINGS) {
            leases++;

            for (Echoes echoes : closing) {
                FINDINGS.computeIfAbsent(echoes.template(), Finding::new).merge(echoes);
            }
        }
    }

    public static List<Finding> findings() {
        return findings(List.of());
    }

    public static List<Finding> findings(List<Lease> pending) {
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
