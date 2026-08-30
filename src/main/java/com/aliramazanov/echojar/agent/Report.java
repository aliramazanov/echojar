package com.aliramazanov.echojar.agent;

import java.io.PrintStream;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aliramazanov.echojar.bootstrap.findings.CallSite;
import com.aliramazanov.echojar.bootstrap.findings.Finding;
import com.aliramazanov.echojar.bootstrap.findings.Lease;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.bootstrap.findings.OpenLeases;
import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;

final class Report {

    private static final int MOST_ECHOES_PRINTED = 25;

    private static final long STALE_AFTER_MILLIS = 60_000;

    private final int threshold;
    private final Detector detector;
    private final boolean always;
    private final long staleAfter;

    Report(int threshold, Detector detector) {
        this(threshold, detector, false);
    }

    Report(int threshold, Detector detector, boolean alwaysShowDiagnostics) {
        this(threshold, detector, alwaysShowDiagnostics, STALE_AFTER_MILLIS);
    }

    Report(int threshold, Detector detector, boolean alwaysShowDiagnostics, long staleAfterMillis) {
        this.threshold = threshold;
        this.detector = detector;
        this.always = alwaysShowDiagnostics;
        this.staleAfter = staleAfterMillis;
    }

    private static String describe(long millis) {
        long seconds = millis / 1000;

        if (seconds < 120) {
            return seconds + " seconds";
        }

        long minutes = seconds / 60;

        return minutes < 120 ? minutes + " minutes" : (minutes / 60) + " hours";
    }

    private static Unit unit(View view) {
        long requests = Diagnostics.snapshot().units();

        if (requests == 0) {
            return new Unit("connection lease", "connection leases", false);
        }

        if (dominant(requests, view.units())) {
            return new Unit("request", "requests", true);
        }

        return new Unit("unit of work", "units of work", false);
    }

    private static boolean dominant(long requests, long total) {
        return requests * 2 >= total;
    }

    private static int sites(List<Finding> echoes) {
        Set<CallSite> distinct = new HashSet<>();

        for (Finding finding : echoes) {
            if (finding.site() != null) {
                distinct.add(finding.site());
            }
        }

        return distinct.size();
    }

    private static String plural(long count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }

    void print(PrintStream out) {
        print(out, 0);
    }

    void print(PrintStream out, int override) {
        int effective = override > 0 ? override : threshold;
        View view = view();
        Unit named = unit(view);

        boolean requests = named.requests();
        String unit = named.one();
        String units = named.many();

        out.printf("%n=== echojar %s ===%n", Instant.now());

        List<Finding> echoes = view.findings().stream()
                .filter(finding -> finding.peakPerLease() >= effective)
                .sorted(Comparator.comparingInt(Finding::peakPerLease).reversed())
                .toList();

        if (echoes.isEmpty()) {
            out.printf("%nechojar: no echoes in %d %s%n%n", view.units(), units);
            spread(out, view, effective, requests, unit, units);
            caveats(out, view);
            return;
        }

        int sites = sites(echoes);

        if (sites > 0) {
            out.printf(
                    "%nechojar: %s in %s%n%n",
                    plural(echoes.size(), "echo", "echoes"),
                    plural(sites, "call site", "call sites")
            );
        } else {
            out.printf("%nechojar: %s%n%n", plural(echoes.size(), "echo", "echoes"));
        }

        int printed = 0;

        for (Finding finding : echoes) {
            if (printed++ == MOST_ECHOES_PRINTED) {
                out.printf("  and %d more, loudest first%n%n", echoes.size() - MOST_ECHOES_PRINTED);
                break;
            }

            out.printf("  %s%n", finding.template().text());
            out.printf(
                    "    %s in one %s%n",
                    plural(finding.peakPerLease(), "execution", "executions"),
                    unit
            );

            CallSite site = finding.site();

            out.printf("    %s%n", site == null ? "call site not resolved" : site);

            if (finding.leases() > 1) {
                out.printf(
                        "    seen in %d %s, %d executions total%n",
                        finding.leases(), units, finding.totalExecutions()
                );
            }

            age(out, finding);

            out.println();
        }

        spread(out, view, effective, requests, unit, units);
        caveats(out, view);
    }

    private View view() {
        List<Lease> pending = OpenLeases.snapshot();
        detector.resolve(pending);
        return new View(Ledger.findings(pending), Ledger.leases(pending.size()));
    }

    private void spread(
            PrintStream out,
            View view,
            int effective,
            boolean requests,
            String unit,
            String units
    ) {
        List<Finding> churn = view.findings().stream()
                .filter(finding -> finding.peakPerLease() < effective)
                .filter(finding -> finding.leases() >= effective)
                .filter(finding -> finding.totalExecutions() >= (long) finding.leases())
                .sorted(Comparator.comparingLong(Finding::totalExecutions).reversed())
                .limit(MOST_ECHOES_PRINTED)
                .toList();
        if (churn.isEmpty()) {
            return;
        }

        out.printf("  one or two queries per %s, across many of them:%n%n", unit);

        for (Finding finding : churn) {
            out.printf("  %s%n", finding.template().text());

            out.printf(
                    "    %s across %d %s%n",
                    plural(finding.totalExecutions(), "execution", "executions"),
                    finding.leases(),
                    units
            );

            out.printf(
                    "    %s%n",
                    finding.site() == null ? "call site not resolved" : finding.site()
            );

            out.println();
        }

        if (requests) {
            out.printf("  these run on nearly every request. that may be correct, or it may be%n");
            out.printf("  a lookup worth caching. a loop would have shown up as an echo above" +
                    ".%n%n");
        } else {
            out.printf("  a loop that reconnects for every query looks like this, and so does a%n");
            out.printf("  query that simply runs once per request. echojar cannot tell them " +
                    "apart%n");
            out.printf("  without a request boundary, which it did not find in this JVM.%n%n");
        }
    }

    private void age(PrintStream out, Finding finding) {
        long idle = System.currentTimeMillis() - finding.lastSeen();

        if (idle < staleAfter) {
            return;
        }

        out.printf("    last seen %s ago%n", describe(idle));
    }

    private void caveats(PrintStream out, View view) {
        long overflowed = detector.overflowed();

        if (overflowed > 0) {
            out.printf(
                    "  %d statements were not tracked, the template cache is full, raise " +
                            "templates=%n",
                    overflowed
            );
        }

        long ambiguous = detector.ambiguous();

        if (ambiguous > 0) {
            out.printf("  %d statements ran from more than one call site%n", ambiguous);
        }

        unbounded(out, view);
        health(out, view);
    }

    private void unbounded(PrintStream out, View view) {
        long requests = Diagnostics.snapshot().units();
        long total = view.units();

        if (requests == 0 || dominant(requests, total)) {
            return;
        }

        out.printf(
                "  only %d of %d units of work were inside a request. the rest ran on%n",
                requests, total
        );

        out.printf("  another thread, where echojar falls back to the connection lease and a%n");
        out.printf("  loop that reconnects for every query cannot be told from normal traffic.%n");
    }

    private void health(PrintStream out, View view) {
        Diagnostics.Snapshot health = Diagnostics.snapshot();

        if (health.healthy() && !always) {
            return;
        }

        out.printf("%n  agent health%n");
        out.printf(
                "    %d executions across %d units of work%n",
                health.executions(),
                view.units()
        );

        if (health.units() > 0) {
            out.printf(
                    "    %d of those were requests, bounded by a servlet or filter%n",
                    health.units()
            );
        }

        long untracked = OpenLeases.untracked();

        if (untracked > 0) {
            out.printf(
                    "    %d open leases were not tracked, too many were open at once%n",
                    untracked
            );
        }

        if (health.leasesOpen() > 0) {
            out.printf("    %d connection leases still open%n", health.leasesOpen());
        }

        if (health.units() == 0 && health.leasesClosed() > 20
                && health.executions() < health.leasesClosed() * 2L) {
            out.printf("    barely one query per lease and no request boundary was found, so a%n");
            out.printf(
                    "    repeated query that borrows a fresh connection would not show as an " +
                            "echo%n");
        }

        out.printf(
                "    %d statements templated, %d types transformed, %d stack walks%n",
                health.statementsTemplated(), health.typesTransformed(), health.stackWalks()
        );

        if (health.typesUnresolvable() > 0) {
            out.printf(
                    "    %d types had an incomplete hierarchy, which is normal for optional " +
                            "dependencies%n",
                    health.typesUnresolvable()
            );
        }

        if (health.suppressedTotal() == 0) {
            out.printf("    no failures were suppressed%n");
            return;
        }

        out.printf("    %d failures suppressed, by site:%n", health.suppressedTotal());

        health.suppressedBySite().forEach((site, count) -> out.printf(
                "      %-10s %6d   first: %s%n", site, count,
                health.firstFailureBySite().get(site)
        ));
    }

    private record View(List<Finding> findings, long units) {
    }

    private record Unit(String one, String many, boolean requests) {
    }
}
