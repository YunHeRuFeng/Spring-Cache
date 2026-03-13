package com.playground.cache.service;

public enum QuerySource {
    CACHE_HIT("来自缓存"),
    DATABASE_LOAD("来自数据库"),
    RELOADED_AFTER_EXPIRE("缓存已过期，已重新加载"),
    NOT_FOUND("未找到数据");

    private final String label;

    QuerySource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
