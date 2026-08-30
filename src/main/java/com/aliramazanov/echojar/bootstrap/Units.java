package com.aliramazanov.echojar.bootstrap;

import com.aliramazanov.echojar.bootstrap.findings.Lease;
import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;

public final class Units {

    private static final ThreadLocal<Frame> FRAMES = ThreadLocal.withInitial(Frame::new);

    private Units() {
    }

    public static void enter() {
        try {
            open();
        } catch (RuntimeException | LinkageError failure) {
            Diagnostics.suppressed(Diagnostics.Site.EXECUTE, failure);
        }
    }

    private static void open() {
        Frame frame = FRAMES.get();
        if (frame.depth++ == 0) {
            frame.unit = new Lease();
        }
    }

    public static void exit() {
        try {
            finish();
        } catch (RuntimeException | LinkageError failure) {
            Diagnostics.suppressed(Diagnostics.Site.CLOSE, failure);
        }
    }

    private static void finish() {
        Frame frame = FRAMES.get();

        if (frame.depth > 0) {
            frame.depth--;
        }

        if (frame.depth != 0) {
            return;
        }

        Lease finished = frame.unit;
        frame.unit = null;

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
