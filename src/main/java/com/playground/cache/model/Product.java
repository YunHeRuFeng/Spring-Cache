package com.playground.cache.model;

import java.math.BigDecimal;

public record Product(
        Long id,
        String name,
        String category,
        BigDecimal price,
        String description
) {
}
