package com.playground.cache.core;

public interface EvictionPolicy<K, V> {

    void onPut(CacheEntry<K, V> entry);

    void onAccess(CacheEntry<K, V> entry);

    void onRemove(CacheEntry<K, V> entry);

    K evictKey();

    String name();
}
