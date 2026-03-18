package com.playground.cache.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ARC (Adaptive Replacement Cache) 淘汰策略测试。
 */
class ArcEvictionPolicyTest {

    /**
     * 基本淘汰测试：容量为 2，放入 3 个 key，应淘汰最早未被访问的。
     * 新条目默认进入 T1，按照 LRU 顺序，先插入的先被淘汰。
     */
    @Test
    void shouldEvictFromT1WhenCapacityExceeded() {
        LocalCache<Integer, String> cache = new LocalCache<>(2, new ArcEvictionPolicy<>(2));
        cache.put(1, "A", Duration.ofMinutes(1));
        cache.put(2, "B", Duration.ofMinutes(1));

        // 不访问任何 key，直接插入第 3 个
        cache.put(3, "C", Duration.ofMinutes(1));

        // key=1 应该被从 T1 的 LRU 端淘汰
        assertFalse(cache.get(1).isPresent(), "key 1 应被淘汰");
        assertTrue(cache.get(2).isPresent(), "key 2 应仍在缓存中");
        assertTrue(cache.get(3).isPresent(), "key 3 应在缓存中");
    }

    /**
     * 访问后条目从 T1 提升到 T2，T2 中的条目不会优先被淘汰。
     */
    @Test
    void shouldPromoteToT2OnAccessAndEvictT1First() {
        LocalCache<Integer, String> cache = new LocalCache<>(2, new ArcEvictionPolicy<>(2));
        cache.put(1, "A", Duration.ofMinutes(1));
        cache.put(2, "B", Duration.ofMinutes(1));

        // 访问 key=1，使其提升到 T2
        cache.get(1);

        // 插入 key=3，应淘汰 T1 中的 key=2（而不是 T2 中的 key=1）
        cache.put(3, "C", Duration.ofMinutes(1));

        assertTrue(cache.get(1).isPresent(), "key 1 在 T2 中，不应被淘汰");
        assertFalse(cache.get(2).isPresent(), "key 2 在 T1 中，应被淘汰");
        assertTrue(cache.get(3).isPresent(), "key 3 应在缓存中");
    }

    /**
     * 幽灵命中 B1：被淘汰的 key 再次被 put 时，应增大 p（偏向 recency），
     * 且该 key 进入 T2 而非 T1。
     */
    @Test
    void shouldAdaptWhenGhostHitB1() {
        LocalCache<Integer, String> cache = new LocalCache<>(2, new ArcEvictionPolicy<>(2));
        cache.put(1, "A", Duration.ofMinutes(1));
        cache.put(2, "B", Duration.ofMinutes(1));

        // 插入 key=3，key=1 被淘汰到 B1
        cache.put(3, "C", Duration.ofMinutes(1));
        assertFalse(cache.get(1).isPresent(), "key 1 应已被淘汰");

        // 再次 put key=1（B1 幽灵命中），key=1 应该进入 T2
        cache.put(1, "A2", Duration.ofMinutes(1));
        assertTrue(cache.get(1).isPresent(), "key 1 通过 B1 幽灵命中回到缓存");
    }

    /**
     * 多次淘汰后缓存仍然正常工作。
     */
    @Test
    void shouldHandleMultipleEvictions() {
        LocalCache<Integer, String> cache = new LocalCache<>(3, new ArcEvictionPolicy<>(3));

        // 填满缓存
        cache.put(1, "A", Duration.ofMinutes(1));
        cache.put(2, "B", Duration.ofMinutes(1));
        cache.put(3, "C", Duration.ofMinutes(1));

        // 连续插入触发多次淘汰
        cache.put(4, "D", Duration.ofMinutes(1));
        cache.put(5, "E", Duration.ofMinutes(1));
        cache.put(6, "F", Duration.ofMinutes(1));

        assertEquals(3, cache.size(), "缓存大小应等于容量");
        assertTrue(cache.get(4).isPresent(), "key 4 应在缓存中");
        assertTrue(cache.get(5).isPresent(), "key 5 应在缓存中");
        assertTrue(cache.get(6).isPresent(), "key 6 应在缓存中");
    }

    /**
     * ARC 自适应能力测试：访问模式变化后，能自适应调整。
     * 先频繁访问一批 key 使其进入 T2，然后引入新 key 触发淘汰，
     * 已在 T2 中的热点 key 应受到保护。
     */
    @Test
    void shouldProtectFrequentlyAccessedKeysInT2() {
        LocalCache<Integer, String> cache = new LocalCache<>(3, new ArcEvictionPolicy<>(3));

        cache.put(1, "A", Duration.ofMinutes(1));
        cache.put(2, "B", Duration.ofMinutes(1));
        cache.put(3, "C", Duration.ofMinutes(1));

        // 频繁访问 key=1 和 key=2，使其提升到 T2
        cache.get(1);
        cache.get(1);
        cache.get(2);
        cache.get(2);

        // 插入 key=4，应淘汰 T1 中的 key=3
        cache.put(4, "D", Duration.ofMinutes(1));

        assertTrue(cache.get(1).isPresent(), "key 1 在 T2 中应受保护");
        assertTrue(cache.get(2).isPresent(), "key 2 在 T2 中应受保护");
        assertFalse(cache.get(3).isPresent(), "key 3 在 T1 中应被淘汰");
        assertTrue(cache.get(4).isPresent(), "key 4 应在缓存中");
    }

    /**
     * 策略名称测试。
     */
    @Test
    void shouldReturnArcPolicyName() {
        LocalCache<Integer, String> cache = new LocalCache<>(2, new ArcEvictionPolicy<>(2));
        assertEquals("ARC", cache.policyName());
    }

    /**
     * 显式删除和清空测试。
     */
    @Test
    void shouldHandleRemoveAndClear() {
        LocalCache<Integer, String> cache = new LocalCache<>(3, new ArcEvictionPolicy<>(3));
        cache.put(1, "A", Duration.ofMinutes(1));
        cache.put(2, "B", Duration.ofMinutes(1));

        assertTrue(cache.remove(1));
        assertFalse(cache.get(1).isPresent());
        assertEquals(1, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
    }

    /**
     * 更新已存在的 key 不应导致容量增长。
     */
    @Test
    void shouldUpdateExistingKeyWithoutIncreasingSize() {
        LocalCache<Integer, String> cache = new LocalCache<>(2, new ArcEvictionPolicy<>(2));
        cache.put(1, "A", Duration.ofMinutes(1));
        cache.put(2, "B", Duration.ofMinutes(1));

        // 更新 key=1 的值
        cache.put(1, "A-updated", Duration.ofMinutes(1));

        assertEquals(2, cache.size(), "更新不应增加 size");
        assertEquals("A-updated", cache.get(1).orElse(null), "值应被更新");
    }
}
