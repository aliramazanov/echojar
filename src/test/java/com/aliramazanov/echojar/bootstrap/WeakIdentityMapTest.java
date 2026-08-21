package com.aliramazanov.echojar.bootstrap;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class WeakIdentityMapTest {

    @Test
    void keysAreComparedByIdentityNotEquality() {
        WeakIdentityMap<String> map = new WeakIdentityMap<>();
        List<Integer> first = new ArrayList<>(List.of(1, 2, 3));
        List<Integer> second = new ArrayList<>(List.of(1, 2, 3));
        assertEquals(first, second, "these are equal but distinct objects");

        map.computeIfAbsent(first, () -> "first");
        assertSame("first", map.get(first));
        assertNull(map.get(second), "an equal but different object must not share the entry");
    }

    @Test
    void repeatedLookupsReturnTheSameValue() {
        WeakIdentityMap<Object> map = new WeakIdentityMap<>();
        Object key = new Object();
        Object created = map.computeIfAbsent(key, Object::new);
        assertSame(created, map.computeIfAbsent(key, Object::new));
    }

    @Test
    void removeDropsTheEntry() {
        WeakIdentityMap<String> map = new WeakIdentityMap<>();
        Object key = new Object();
        map.computeIfAbsent(key, () -> "value");
        map.remove(key);
        assertNull(map.get(key));
    }

    @Test
    void entriesDoNotOutliveTheirKeys() {
        WeakIdentityMap<byte[]> map = new WeakIdentityMap<>();
        for (int entry = 0; entry < 20_000; entry++) {
            map.computeIfAbsent(new Object(), () -> new byte[128]);
        }
        assertTrue(map.size() > 0, "entries were recorded");

        for (int attempt = 0; attempt < 50 && map.size() > 100; attempt++) {
            System.gc();
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(map.size() <= 100,
                "unreachable keys must be expunged, still holding " + map.size());
    }

    @Test
    void aLiveKeyIsNeverExpunged() {
        WeakIdentityMap<String> map = new WeakIdentityMap<>();
        Object kept = new Object();
        map.computeIfAbsent(kept, () -> "kept");

        for (int entry = 0; entry < 20_000; entry++) {
            map.computeIfAbsent(new Object(), () -> "garbage");
        }

        for (int attempt = 0; attempt < 20; attempt++) {
            System.gc();
        }

        assertSame("kept", map.get(kept), "a reachable key must keep its entry");
        assertNotSame(null, kept);
    }
}
