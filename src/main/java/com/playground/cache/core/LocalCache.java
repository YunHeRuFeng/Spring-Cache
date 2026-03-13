package com.playground.cache.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class LocalCache<K, V> implements Cache<K, V> {

    private final ConcurrentMap<K, CacheEntry<K, V>> storage = new ConcurrentHashMap<>();
    private final EvictionPolicy<K, V> evictionPolicy;
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();
    private final AtomicLong expiredCount = new AtomicLong();

    public LocalCache(int capacity, EvictionPolicy<K, V> evictionPolicy) {
        this.capacity = capacity;
        this.evictionPolicy = evictionPolicy;
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        Instant now = Instant.now();
        Instant expireAt = ttl == null ? null : now.plus(ttl);
        lock.lock();
        try {
            CacheEntry<K, V> existing = storage.get(key);
            if (existing != null) {
                existing.setValue(value);
                existing.setExpireAt(expireAt);
                evictionPolicy.onAccess(existing);
                return;
            }
            evictExpiredEntries(now);
            if (storage.size() >= capacity) {
                evictOne();
            }
            CacheEntry<K, V> entry = new CacheEntry<>(key, value, now, expireAt);
            storage.put(key, entry);
            evictionPolicy.onPut(entry);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<V> get(K key) {
        return getEntry(key).map(CacheEntryView::value);
    }

    public CacheLookupResult<K, V> lookup(K key) {
        Instant now = Instant.now();
        lock.lock();
        try {
            CacheEntry<K, V> entry = storage.get(key);
            if (entry == null) {
                missCount.incrementAndGet();
                return new CacheLookupResult<>(CacheLookupStatus.MISS, null);
            }
            if (entry.isExpired(now)) {
                removeExpired(entry);
                missCount.incrementAndGet();
                return new CacheLookupResult<>(CacheLookupStatus.EXPIRED, null);
            }
            entry.recordAccess(now);
            evictionPolicy.onAccess(entry);
            hitCount.incrementAndGet();
            return new CacheLookupResult<>(CacheLookupStatus.HIT, CacheEntryView.from(entry));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<CacheEntryView<K, V>> getEntry(K key) {
        CacheLookupResult<K, V> result = lookup(key);
        return Optional.ofNullable(result.entry());
    }

    @Override
    public boolean remove(K key) {
        lock.lock();
        try {
            CacheEntry<K, V> entry = storage.remove(key);
            if (entry == null) {
                return false;
            }
            evictionPolicy.onRemove(entry);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            storage.values().forEach(evictionPolicy::onRemove);
            storage.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        return storage.size();
    }

    @Override
    public CacheStats snapshotStats() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        double hitRate = total == 0 ? 0.0 : (double) hits / total;
        return new CacheStats(hits, misses, evictionCount.get(), expiredCount.get(), hitRate);
    }

    public String policyName() {
        return evictionPolicy.name();
    }

    public int capacity() {
        return capacity;
    }

    public List<CacheEntryView<K, V>> snapshotEntries() {
        Instant now = Instant.now();
        List<CacheEntryView<K, V>> entries = new ArrayList<>();
        lock.lock();
        try {
            evictExpiredEntries(now);
            storage.values().forEach(entry -> entries.add(CacheEntryView.from(entry)));
        } finally {
            lock.unlock();
        }
        return entries;
    }

    public int cleanUpExpiredEntries() {
        lock.lock();
        try {
            return evictExpiredEntries(Instant.now());
        } finally {
            lock.unlock();
        }
    }

    private int evictExpiredEntries(Instant now) {
        List<CacheEntry<K, V>> expired = storage.values().stream()
                .filter(entry -> entry.isExpired(now))
                .toList();
        expired.forEach(this::removeExpired);
        return expired.size();
    }

    private void removeExpired(CacheEntry<K, V> entry) {
        storage.remove(entry.getKey());
        evictionPolicy.onRemove(entry);
        expiredCount.incrementAndGet();
    }

    private void evictOne() {
        K evictedKey = evictionPolicy.evictKey();
        if (evictedKey == null) {
            return;
        }
        storage.remove(evictedKey);
        evictionCount.incrementAndGet();
    }
}
