package com.playground.cache.core;

import java.util.HashMap;
import java.util.Map;

abstract class AbstractLinkedPolicy<K, V> implements EvictionPolicy<K, V> {

    private final Map<K, Node<K>> nodeIndex = new HashMap<>();
    private final Node<K> head = new Node<>(null);
    private final Node<K> tail = new Node<>(null);

    protected AbstractLinkedPolicy() {
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public void onPut(CacheEntry<K, V> entry) {
        removeIfPresent(entry.getKey());
        Node<K> node = new Node<>(entry.getKey());
        nodeIndex.put(entry.getKey(), node);
        append(node);
    }

    @Override
    public void onRemove(CacheEntry<K, V> entry) {
        removeIfPresent(entry.getKey());
    }

    @Override
    public K evictKey() {
        if (head.next == tail) {
            return null;
        }
        Node<K> node = head.next;
        unlink(node);
        nodeIndex.remove(node.key);
        return node.key;
    }

    protected void moveToTail(K key) {
        Node<K> node = nodeIndex.get(key);
        if (node == null) {
            return;
        }
        unlink(node);
        append(node);
    }

    private void removeIfPresent(K key) {
        Node<K> node = nodeIndex.remove(key);
        if (node != null) {
            unlink(node);
        }
    }

    private void append(Node<K> node) {
        Node<K> prev = tail.prev;
        prev.next = node;
        node.prev = prev;
        node.next = tail;
        tail.prev = node;
    }

    private void unlink(Node<K> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private static final class Node<K> {
        private final K key;
        private Node<K> prev;
        private Node<K> next;

        private Node(K key) {
            this.key = key;
        }
    }
}
