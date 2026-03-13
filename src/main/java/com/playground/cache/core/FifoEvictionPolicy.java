package com.playground.cache.core;

public class FifoEvictionPolicy<K, V> extends AbstractLinkedPolicy<K, V> {

    @Override
    public void onAccess(CacheEntry<K, V> entry) {
        // FIFO keeps insertion order stable.
    }

    @Override
    public String name() {
        return CachePolicyType.FIFO.name();
    }
}
