package com.aliramazanov.echojar.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.aliramazanov.echojar.bootstrap.findings.Lease;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UnitsTest {

    @Test
    void nestedBoundariesShareTheOutermostUnit() {
        Units.enter();
        Lease outer = Units.current();
        Units.enter();
        assertSame(outer, Units.current(), "a filter inside a filter is still one request");
        Units.exit();
        assertSame(outer, Units.current(), "the inner boundary must not close the request");
        Units.exit();
        assertNull(Units.current());
    }

    @Test
    void anUnbalancedExitCannotDriveTheDepthNegative() {
        Units.exit();
        Units.exit();
        Units.enter();
        assertNotNull(Units.current(), "a stray exit must not leave the next request unable to open");
        Units.exit();
        assertNull(Units.current());
    }

    @Test
    void eachThreadCarriesItsOwnUnit() throws InterruptedException {
        Units.enter();
        Lease mine = Units.current();
        AtomicReference<Lease> theirs = new AtomicReference<>();
        Thread other = new Thread(() -> {
            Units.enter();
            theirs.set(Units.current());
            Units.exit();
        });
        other.start();
        other.join();
        Units.exit();
        assertNotNull(theirs.get());
        assertNotSame(mine, theirs.get(), "one request must never accumulate another thread's queries");
    }

    @Test
    void aClosedUnitIsNotReusedByTheNextRequest() {
        Units.enter();
        Lease first = Units.current();
        Units.exit();
        Units.enter();
        assertNotSame(first, Units.current(), "state left on a pooled thread is how this goes wrong");
        Units.exit();
    }

    @Test
    void reportsOnlyUnitsThatDidWork() {
        long before = counted();
        Units.enter();
        Units.exit();
        assertEquals(before, counted(), "a request that never queried is not a unit of work");
    }

    private static long counted() {
        return com.aliramazanov.echojar.bootstrap.watch.Diagnostics.snapshot().units();
    }
}
