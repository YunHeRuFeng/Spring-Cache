package com.playground.cache.core;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class LfuEvictionPolicy<K, V> implements EvictionPolicy<K, V> {

    private final Map<K, Integer> frequencies = new HashMap<>();
    private final Map<Integer, LinkedHashSet<K>> keysByFrequency = new HashMap<>();
    private int minFrequency = 1;

    @Override
    public void onPut(CacheEntry<K, V> entry) {
        onRemove(entry);
        frequencies.put(entry.getKey(), 1);
        keysByFrequency.computeIfAbsent(1, ignored -> new LinkedHashSet<>()).add(entry.getKey());
        minFrequency = 1;
    }

    @Override
    public void onAccess(CacheEntry<K, V> entry) {
        Integer frequency = frequencies.get(entry.getKey());
        if (frequency == null) {
            return;
        }
        LinkedHashSet<K> currentKeys = keysByFrequency.get(frequency);
        currentKeys.remove(entry.getKey());
        if (currentKeys.isEmpty()) {
            keysByFrequency.remove(frequency);
            if (minFrequency == frequency) {
                minFrequency++;
            }
        }
        int nextFrequency = frequency + 1;
        frequencies.put(entry.getKey(), nextFrequency);
        keysByFrequency.computeIfAbsent(nextFrequency, ignored -> new LinkedHashSet<>()).add(entry.getKey());
    }

    @Override
    public void onRemove(CacheEntry<K, V> entry) {
        Integer frequency = frequencies.remove(entry.getKey());
        if (frequency == null) {
            return;
        }
        LinkedHashSet<K> keys = keysByFrequency.get(frequency);
        if (keys != null) {
            keys.remove(entry.getKey());
            if (keys.isEmpty()) {
                keysByFrequency.remove(frequency);
                if (minFrequency == frequency) {
                    minFrequency = keysByFrequency.keySet().stream().min(Integer::compareTo).orElse(1);
                }
            }
        }
    }

    @Override
    public K evictKey() {
        LinkedHashSet<K> keys = keysByFrequency.get(minFrequency);
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        K victim = keys.iterator().next();
        keys.remove(victim);
        if (keys.isEmpty()) {
            keysByFrequency.remove(minFrequency);
            minFrequency = keysByFrequency.keySet().stream().min(Integer::compareTo).orElse(1);
        }
        frequencies.remove(victim);
        return victim;
    }

    @Override
    public String name() {
        return CachePolicyType.LFU.name();
    }
}
