package com.aliramazanov.echojar.bootstrap;

import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;

public final class Chain {

    private static final ThreadLocal<Frame> FRAMES = ThreadLocal.withInitial(Frame::new);

    private Chain() {
    }

    public static void prepareEnter() {
        FRAMES.get().prepares++;
    }

    public static void prepareExit(Object connection, String sql, Object statement) {
        Frame frame = FRAMES.get();
        if (frame.prepares > 0) {
            frame.prepares--;
        }
        // only the outermost layer is templated, so a statement wrapped by a pool is counted
        // once. Identity cannot decide this: DBCP2 answers unwrap(PreparedStatement.class)
        // with the wrapper itself, making a delegating statement indistinguishable from the
        // driver's own. Nesting depth is the only reliable signal, and the outermost layer is
        // the handle the application holds, so it is the one whose lease owns the executions.
        if (frame.prepares != 0 || statement == null || sql == null) {
            return;
        }
        try {
            if (Echo.templated(statement, sql)) {
                Echo.owned(statement, connection);
            }
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.PREPARE, failure);
        }
    }

    public static Object executeEnter() {
        Frame frame = FRAMES.get();
        frame.executes++;
        return frame;
    }

    public static boolean executeClaim(Object handle) {
        if (!(handle instanceof Frame frame)) {
            return false;
        }
        if (frame.executes > 0) {
            frame.executes--;
        }
        return frame.executes == 0;
    }

    public static void statementEnter() {
        FRAMES.get().statements++;
    }

    public static boolean statementExit() {
        Frame frame = FRAMES.get();
        if (frame.statements > 0) {
            frame.statements--;
        }
        return frame.statements == 0;
    }

    public static boolean reenter() {
        Frame frame = FRAMES.get();
        if (frame.inside) {
            return false;
        }
        frame.inside = true;
        return true;
    }

    public static void release() {
        FRAMES.get().inside = false;
    }

    private static final class Frame {

        private int prepares;
        private int statements;
        private int executes;
        private boolean inside;
    }
}
