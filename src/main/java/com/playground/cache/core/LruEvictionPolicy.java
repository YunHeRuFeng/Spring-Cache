package com.playground.cache.core;

public class LruEvictionPolicy<K, V> extends AbstractLinkedPolicy<K, V> {

    @Override
    public void onAccess(CacheEntry<K, V> entry) {
        moveToTail(entry.getKey());
    }

    @Override
    public String name() {
        return CachePolicyType.LRU.name();
    }
}
