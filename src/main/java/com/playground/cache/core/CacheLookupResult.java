package com.playground.cache.core;

import java.util.Optional;

public record CacheLookupResult<K, V>(
        CacheLookupStatus status,
        CacheEntryView<K, V> entry
) {
    public Optional<V> value() {
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.value());
    }
}
