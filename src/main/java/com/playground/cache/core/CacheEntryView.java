package com.playground.cache.core;

import java.time.Instant;

public record CacheEntryView<K, V>(
        K key,
        V value,
        Instant createdAt,
        Instant expireAt,
        long accessCount,
        Instant lastAccessAt
) {
    public static <K, V> CacheEntryView<K, V> from(CacheEntry<K, V> entry) {
        return new CacheEntryView<>(
                entry.getKey(),
                entry.getValue(),
                entry.getCreatedAt(),
                entry.getExpireAt(),
                entry.getAccessCount(),
                entry.getLastAccessAt()
        );
    }
}
