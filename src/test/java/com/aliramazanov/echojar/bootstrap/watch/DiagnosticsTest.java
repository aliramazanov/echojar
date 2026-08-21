package com.aliramazanov.echojar.bootstrap.watch;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiagnosticsTest {

    @BeforeEach
    void reset() {
        Diagnostics.reset();
        Journal.configure(Journal.Level.OFF, System.err, 200);
    }

    @Test
    void aQuietAgentReportsItselfHealthy() {
        assertTrue(Diagnostics.snapshot().healthy());
        assertEquals(0, Diagnostics.snapshot().suppressedTotal());
    }

    @Test
    void countersAccumulate() {
        Diagnostics.execution(3);
        Diagnostics.execution(4);
        Diagnostics.leaseOpened();
        Diagnostics.leaseClosed();
        Diagnostics.templated();
        Diagnostics.stackWalk();
        Diagnostics.transformed();
        Diagnostics.Snapshot health = Diagnostics.snapshot();
        assertEquals(7, health.executions());
        assertEquals(1, health.leasesClosed());
        assertEquals(0, health.leasesOpen());
        assertEquals(1, health.statementsTemplated());
        assertEquals(1, health.stackWalks());
        assertEquals(1, health.typesTransformed());
    }

    @Test
    void openLeasesAreOpenedMinusClosed() {
        Diagnostics.leaseOpened();
        Diagnostics.leaseOpened();
        Diagnostics.leaseOpened();
        Diagnostics.leaseClosed();
        assertEquals(2, Diagnostics.snapshot().leasesOpen());
    }

    @Test
    void aSuppressedFailureIsCountedAndAttributed() {
        Diagnostics.suppressed(Diagnostics.Site.EXECUTE, new IllegalStateException("driver said no"));
        Diagnostics.Snapshot health = Diagnostics.snapshot();
        assertFalse(health.healthy(), "an agent that swallowed something is not healthy");
        assertEquals(1, health.suppressedTotal());
        assertEquals(1L, health.suppressedBySite().get(Diagnostics.Site.EXECUTE));
        assertTrue(health.firstFailureBySite().get(Diagnostics.Site.EXECUTE).contains("driver said no"));
    }

    @Test
    void sitesAreCountedSeparately() {
        Diagnostics.suppressed(Diagnostics.Site.PREPARE, new RuntimeException("a"));
        Diagnostics.suppressed(Diagnostics.Site.CLOSE, new RuntimeException("b"));
        Diagnostics.suppressed(Diagnostics.Site.CLOSE, new RuntimeException("c"));
        Diagnostics.Snapshot health = Diagnostics.snapshot();
        assertEquals(3, health.suppressedTotal());
        assertEquals(1L, health.suppressedBySite().get(Diagnostics.Site.PREPARE));
        assertEquals(2L, health.suppressedBySite().get(Diagnostics.Site.CLOSE));
        assertTrue(health.firstFailureBySite().get(Diagnostics.Site.CLOSE).contains("b"),
                "the first failure is kept, not the latest");
    }

    @Test
    void anIncompleteTypeHierarchyIsNotAFailure() {
        Diagnostics.unresolvable();
        Diagnostics.unresolvable();
        Diagnostics.Snapshot health = Diagnostics.snapshot();
        assertEquals(2, health.typesUnresolvable());

        assertTrue(health.healthy(),
                "an optional dependency the application never shipped is not the agent failing");

        assertEquals(0, health.suppressedTotal());
    }

    @Test
    void aFloodOfFailuresIsCountedButNotDescribedEveryTime() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Journal.configure(Journal.Level.WARN, new PrintStream(buffer, true, StandardCharsets.UTF_8), 200);
        for (int failure = 0; failure < 500; failure++) {
            Diagnostics.suppressed(Diagnostics.Site.BATCH, new RuntimeException("boom " + failure));
        }

        assertEquals(500, Diagnostics.snapshot().suppressedTotal(), "every one is counted");
        long lines = buffer.toString(StandardCharsets.UTF_8).lines().count();
        assertTrue(lines <= 5, "a broken driver must not flood the application log, wrote " + lines);
    }
}
