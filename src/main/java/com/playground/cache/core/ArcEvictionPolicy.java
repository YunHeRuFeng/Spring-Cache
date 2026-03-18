package com.playground.cache.core;

import java.util.LinkedHashSet;

/**
 * ARC (Adaptive Replacement Cache) 淘汰策略。
 * <p>
 * 维护 4 个 LRU 链表和 1 个自适应参数 p：
 * <ul>
 *     <li>T1 — 最近访问过一次的缓存条目（recency）</li>
 *     <li>T2 — 最近访问过多次的缓存条目（frequency）</li>
 *     <li>B1 — T1 的幽灵条目，仅记录 key</li>
 *     <li>B2 — T2 的幽灵条目，仅记录 key</li>
 *     <li>p  — T1 的目标大小，命中 B1 时增大（偏向最近性），命中 B2 时减小（偏向频率）</li>
 * </ul>
 * 通过幽灵列表动态学习工作负载特征，在 LRU 和 LFU 之间自适应平衡。
 */
public class ArcEvictionPolicy<K, V> implements EvictionPolicy<K, V> {

    /** 最近访问一次的缓存条目（真实缓存）。按插入顺序排列，头部为 LRU 端。 */
    private final LinkedHashSet<K> t1 = new LinkedHashSet<>();
    /** 最近访问多次的缓存条目（真实缓存）。按访问顺序排列，头部为 LRU 端。 */
    private final LinkedHashSet<K> t2 = new LinkedHashSet<>();
    /** T1 的幽灵列表（只记录 key，不记录 value）。 */
    private final LinkedHashSet<K> b1 = new LinkedHashSet<>();
    /** T2 的幽灵列表（只记录 key，不记录 value）。 */
    private final LinkedHashSet<K> b2 = new LinkedHashSet<>();

    /** 缓存总容量。 */
    private final int capacity;
    /** 自适应目标参数：T1 的目标大小。 */
    private int p = 0;

    public ArcEvictionPolicy(int capacity) {
        this.capacity = capacity;
    }

    /**
     * 新条目插入缓存时调用。
     * <p>
     * 根据 key 是否在幽灵列表中出现来调整 p 值，并将 key 放入 T1 或 T2。
     * 同时维护 B1/B2 的大小上限。
     */
    @Override
    public void onPut(CacheEntry<K, V> entry) {
        K key = entry.getKey();

        // 如果 key 已在 T1 或 T2 中（更新场景），先移除再重新放入
        if (t1.remove(key) || t2.remove(key)) {
            t2.add(key);
            return;
        }

        if (b1.remove(key)) {
            // Case: 幽灵命中 B1 → 最近性很重要，增大 p
            int delta = Math.max(1, b2.size() / Math.max(b1.size(), 1));
            p = Math.min(p + delta, capacity);
            t2.add(key);
        } else if (b2.remove(key)) {
            // Case: 幽灵命中 B2 → 频率很重要，减小 p
            int delta = Math.max(1, b1.size() / Math.max(b2.size(), 1));
            p = Math.max(p - delta, 0);
            t2.add(key);
        } else {
            // Case: 完全未命中 → 放入 T1
            // 控制 B1 大小
            if (t1.size() + b1.size() >= capacity) {
                if (!b1.isEmpty()) {
                    removeFirst(b1);
                }
            }
            // 控制总幽灵列表大小
            if (t1.size() + t2.size() + b1.size() + b2.size() >= 2 * capacity) {
                if (!b2.isEmpty()) {
                    removeFirst(b2);
                }
            }
            t1.add(key);
        }
    }

    /**
     * 缓存命中时调用。将条目从 T1 提升到 T2（或在 T2 内移到 MRU 端）。
     */
    @Override
    public void onAccess(CacheEntry<K, V> entry) {
        K key = entry.getKey();
        if (t1.remove(key)) {
            // 从 T1 提升到 T2
            t2.add(key);
        } else if (t2.remove(key)) {
            // 在 T2 内移到 MRU 端（LinkedHashSet 重新 add 到末尾）
            t2.add(key);
        }
    }

    /**
     * 条目被显式删除或过期清理时调用。
     */
    @Override
    public void onRemove(CacheEntry<K, V> entry) {
        K key = entry.getKey();
        t1.remove(key);
        t2.remove(key);
        // 不清理 B1/B2，保留幽灵记录用于学习
    }

    /**
     * 选择一个要淘汰的 key。执行 replace 操作：
     * 根据 p 值决定从 T1 还是 T2 淘汰，被淘汰的 key 降级到对应的幽灵列表。
     *
     * @return 应被淘汰的 key，如果缓存为空则返回 null
     */
    @Override
    public K evictKey() {
        return replace();
    }

    @Override
    public String name() {
        return CachePolicyType.ARC.name();
    }

    /**
     * ARC 核心操作：根据自适应参数 p 决定从 T1 还是 T2 淘汰。
     * <p>
     * 规则：
     * <ul>
     *     <li>如果 T1 非空 且 (|T1| > p 或 T2 为空)，则从 T1 的 LRU 端淘汰，降级到 B1</li>
     *     <li>否则从 T2 的 LRU 端淘汰，降级到 B2</li>
     * </ul>
     */
    private K replace() {
        K victim;
        if (!t1.isEmpty() && (t1.size() > p || (t2.isEmpty() && t1.size() == p))) {
            victim = removeFirst(t1);
            b1.add(victim);
        } else if (!t2.isEmpty()) {
            victim = removeFirst(t2);
            b2.add(victim);
        } else if (!t1.isEmpty()) {
            victim = removeFirst(t1);
            b1.add(victim);
        } else {
            return null;
        }
        return victim;
    }

    /**
     * 从 LinkedHashSet 中移除并返回第一个元素（LRU 端）。
     */
    private K removeFirst(LinkedHashSet<K> set) {
        K first = set.iterator().next();
        set.remove(first);
        return first;
    }
}
