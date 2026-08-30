package com.aliramazanov.echojar.bootstrap.watch;

import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;

public final class Journal {

    private static final long DEFAULT_BUDGET = 200;
    private static final AtomicLong WRITTEN = new AtomicLong();
    private static volatile Level level = Level.WARN;
    private static volatile PrintStream out = System.err;
    private static volatile long budget = DEFAULT_BUDGET;

    private Journal() {
    }

    public static void configure(Level requested, PrintStream sink, long lineBudget) {
        level = requested == null ? Level.WARN : requested;
        out = sink == null ? System.err : sink;
        budget = lineBudget < 0 ? DEFAULT_BUDGET : lineBudget;
    }

    public static Level level() {
        return level;
    }

    public static boolean writes(Level requested) {
        Level current = level();

        return current != Level.OFF && requested.ordinal() <= current.ordinal();
    }

    public static void warn(String message) {
        write(Level.WARN, message);
    }

    public static void info(String message) {
        write(Level.INFO, message);
    }

    public static void debug(String message) {
        write(Level.DEBUG, message);
    }

    private static void write(@NotNull Level requested, String message) {
        if (!writes(requested)) {
            return;
        }

        long written = WRITTEN.incrementAndGet();

        if (written > budget) {
            if (written == budget + 1) {
                out.printf(
                        "echojar [%s] further diagnostics suppressed after %d lines%n",
                        "WARN",
                        budget
                );
            }

            return;
        }

        out.printf("echojar [%s] %s%n", requested, message);
    }

    public enum Level {
        OFF,
        WARN,
        INFO,
        DEBUG
    }
}
