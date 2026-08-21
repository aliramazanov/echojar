package com.aliramazanov.echojar.fake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Db {

    private static final List<String> EXECUTED = Collections.synchronizedList(new ArrayList<>());

    private static volatile boolean recording = true;

    private Db() {
    }

    public static void recording(boolean enabled) {
        recording = enabled;
    }

    static void executed(String sql) {
        if (recording) {
            EXECUTED.add(sql);
        }
    }

    public static void reset() {
        EXECUTED.clear();
    }

    public static List<String> executed() {
        synchronized (EXECUTED) {
            return new ArrayList<>(EXECUTED);
        }
    }

    public static int count(String sql) {
        int total = 0;
        for (String seen : executed()) {
            if (seen.equals(sql)) {
                total++;
            }
        }
        return total;
    }
}
