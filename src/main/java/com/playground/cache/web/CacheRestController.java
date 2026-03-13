package com.playground.cache.web;

import com.playground.cache.core.CacheEntryView;
import com.playground.cache.core.CachePolicyType;
import com.playground.cache.core.CacheStats;
import com.playground.cache.core.HotKeyView;
import com.playground.cache.model.Product;
import com.playground.cache.service.CacheManagerService;
import com.playground.cache.service.ProductQueryResult;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/cache")
public class CacheRestController {

    private final CacheManagerService cacheManagerService;

    public CacheRestController(CacheManagerService cacheManagerService) {
        this.cacheManagerService = cacheManagerService;
    }

    @GetMapping("/products/{id}")
    public ProductQueryResult queryProduct(@PathVariable @Min(1) Long id) {
        ProductQueryResult result = cacheManagerService.queryProductWithSource(id);
        if (result.product() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return result;
    }

    @GetMapping("/entries")
    public List<CacheEntryView<Long, Product>> entries() {
        return cacheManagerService.entries();
    }

    @GetMapping("/hot-keys")
    public List<HotKeyView<Long, Product>> hotKeys(@RequestParam(defaultValue = "5") int limit) {
        return cacheManagerService.hotKeys(limit);
    }

    @GetMapping("/stats")
    public CacheStats stats() {
        return cacheManagerService.stats();
    }

    @PostMapping("/policy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void switchPolicy(@RequestParam CachePolicyType policy) {
        cacheManagerService.switchPolicy(policy);
    }

    @DeleteMapping("/entries/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long key) {
        if (!cacheManagerService.remove(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cache key not found");
        }
    }

    @DeleteMapping("/entries")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() {
        cacheManagerService.clear();
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return Map.of(
                "policy", cacheManagerService.policyName(),
                "size", cacheManagerService.size(),
                "capacity", cacheManagerService.capacity(),
                "lastCleanupCount", cacheManagerService.lastCleanupCount(),
                "lastCleanupAt", cacheManagerService.lastCleanupAt(),
                "lastQuerySource", cacheManagerService.lastQuerySource(),
                "lastQueryKey", cacheManagerService.lastQueryKey(),
                "stats", cacheManagerService.stats()
        );
    }
}
