package com.playground.cache.core;

import java.time.Instant;

public class CacheEntry<K, V> {
    private final K key;
    private V value;
    private final Instant createdAt;
    private Instant expireAt;
    private long accessCount;
    private Instant lastAccessAt;

    public CacheEntry(K key, V value, Instant createdAt, Instant expireAt) {
        this.key = key;
        this.value = value;
        this.createdAt = createdAt;
        this.expireAt = expireAt;
        this.lastAccessAt = createdAt;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt;
    }

    public long getAccessCount() {
        return accessCount;
    }

    public Instant getLastAccessAt() {
        return lastAccessAt;
    }

    public void recordAccess(Instant accessAt) {
        this.accessCount++;
        this.lastAccessAt = accessAt;
    }

    public boolean isExpired(Instant now) {
        return expireAt != null && now.isAfter(expireAt);
    }
}
