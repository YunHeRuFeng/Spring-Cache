package com.playground.cache.service;

import com.playground.cache.model.Product;

public record ProductQueryResult(
        Product product,
        QuerySource source
) {
}
