package com.playground.cache.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalCacheTest {

    @Test
    void shouldEvictLeastRecentlyUsedEntry() {
        LocalCache<Integer, String> cache = new LocalCache<>(2, new LruEvictionPolicy<>());
        cache.put(1, "A", Duration.ofMinutes(1));
        cache.put(2, "B", Duration.ofMinutes(1));

        cache.get(1);
        cache.put(3, "C", Duration.ofMinutes(1));

        assertTrue(cache.get(1).isPresent());
        assertFalse(cache.get(2).isPresent());
        assertTrue(cache.get(3).isPresent());
        assertEquals(1, cache.snapshotStats().evictionCount());
    }

    @Test
    void shouldEvictLeastFrequentlyUsedEntry() {
        LocalCache<Integer, String> cache = new LocalCache<>(2, new LfuEvictionPolicy<>());
        cache.put(1, "A", Duration.ofMinutes(1));
        cache.put(2, "B", Duration.ofMinutes(1));

        cache.get(1);
        cache.get(1);
        cache.put(3, "C", Duration.ofMinutes(1));

        assertTrue(cache.get(1).isPresent());
        assertFalse(cache.get(2).isPresent());
        assertTrue(cache.get(3).isPresent());
    }

    @Test
    void shouldEvictFirstInEntryUnderFifo() {
        LocalCache<Integer, String> cache = new LocalCache<>(2, new FifoEvictionPolicy<>());
        cache.put(1, "A", Duration.ofMinutes(1));
        cache.put(2, "B", Duration.ofMinutes(1));

        cache.get(1);
        cache.put(3, "C", Duration.ofMinutes(1));

        assertFalse(cache.get(1).isPresent());
        assertTrue(cache.get(2).isPresent());
        assertTrue(cache.get(3).isPresent());
    }

    @Test
    void shouldExpireEntryAfterTtl() throws InterruptedException {
        LocalCache<Integer, String> cache = new LocalCache<>(2, new LruEvictionPolicy<>());
        cache.put(1, "A", Duration.ofMillis(50));

        Thread.sleep(80);

        assertFalse(cache.get(1).isPresent());
        assertEquals(1, cache.snapshotStats().expiredCount());
    }

    @Test
    void shouldCleanExpiredEntriesInBackgroundStyleSweep() throws InterruptedException {
        LocalCache<Integer, String> cache = new LocalCache<>(3, new LruEvictionPolicy<>());
        cache.put(1, "A", Duration.ofMillis(40));
        cache.put(2, "B", Duration.ofMinutes(1));

        Thread.sleep(70);

        assertEquals(1, cache.cleanUpExpiredEntries());
        assertEquals(1, cache.size());
        assertFalse(cache.get(1).isPresent());
        assertTrue(cache.get(2).isPresent());
    }

    @Test
    void shouldSortHotKeysByAccessCount() {
        LocalCache<Integer, String> cache = new LocalCache<>(3, new LruEvictionPolicy<>());
        cache.put(1, "A", Duration.ofMinutes(1));
        cache.put(2, "B", Duration.ofMinutes(1));
        cache.put(3, "C", Duration.ofMinutes(1));

        cache.get(2);
        cache.get(2);
        cache.get(1);

        List<Integer> hotKeys = cache.snapshotEntries().stream()
                .sorted(Comparator.comparing(CacheEntryView<Integer, String>::accessCount).reversed())
                .map(CacheEntryView::key)
                .toList();

        assertEquals(List.of(2, 1, 3), hotKeys);
    }
}
