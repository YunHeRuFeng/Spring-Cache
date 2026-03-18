package com.playground.cache.service;

import com.playground.cache.core.CacheEntryView;
import com.playground.cache.core.CacheLookupResult;
import com.playground.cache.core.CacheLookupStatus;
import com.playground.cache.core.CachePolicyType;
import com.playground.cache.core.CacheStats;
import com.playground.cache.core.ArcEvictionPolicy;
import com.playground.cache.core.FifoEvictionPolicy;
import com.playground.cache.core.HotKeyView;
import com.playground.cache.core.LfuEvictionPolicy;
import com.playground.cache.core.LocalCache;
import com.playground.cache.core.LruEvictionPolicy;
import com.playground.cache.model.Product;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CacheManagerService {

    private static final int DEFAULT_CAPACITY = 8;
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(45);

    private final ProductRepository productRepository;
    private final AtomicInteger lastCleanupCount = new AtomicInteger();
    private final AtomicReference<Instant> lastCleanupAt = new AtomicReference<>();
    private final AtomicReference<QuerySource> lastQuerySource = new AtomicReference<>();
    private final AtomicReference<Long> lastQueryKey = new AtomicReference<>();
    private LocalCache<Long, Product> cache;

    public CacheManagerService(ProductRepository productRepository) {
        this.productRepository = productRepository;
        this.cache = new LocalCache<>(DEFAULT_CAPACITY, new LruEvictionPolicy<>());
    }

    @PostConstruct
    void preloadHotProducts() {
        preload(List.of(1L, 2L, 3L));
    }

    public Optional<Product> queryProduct(Long id) {
        ProductQueryResult result = queryProductWithSource(id);
        return result.product() == null ? Optional.empty() : Optional.of(result.product());
    }

    public ProductQueryResult queryProductWithSource(Long id) {
        CacheLookupResult<Long, Product> lookupResult = cache.lookup(id);
        if (lookupResult.status() == CacheLookupStatus.HIT) {
            Product product = lookupResult.entry().value();
            lastQuerySource.set(QuerySource.CACHE_HIT);
            lastQueryKey.set(id);
            return new ProductQueryResult(product, QuerySource.CACHE_HIT);
        }
        Optional<Product> product = productRepository.findById(id);
        if (product.isEmpty()) {
            lastQuerySource.set(QuerySource.NOT_FOUND);
            lastQueryKey.set(id);
            return new ProductQueryResult(null, QuerySource.NOT_FOUND);
        }
        cache.put(id, product.get(), DEFAULT_TTL);
        QuerySource source = lookupResult.status() == CacheLookupStatus.EXPIRED
                ? QuerySource.RELOADED_AFTER_EXPIRE
                : QuerySource.DATABASE_LOAD;
        lastQuerySource.set(source);
        lastQueryKey.set(id);
        return new ProductQueryResult(product.get(), source);
    }

    public List<Product> allProducts() {
        return productRepository.findAll();
    }

    public List<CacheEntryView<Long, Product>> entries() {
        return cache.snapshotEntries().stream()
                .sorted(Comparator.comparing(CacheEntryView::key))
                .toList();
    }

    public List<HotKeyView<Long, Product>> hotKeys(int limit) {
        return cache.snapshotEntries().stream()
                .sorted(Comparator
                        .comparing(CacheEntryView<Long, Product>::accessCount).reversed()
                        .thenComparing(CacheEntryView::key))
                .limit(limit)
                .map(HotKeyView::from)
                .toList();
    }

    public CacheStats stats() {
        return cache.snapshotStats();
    }

    public String policyName() {
        return cache.policyName();
    }

    public int size() {
        return cache.size();
    }

    public int capacity() {
        return cache.capacity();
    }

    public void clear() {
        cache.clear();
    }

    public boolean remove(Long key) {
        return cache.remove(key);
    }

    public void switchPolicy(CachePolicyType policyType) {
        LocalCache<Long, Product> replacement = switch (policyType) {
            case LRU -> new LocalCache<>(DEFAULT_CAPACITY, new LruEvictionPolicy<>());
            case LFU -> new LocalCache<>(DEFAULT_CAPACITY, new LfuEvictionPolicy<>());
            case FIFO -> new LocalCache<>(DEFAULT_CAPACITY, new FifoEvictionPolicy<>());
            case ARC -> new LocalCache<>(DEFAULT_CAPACITY, new ArcEvictionPolicy<>(DEFAULT_CAPACITY));
        };
        this.cache = replacement;
        lastCleanupCount.set(0);
        lastCleanupAt.set(null);
        preload(List.of(1L, 2L, 3L));
    }

    public int lastCleanupCount() {
        return lastCleanupCount.get();
    }

    public Instant lastCleanupAt() {
        return lastCleanupAt.get();
    }

    public QuerySource lastQuerySource() {
        return lastQuerySource.get();
    }

    public Long lastQueryKey() {
        return lastQueryKey.get();
    }

    @Scheduled(fixedDelayString = "${cache.cleanup.fixed-delay-ms:10000}")
    public void cleanExpiredEntries() {
        int removed = cache.cleanUpExpiredEntries();
        lastCleanupCount.set(removed);
        lastCleanupAt.set(Instant.now());
    }

    private void preload(List<Long> productIds) {
        for (Long productId : productIds) {
            productRepository.findById(productId)
                    .ifPresent(product -> cache.put(productId, product, DEFAULT_TTL));
        }
    }
}
