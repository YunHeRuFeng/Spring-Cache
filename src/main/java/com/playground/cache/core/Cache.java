package com.playground.cache.core;

import java.time.Duration;
import java.util.Optional;

public interface Cache<K, V> {

    void put(K key, V value, Duration ttl);

    Optional<V> get(K key);

    Optional<CacheEntryView<K, V>> getEntry(K key);

    boolean remove(K key);

    void clear();

    int size();

    CacheStats snapshotStats();
}
