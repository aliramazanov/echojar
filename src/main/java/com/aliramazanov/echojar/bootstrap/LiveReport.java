package com.aliramazanov.echojar.bootstrap;

import java.io.PrintStream;

public final class LiveReport {

    private static volatile Printer printer;

    private LiveReport() {
    }

    public static void install(Printer installed) {
        printer = installed;
    }

    public static boolean print(PrintStream out, int threshold) {
        Printer current = printer;
        if (current == null) {
            return false;
        }

        current.print(out, threshold);

        return true;
    }

    public interface Printer {

        void print(PrintStream out, int threshold);
    }
}
