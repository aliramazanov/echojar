package com.aliramazanov.echojar.bootstrap.findings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenLeasesTest {

    @BeforeEach
    void reset() {
        OpenLeases.reset();
    }

    @Test
    void anOpenLeaseIsVisibleAndAClosedOneIsNot() {
        Lease lease = new Lease();
        OpenLeases.opened(lease);
        assertTrue(OpenLeases.snapshot().contains(lease));
        OpenLeases.closed(lease);
        assertTrue(OpenLeases.snapshot().isEmpty(), "a closed lease must not be reported again");
    }

    @Test
    void closingSomethingNeverOpenedIsHarmless() {
        OpenLeases.closed(new Lease());
        assertTrue(OpenLeases.snapshot().isEmpty());
    }

    @Test
    void churningLeasesFromManyThreadsLeavesNothingBehind() throws Exception {
        int threads = 16;
        int perThread = 2_000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicBoolean broken = new AtomicBoolean();

        for (int worker = 0; worker < threads; worker++) {
            Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                    for (int round = 0; round < perThread; round++) {
                        Lease lease = new Lease();
                        OpenLeases.opened(lease);
                        OpenLeases.closed(lease);
                    }
                } catch (Throwable failure) {
                    broken.set(true);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "workers did not finish");
        assertFalse(broken.get(), "a worker blew up");
        assertEquals(List.of(), OpenLeases.snapshot(),
                "every lease was closed, so the registry must be empty rather than leaking");
        assertEquals(0, OpenLeases.untracked(), "nothing should have hit the cap");
    }

    @Test
    void snapshotIsStableWhileLeasesChurn() throws Exception {
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger snapshots = new AtomicInteger();
        AtomicBoolean broken = new AtomicBoolean();

        Thread churn = Thread.ofPlatform().start(() -> {
            while (!stop.get()) {
                Lease lease = new Lease();
                OpenLeases.opened(lease);
                OpenLeases.closed(lease);
            }
        });
        Thread reader = Thread.ofPlatform().start(() -> {
            try {
                while (!stop.get()) {
                    for (Lease lease : OpenLeases.snapshot()) {
                        lease.empty();
                    }
                    snapshots.incrementAndGet();
                }
            } catch (Throwable failure) {
                broken.set(true);
            }
        });

        Thread.sleep(1_000);
        stop.set(true);
        churn.join(30_000);
        reader.join(30_000);
        assertFalse(broken.get(), "reporting while leases churn must never throw");
        assertTrue(snapshots.get() > 0, "the reader never ran");
    }

    @Test
    void theCapStopsTrackingRatherThanGrowingForever() {
        for (int lease = 0; lease < 25_000; lease++) {
            OpenLeases.opened(new Lease());
        }
        assertTrue(OpenLeases.snapshot().size() <= 20_000,
                "an application that never closes connections must not grow the registry without limit");
        assertTrue(OpenLeases.untracked() > 0, "and the report must be able to say so");
    }
}
