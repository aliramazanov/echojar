package com.aliramazanov.echojar.bootstrap.watch;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public final class Diagnostics {

    private static final int DESCRIBED_PER_SITE = 3;
    private static final LongAdder EXECUTIONS = new LongAdder();
    private static final LongAdder LEASES_OPENED = new LongAdder();
    private static final LongAdder LEASES_CLOSED = new LongAdder();
    private static final LongAdder UNITS = new LongAdder();
    private static final LongAdder STATEMENTS_TEMPLATED = new LongAdder();
    private static final LongAdder STACK_WALKS = new LongAdder();
    private static final LongAdder TYPES_TRANSFORMED = new LongAdder();
    private static final LongAdder TYPES_UNRESOLVABLE = new LongAdder();
    private static final Map<Site, LongAdder> SUPPRESSED = new EnumMap<>(Site.class);
    private static final Map<Site, String> FIRST_FAILURE = new EnumMap<>(Site.class);

    static {
        for (Site site : Site.values()) {
            SUPPRESSED.put(site, new LongAdder());
        }
    }

    private Diagnostics() {
    }

    public static void execution(int count) {
        EXECUTIONS.add(count);
    }

    public static void leaseOpened() {
        LEASES_OPENED.increment();
    }

    public static void leaseClosed() {
        LEASES_CLOSED.increment();
    }

    public static void unitClosed() {
        UNITS.increment();
    }

    public static void templated() {
        STATEMENTS_TEMPLATED.increment();
    }

    public static void stackWalk() {
        STACK_WALKS.increment();
    }

    public static void transformed() {
        TYPES_TRANSFORMED.increment();
    }

    public static void unresolvable() {
        TYPES_UNRESOLVABLE.increment();
    }

    public static void suppressed(Site site, Throwable failure) {
        LongAdder counter = SUPPRESSED.get(site);
        counter.increment();
        if (counter.sum() <= DESCRIBED_PER_SITE) {
            describe(site, failure);
        }
    }

    private static synchronized void describe(Site site, Throwable failure) {
        FIRST_FAILURE.putIfAbsent(site, describeBriefly(failure));
        Journal.warn(site + " suppressed: " + describeBriefly(failure));
    }

    private static String describeBriefly(Throwable failure) {
        StackTraceElement[] frames = failure.getStackTrace();
        String origin = frames.length == 0 ? "no frame" : frames[0].toString();
        return failure.getClass().getName() + ": " + failure.getMessage() + " at " + origin;
    }

    static synchronized void reset() {
        resetWindow();
        TYPES_TRANSFORMED.reset();
        TYPES_UNRESOLVABLE.reset();
    }

    public static synchronized void resetWindow() {
        EXECUTIONS.reset();
        LEASES_OPENED.reset();
        LEASES_CLOSED.reset();
        UNITS.reset();
        STATEMENTS_TEMPLATED.reset();
        STACK_WALKS.reset();
        SUPPRESSED.values().forEach(LongAdder::reset);
        FIRST_FAILURE.clear();
    }

    public static Snapshot snapshot() {
        Map<Site, Long> suppressed = new EnumMap<>(Site.class);
        Map<Site, String> examples = new EnumMap<>(Site.class);

        long total = 0;

        synchronized (Diagnostics.class) {
            for (Site site : Site.values()) {
                long count = SUPPRESSED.get(site).sum();
                total += count;

                if (count > 0) {
                    suppressed.put(site, count);
                    examples.put(site, FIRST_FAILURE.get(site));
                }
            }
        }

        return new Snapshot(
                EXECUTIONS.sum(),
                LEASES_OPENED.sum(),
                LEASES_CLOSED.sum(),
                UNITS.sum(),
                STATEMENTS_TEMPLATED.sum(),
                STACK_WALKS.sum(),
                TYPES_TRANSFORMED.sum(),
                TYPES_UNRESOLVABLE.sum(),
                total,
                suppressed,
                examples
        );
    }

    public enum Site {
        PREPARE,
        EXECUTE,
        CLOSE,
        BATCH,
        CALL_SITE,
        TRANSFORM,
        REPORT
    }

    public record Snapshot(
            long executions, long leasesOpened,
            long leasesClosed, long units,
            long statementsTemplated, long stackWalks, long typesTransformed,
            long typesUnresolvable, long suppressedTotal,
            Map<Site, Long> suppressedBySite, Map<Site, String> firstFailureBySite
    ) {

        public long leasesOpen() {
            return Math.max(0, leasesOpened - leasesClosed);
        }

        public boolean healthy() {
            return suppressedTotal == 0;
        }
    }
}
