package com.aliramazanov.echojar.bootstrap.findings;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class OpenLeases {

    private static final int CAP = 20_000;

    private static final Set<Lease> OPEN = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger TRACKED = new AtomicInteger();
    private static final AtomicInteger UNTRACKED = new AtomicInteger();

    private OpenLeases() {
    }

    public static void opened(Lease lease) {
        if (TRACKED.get() >= CAP) {
            UNTRACKED.incrementAndGet();
            return;
        }

        if (OPEN.add(lease)) {
            TRACKED.incrementAndGet();
        }
    }

    public static void closed(Lease lease) {
        if (OPEN.remove(lease)) {
            TRACKED.decrementAndGet();
        }
    }

    @Contract(value = " -> new", pure = true)
    public static @NotNull List<Lease> snapshot() {
        return new ArrayList<>(OPEN);
    }

    public static long untracked() {
        return UNTRACKED.get();
    }

    public static void reset() {
        OPEN.clear();
        TRACKED.set(0);
        UNTRACKED.set(0);
    }
}
