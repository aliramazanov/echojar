package com.aliramazanov.echojar.bootstrap;

import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;

public final class Chain {

    private static final ThreadLocal<Frame> FRAMES = ThreadLocal.withInitial(Frame::new);

    private Chain() {
    }

    public static void prepareEnter() {
        try {
            FRAMES.get().prepares++;
        } catch (RuntimeException | LinkageError failure) {
            Diagnostics.suppressed(Diagnostics.Site.PREPARE, failure);
        }
    }

    public static void prepareExit(Object connection, String sql, Object statement) {
        try {
            prepare(connection, sql, statement);
        } catch (RuntimeException | LinkageError failure) {
            Diagnostics.suppressed(Diagnostics.Site.PREPARE, failure);
        }
    }

    private static void prepare(Object connection, String sql, Object statement) {
        Frame frame = FRAMES.get();

        if (frame.prepares > 0) {
            frame.prepares--;
        }

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

    public static Object executeEnter(Object statement) {
        try {
            Frame frame = FRAMES.get();
            frame.push(statement);
            return frame;
        } catch (RuntimeException | LinkageError failure) {
            Diagnostics.suppressed(Diagnostics.Site.EXECUTE, failure);
            return null;
        }
    }

    public static boolean executeClaim(Object handle, Object statement) {
        try {
            if (!(handle instanceof Frame frame)) {
                return false;
            }

            return frame.pop();
        } catch (RuntimeException | LinkageError failure) {
            Diagnostics.suppressed(Diagnostics.Site.EXECUTE, failure);
            return false;
        }
    }

    public static void statementEnter() {
        try {
            FRAMES.get().statements++;
        } catch (RuntimeException | LinkageError failure) {
            Diagnostics.suppressed(Diagnostics.Site.EXECUTE, failure);
        }
    }

    public static boolean statementExit() {
        try {
            Frame frame = FRAMES.get();

            if (frame.statements > 0) {
                frame.statements--;
            }

            return frame.statements == 0;
        } catch (RuntimeException | LinkageError failure) {
            Diagnostics.suppressed(Diagnostics.Site.EXECUTE, failure);
            return false;
        }
    }

    public static boolean reenter() {
        try {
            Frame frame = FRAMES.get();

            if (frame.inside) {
                return false;
            }

            frame.inside = true;

            return true;
        } catch (RuntimeException | LinkageError failure) {
            Diagnostics.suppressed(Diagnostics.Site.EXECUTE, failure);
            return false;
        }
    }

    public static void release() {
        try {
            FRAMES.get().inside = false;
        } catch (RuntimeException | LinkageError failure) {
            Diagnostics.suppressed(Diagnostics.Site.EXECUTE, failure);
        }
    }

    private static final class Frame {

        private static final int DEEPEST = 64;

        private Object[] executing = new Object[8];
        private int executingDepth;
        private int prepares;
        private int statements;
        private boolean inside;

        private void push(Object statement) {
            if (executingDepth == executing.length && executing.length < DEEPEST) {
                Object[] grown = new Object[executing.length * 2];
                System.arraycopy(executing, 0, grown, 0, executing.length);
                executing = grown;
            }

            if (executingDepth < executing.length) {
                executing[executingDepth] = statement;
            }

            executingDepth++;
        }

        private boolean pop() {
            if (executingDepth == 0) {
                return false;
            }

            executingDepth--;

            if (executingDepth >= executing.length) {
                return false;
            }

            Object mine = executing[executingDepth];
            executing[executingDepth] = null;

            for (int below = 0; below < executingDepth; below++) {
                if (executing[below] == mine) {
                    return false;
                }
            }

            return true;
        }
    }
}
