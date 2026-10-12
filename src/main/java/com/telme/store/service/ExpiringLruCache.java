package com.telme.store.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

final class ExpiringLruCache<V> {

    private final Clock clock;
    private final Duration ttl;
    private final Map<String, Entry<V>> entries;

    ExpiringLruCache(Clock clock, Duration ttl, int maxSize) {
        this.clock = clock;
        this.ttl = ttl;
        this.entries = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry<V>> eldest) {
                return size() > maxSize;
            }
        });
    }

    V getOrLoad(String key, Supplier<V> loader) {
        Instant now = clock.instant();
        Entry<V> cached = entries.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.value();
        }
        V value = loader.get();
        entries.put(key, new Entry<>(value, now.plus(ttl)));
        return value;
    }

    private record Entry<V>(V value, Instant expiresAt) {
    }
}
