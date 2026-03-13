package com.playground.cache.core;

public record CacheStats(
        long hitCount,
        long missCount,
        long evictionCount,
        long expiredCount,
        double hitRate
) {
}
