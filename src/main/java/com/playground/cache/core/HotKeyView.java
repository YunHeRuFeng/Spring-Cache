package com.playground.cache.core;

public record HotKeyView<K, V>(
        K key,
        V value,
        long accessCount
) {
    public static <K, V> HotKeyView<K, V> from(CacheEntryView<K, V> entry) {
        return new HotKeyView<>(entry.key(), entry.value(), entry.accessCount());
    }
}
