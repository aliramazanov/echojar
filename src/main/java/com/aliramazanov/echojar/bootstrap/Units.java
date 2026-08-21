package com.aliramazanov.echojar.bootstrap;

import com.aliramazanov.echojar.bootstrap.findings.Lease;
import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;

public final class Units {

    // thread scoped deliberately: a unit that followed a query onto a pooled executor thread
    // would outlive the request that opened it, which is how thread-local detectors end up
    // reporting the previous request's queries as this one's. Work handed to another thread
    // falls back to its connection lease instead.
    private static final ThreadLocal<Frame> FRAMES = ThreadLocal.withInitial(Frame::new);

    private Units() {
    }

    public static void enter() {
        Frame frame = FRAMES.get();
        if (frame.depth++ == 0) {
            frame.unit = new Lease();
        }
    }

    public static void exit() {
        Frame frame = FRAMES.get();
        if (frame.depth > 0) {
            frame.depth--;
        }
        if (frame.depth != 0) {
            return;
        }
        Lease finished = frame.unit;
        frame.unit = null;
        // requests that never queried are dropped rather than counted, or static assets and
        // health checks would swamp the denominator that every ratio in the report divides by
        if (finished != null && !finished.empty()) {
            Diagnostics.unitClosed();
            Echo.unitClosed(finished);
        }
    }

    public static Lease current() {
        return FRAMES.get().unit;
    }

    private static final class Frame {

        private int depth;
        private Lease unit;
    }
}
