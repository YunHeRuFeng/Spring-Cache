# 🗺️ Spring Cache Demo 项目完整阅读指南

## 一、项目全景图

```text
请求流入                                        数据源
  │                                              │
  ▼                                              ▼
┌─────────────────────┐    ┌──────────────────┐  ┌──────────────────┐
│    Web 入口层        │───▶│   业务服务层      │──▶│ ProductRepository│
│ CacheRestController │    │CacheManagerService│  │ (模拟数据库)      │
│ DashboardController │    │                  │  └──────────────────┘
└─────────────────────┘    └───────┬──────────┘
                                   │
                                   ▼
                          ┌──────────────────┐
                          │   核心缓存层      │
                          │   LocalCache      │
                          │ ┌──────────────┐  │
                          │ │ CacheEntry   │  │
                          │ │ CacheStats   │  │
                          │ └──────────────┘  │
                          └───────┬──────────┘
                                  │
                                  ▼
                          ┌──────────────────┐
                          │   策略实现层      │
                          │ EvictionPolicy   │
                          │ ├─ LRU (链表)    │
                          │ ├─ LFU (频率桶)  │
                          │ └─ FIFO (链表)   │
                          └──────────────────┘
```

### 包结构对应表

| 层级 | 包路径 | 核心文件 |
|---|---|---|
| 启动入口 | `com.playground.cache` | `LocalCacheDemoApplication.java` |
| Web 入口层 | `com.playground.cache.web` | `CacheRestController.java`, `DashboardController.java` |
| 业务服务层 | `com.playground.cache.service` | `CacheManagerService.java`, `ProductRepository.java`, `QuerySource.java`, `ProductQueryResult.java` |
| 核心缓存层 | `com.playground.cache.core` | `Cache.java`, `LocalCache.java`, `CacheEntry.java`, `CacheEntryView.java`, `CacheLookupResult.java`, `CacheLookupStatus.java`, `CacheStats.java` |
| 策略实现层 | `com.playground.cache.core` | `EvictionPolicy.java`, `AbstractLinkedPolicy.java`, `LruEvictionPolicy.java`, `LfuEvictionPolicy.java`, `FifoEvictionPolicy.java` |
| 数据模型 | `com.playground.cache.model` | `Product.java` |
| 测试验证层 | `com.playground.cache.core(test)` | `LocalCacheTest.java` |

---

## 二、推荐阅读起点 —— 从 `CacheManagerService.queryProductWithSource()` 开始

### 为什么从这个函数开始？

它是整个系统的“十字路口”：

1. 它被 Web 层两个 Controller 直接调用（是所有请求的汇聚点）  
2. 它向下调用 `LocalCache.lookup()` 完成缓存查找  
3. 在缓存未命中时调用 `ProductRepository.findById()` 获取数据  
4. 再通过 `LocalCache.put()` 回填缓存  
5. 它涵盖了所有四种查询结果：`CACHE_HIT`、`DATABASE_LOAD`、`RELOADED_AFTER_EXPIRE`、`NOT_FOUND`  

读完这一个函数，你就能理解整个系统的主线数据流。

---

## 三、逐层详解

## 第 1 层：启动入口

**文件：** `LocalCacheDemoApplication.java`

```java
@EnableScheduling      // ← 开启 Spring 定时任务，用于后台清理过期缓存
@SpringBootApplication // ← Spring Boot 自动配置
public class LocalCacheDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocalCacheDemoApplication.class, args);
    }
}
```

- 作用：唯一的应用启动点。`@EnableScheduling` 是关键注解，没有它 `CacheManagerService.cleanExpiredEntries()` 上的 `@Scheduled` 就不会生效。  
- 配置：`application.yml` 设置了端口 `8080`，清理间隔 `10000ms`（10 秒），以及关闭了 Thymeleaf 模板缓存（方便开发热加载）。

---

## 第 2 层：Web 入口层

### 2.1 `CacheRestController.java` — REST API 入口

调用关系：所有方法都委托给 `CacheManagerService`

| HTTP 方法 + 路径 | 调用的 Service 方法 | 作用 |
|---|---|---|
| `GET /api/cache/products/{id}` | `queryProductWithSource(id)` | 查询商品（缓存或数据库） |
| `GET /api/cache/entries` | `entries()` | 查看所有缓存条目 |
| `GET /api/cache/hot-keys?limit=5` | `hotKeys(limit)` | 查看热点 Key |
| `GET /api/cache/stats` | `stats()` | 查看命中率等统计 |
| `POST /api/cache/policy?policy=LFU` | `switchPolicy(policy)` | 切换淘汰策略 |
| `DELETE /api/cache/entries/{key}` | `remove(key)` | 删除指定缓存 |
| `DELETE /api/cache/entries` | `clear()` | 清空全部缓存 |
| `GET /api/cache/summary` | 多个 getter | 综合摘要 |

**关键理解点：**  
这个 Controller 是一个“薄代理”，不包含业务逻辑，只做参数校验（`@Min(1)`、`@Validated`）和 HTTP 状态码映射（`ResponseStatusException`）。

### 2.2 `DashboardController.java` — Web 页面入口

与 REST Controller 的区别：

- 使用 `@Controller`（不是 `@RestController`），返回视图名（`"dashboard"`）
- 使用 `Model` 传数据给 Thymeleaf 模板渲染 HTML
- 表单提交后用 `RedirectAttributes + "redirect:/"` 实现 PRG 模式（Post-Redirect-Get）

核心方法 `dashboard()` 一次性查询所有展示数据：

```java
model.addAttribute("products", cacheManagerService.allProducts());     // 所有商品
model.addAttribute("entries", cacheManagerService.entries());          // 缓存条目
model.addAttribute("hotKeys", cacheManagerService.hotKeys(5));         // Top 5 热键
model.addAttribute("stats", cacheManagerService.stats());              // 统计数据
model.addAttribute("currentPolicy", cacheManagerService.policyName()); // 当前策略
```

---

## 第 3 层：业务服务层

### 3.1 `CacheManagerService.java` — 核心编排器（最重要文件之一）

构造函数 —— 创建默认缓存实例：

```java
this.cache = new LocalCache<>(DEFAULT_CAPACITY /*8*/, new LruEvictionPolicy<>());
```

`queryProductWithSource(Long id)` —— 核心读取路径（建议第一个精读）：

1. `cache.lookup(id)`  
   - `HIT` → 直接返回缓存值（`QuerySource.CACHE_HIT`）  
   - `MISS` → 去数据库查 → 写入缓存（`QuerySource.DATABASE_LOAD`）  
   - `EXPIRED` → 去数据库查 → 写入缓存（`QuerySource.RELOADED_AFTER_EXPIRE`）  
2. 如果数据库也没有 → `QuerySource.NOT_FOUND`

`switchPolicy(CachePolicyType)` —— 策略热切换：

```java
LocalCache<Long, Product> replacement = switch (policyType) {
    case LRU  -> new LocalCache<>(DEFAULT_CAPACITY, new LruEvictionPolicy<>());
    case LFU  -> new LocalCache<>(DEFAULT_CAPACITY, new LfuEvictionPolicy<>());
    case FIFO -> new LocalCache<>(DEFAULT_CAPACITY, new FifoEvictionPolicy<>());
};
this.cache = replacement;  // 直接替换整个缓存实例
preload(List.of(1L, 2L, 3L)); // 预热
```

> 注意：切换策略会丢弃原缓存全部数据，创建全新实例后重新预热。

`cleanExpiredEntries()` —— 定时后台清理：

```java
@Scheduled(fixedDelayString = "${cache.cleanup.fixed-delay-ms:10000}")
public void cleanExpiredEntries() {
    int removed = cache.cleanUpExpiredEntries();
    lastCleanupCount.set(removed);
    lastCleanupAt.set(Instant.now());
}
```

`preloadHotProducts()` —— 应用启动时预热缓存：

```java
@PostConstruct
void preloadHotProducts() {
    preload(List.of(1L, 2L, 3L));
}
```

### 3.2 `ProductRepository.java` — 模拟数据源

```java
private void simulateSlowQuery() {
    Thread.sleep(600); // 人为加 600ms 延迟，模拟真实数据库
}
```

- `init()` 用 `@PostConstruct` 预填充 20 个商品到内存 HashMap  
- 设计目的：让你在 Dashboard 上直观感受到“缓存命中 vs 数据库加载”的速度差异

### 3.3 辅助类型

| 文件 | 类型 | 作用 |
|---|---|---|
| `QuerySource.java` | enum | 4 种查询来源标签（来自缓存、来自数据库等） |
| `ProductQueryResult.java` | record | 将 Product 和 QuerySource 打包返回 |
| `Product.java` | record | 商品模型（id, name, category, price, description） |

---

## 第 4 层：核心缓存层

### 4.1 `Cache.java` — 顶层接口

```java
public interface Cache<K, V> {
    void put(K key, V value, Duration ttl);
    Optional<V> get(K key);
    Optional<CacheEntryView<K, V>> getEntry(K key);
    boolean remove(K key);
    void clear();
    int size();
    CacheStats snapshotStats();
}
```

定义了缓存通用契约，`LocalCache` 是其唯一实现。

### 4.2 `LocalCache.java` — 缓存引擎（最重要文件）

内部结构：

```java
ConcurrentHashMap<K, CacheEntry<K, V>> storage;
EvictionPolicy<K, V> evictionPolicy;
int capacity;
ReentrantLock lock;
AtomicLong hitCount/missCount/evictionCount/expiredCount;
```

`put()` 方法 —— 写入路径（建议第二个精读）：

1. 加锁  
2. 若 key 已存在 → 更新 value/expireAt，通知策略 `onAccess()`  
3. 清理过期条目 `evictExpiredEntries()`  
4. 若仍满 → `evictOne()`（让策略决定淘汰谁）  
5. 创建新 `CacheEntry`，放入 `storage`，通知策略 `onPut()`  
6. 解锁

`lookup()` 方法 —— 读取路径（被 Service 调用）：

1. 加锁  
2. `storage.get(key)`  
   - `null` → `missCount++`，返回 `MISS`  
   - `isExpired()` → 删除条目 + `missCount++`，返回 `EXPIRED`  
   - 有效命中 → `recordAccess()` + `onAccess()` + `hitCount++`，返回 `HIT`  
3. 解锁

`evictOne()` 方法 —— 淘汰路径：

```java
private void evictOne() {
    K evictedKey = evictionPolicy.evictKey();
    storage.remove(evictedKey);
    evictionCount.incrementAndGet();
}
```

**线程安全设计：**  
虽然用了 `ConcurrentHashMap`，但 `put/lookup/remove/clear` 仍在 `ReentrantLock` 保护下执行，保证“判断容量 → 淘汰 → 插入”等复合操作原子性，以及和策略内部状态一致。

### 4.3 数据载体类

| 类 | 类型 | 核心字段 | 作用 |
|---|---|---|---|
| `CacheEntry<K,V>` | class | key, value, createdAt, expireAt, accessCount, lastAccessAt | 缓存条目（可变，内部使用） |
| `CacheEntryView<K,V>` | record | 同上 | 只读快照（暴露给外部） |
| `CacheLookupResult<K,V>` | record | status, entry | lookup 返回值 |
| `CacheLookupStatus` | enum | HIT, MISS, EXPIRED | 三种查找状态 |
| `CacheStats` | record | hitCount, missCount, evictionCount, expiredCount, hitRate | 统计快照 |
| `HotKeyView<K,V>` | record | key, value, accessCount | 热点 Key 展示 |
| `CachePolicyType` | enum | LRU, LFU, FIFO | 策略枚举 |

`CacheEntry` vs `CacheEntryView` 设计意图：

- `CacheEntry` 是可变对象（`setValue()`、`setExpireAt()`、`recordAccess()`），只在加锁区使用  
- `CacheEntryView` 是不可变 record，通过 `CacheEntryView.from(entry)` 创建快照，安全暴露给外部  

---

## 第 5 层：策略实现层

### 5.1 `EvictionPolicy<K,V>` 接口 — 策略契约

```java
void onPut(CacheEntry<K, V> entry);
void onAccess(CacheEntry<K, V> entry);
void onRemove(CacheEntry<K, V> entry);
K evictKey();
String name();
```

`LocalCache` 在 `put()/lookup()/remove()/clear()` 中调用这些回调，策略通过回调维护内部结构。

### 5.2 `AbstractLinkedPolicy.java` — 双向链表基类

为 LRU / FIFO 提供共享链表基础设施：

```text
head <-> Node(key1) <-> Node(key2) <-> ... <-> tail
         ↑ 最旧/最少使用                 ↑ 最新/最近使用
```

| 方法 | 作用 |
|---|---|
| `onPut()` | 创建新节点并追加到尾部 |
| `onRemove()` | 从链表摘除节点 |
| `evictKey()` | 返回头部第一个真实节点 key |
| `moveToTail(key)` | 将节点移到尾部（LRU 用） |
| `append(node)` | 在尾部前插入节点 |
| `unlink(node)` | 摘除节点前后指针 |

内部类 `Node<K>` 仅存 `key/prev/next`，用 `HashMap<K, Node<K>> nodeIndex` 做 O(1) 查找。

### 5.3 三种具体策略对比

| 策略 | 继承 | onAccess() 行为 | 淘汰谁 |
|---|---|---|---|
| LRU | `AbstractLinkedPolicy` | `moveToTail(key)` | 链表头部（最久未访问） |
| FIFO | `AbstractLinkedPolicy` | 空操作（访问不影响顺序） | 链表头部（最早插入） |
| LFU | 直接实现 `EvictionPolicy` | 频率 +1，移到更高频率桶 | 最低频率桶第一个 key |

LFU 数据结构：

```java
Map<K, Integer> frequencies;
Map<Integer, LinkedHashSet<K>> keysByFrequency;
int minFrequency;
```

`evictKey()` 取 `keysByFrequency.get(minFrequency)` 的第一个元素，即同频率下最早进入的 key。

---

## 第 6 层：测试验证层

**文件：** `LocalCacheTest.java`

| 测试方法 | 验证点 |
|---|---|
| `shouldEvictLeastRecentlyUsedEntry` | LRU：访问过 key=1 后插入 key=3，key=2 被淘汰 |
| `shouldEvictLeastFrequentlyUsedEntry` | LFU：key=2 频率更低被淘汰 |
| `shouldEvictFirstInEntryUnderFifo` | FIFO：key=1（最先进入）被淘汰 |
| `shouldExpireEntryAfterTtl` | TTL 过期后 `get` 返回空，`expiredCount` 增加 |
| `shouldCleanExpiredEntriesInBackgroundStyleSweep` | `cleanUpExpiredEntries()` 批量清除过期条目 |
| `shouldSortHotKeysByAccessCount` | 热点 Key 按访问次数降序排列 |

---

## 四、完整调用链追踪（以“查询商品”为例）

```text
用户请求 GET /api/cache/products/5
  │
  ▼
CacheRestController.queryProduct(5)                          [web 层]
  │
  ▼
CacheManagerService.queryProductWithSource(5)                [service 层]
  │
  ├──▶ LocalCache.lookup(5)                                  [core 层]
  │      │
  │      ├──▶ storage.get(5)                                 → null => MISS
  │      ├──▶ entry.isExpired(now)                           → 过期 => EXPIRED
  │      │     └──▶ removeExpired(entry)
  │      │           ├── storage.remove(key)
  │      │           ├── evictionPolicy.onRemove(entry)      [策略层]
  │      │           └── expiredCount++
  │      └──▶ entry.recordAccess(now)                        → 命中
  │            └── evictionPolicy.onAccess(entry)            [策略层]
  │                 ├── LRU: moveToTail(key)
  │                 ├── LFU: frequency++, 移桶
  │                 └── FIFO: 不处理
  │
  ├── (如果 MISS/EXPIRED)
  │    ├──▶ ProductRepository.findById(5)                    [repository 层]
  │    │     └── Thread.sleep(600) + HashMap.get(5)
  │    └──▶ LocalCache.put(5, product, 45s)                  [core 层]
  │           ├── evictExpiredEntries()
  │           ├── if(满了) evictOne()
  │           │    └── evictionPolicy.evictKey()             [策略层]
  │           ├── new CacheEntry(...)
  │           ├── storage.put(5, entry)
  │           └── evictionPolicy.onPut(entry)                [策略层]
  │
  └──▶ return ProductQueryResult(product, source)
```

---

## 五、分阶段阅读清单

## 🟢 阶段一：30 分钟快速入门（理解主线）

目标：搞清楚“一个查询请求从进来到返回经过了哪些代码”

| 顺序 | 文件 | 重点看什么 |
|---|---|---|
| 1 | `Product.java` | 数据模型长什么样 |
| 2 | `QuerySource.java` | 4 种查询来源 |
| 3 | `ProductQueryResult.java` | Product + Source 封装 |
| 4 | `CacheRestController.java` 的 `queryProduct()` | HTTP 入口调谁 |
| 5 | `CacheManagerService.queryProductWithSource()` ⭐ | HIT / MISS / EXPIRED / NOT_FOUND |
| 6 | `ProductRepository.java` | 模拟数据库 + 600ms 延迟 |
| 7 | `CacheLookupStatus.java` + `CacheLookupResult.java` | lookup 返回结构 |
| 8 | `LocalCache.lookup()` | 缓存查找核心逻辑 |

## 🟡 阶段二：2 小时深入理解（掌握缓存核心 + 策略）

目标：完全理解缓存的存取、淘汰、过期机制和三种策略实现

| 顺序 | 文件 | 重点看什么 |
|---|---|---|
| 9 | `Cache.java` | 缓存接口定义 |
| 10 | `CacheEntry.java` | `recordAccess()`、`isExpired()` |
| 11 | `CacheEntryView.java` | 不可变快照 `from()` |
| 12 | `LocalCache.put()` ⭐ | 更新/过期清理/淘汰/插入 |
| 13 | `LocalCache.remove()` + `clear()` | 删除逻辑 + 策略通知 |
| 14 | `LocalCache.snapshotEntries()` + `snapshotStats()` | 锁内快照复制 |
| 15 | `EvictionPolicy.java` | 4 回调 + 1 淘汰方法 |
| 16 | `AbstractLinkedPolicy.java` ⭐ | 双向链表 + HashMap O(1) |
| 17 | `LruEvictionPolicy.java` | `onAccess()` moveToTail |
| 18 | `FifoEvictionPolicy.java` | `onAccess()` 空实现 |
| 19 | `LfuEvictionPolicy.java` ⭐ | 频率桶 + minFrequency |
| 20 | `LocalCacheTest.java` | 用测试验证策略理解 |

## 🔴 阶段三：半天掌握全局（编排 + 页面 + 定时任务）

目标：理解完整运行时行为，包括生命周期、定时任务、策略切换、Dashboard 展示

| 顺序 | 文件 | 重点看什么 |
|---|---|---|
| 21 | `application.yml` | 端口、TTL、清理间隔 |
| 22 | `LocalCacheDemoApplication.java` | `@EnableScheduling` |
| 23 | `CacheManagerService.preloadHotProducts()` | 启动预热 |
| 24 | `CacheManagerService.cleanExpiredEntries()` | 定时清理 |
| 25 | `CacheManagerService.switchPolicy()` ⭐ | 策略热切换（替换实例 + 预热） |
| 26 | `CacheManagerService.entries()` + `hotKeys()` | 排序与视图转换 |
| 27 | `CacheStats.java` / `HotKeyView.java` / `CachePolicyType.java` | 展示 DTO |
| 28 | `DashboardController.dashboard()` | 页面渲染数据流 |
| 29 | `CacheRestController.java` 全部方法 | REST API 全览 |
| 30 | 回读 `LocalCache` 全文 | 并发与一致性设计 |

---

## 六、关键设计模式速查

| 设计模式 | 在哪里体现 | 具体代码 |
|---|---|---|
| 策略模式 | 淘汰策略可插拔替换 | `EvictionPolicy` + 3 个实现类 |
| 模板方法 | 链表策略共享骨架 | `AbstractLinkedPolicy` 提供通用逻辑，子类改 `onAccess` |
| 快照/不可变视图 | 缓存条目安全暴露 | `CacheEntry`（可变）→ `CacheEntryView.from()`（不可变） |
| 委托 | Web 层无业务逻辑 | Controller → Service → LocalCache |
| 哨兵节点 | 链表边界简化 | `AbstractLinkedPolicy` 的 `head/tail` 虚拟节点 |
| 原子引用/计数器 | 线程安全状态追踪 | `AtomicReference`、`AtomicLong` |

---

## 附：一句话阅读建议

先抓主线（`queryProductWithSource()` + `lookup()`），再看引擎（`put()` + 三策略），最后补全局（定时清理、策略切换、Dashboard），你会最快建立完整心智模型。