package com.aliramazanov.echojar.bootstrap;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

final class WeakIdentityMap<V> {

    private final ConcurrentMap<Key, V> entries = new ConcurrentHashMap<>();
    private final ReferenceQueue<Object> stale = new ReferenceQueue<>();

    V get(Object key) {
        expunge();
        return entries.get(new Key(key, null));
    }

    V computeIfAbsent(Object key, Supplier<V> factory) {
        expunge();

        V existing = entries.get(new Key(key, null));
        if (existing != null) {
            return existing;
        }

        V created = factory.get();
        V raced = entries.putIfAbsent(new Key(key, stale), created);

        return raced != null ? raced : created;
    }

    void remove(Object key) {
        expunge();
        entries.remove(new Key(key, null));
    }

    int size() {
        expunge();
        return entries.size();
    }

    private void expunge() {
        Reference<?> dead;
        while ((dead = stale.poll()) != null) {
            entries.remove((Key) dead);
        }
    }

    private static final class Key extends WeakReference<Object> {

        private final int hash;

        Key(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key that)) {
                return false;
            }
            Object mine = get();
            return mine != null && mine == that.get();
        }
    }
}
