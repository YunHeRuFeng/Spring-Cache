# Java 本地缓存项目新手上门指南（按“可讲清楚”设计）

这份指南的目标只有一个：

> 让你第一次读这个项目，就能快速建立全局认知，并且可以把关键代码“讲给别人听”。

项目位置：`Playground/java-project`

---

## 0. 先用 30 秒认识项目

这是一个基于 Spring Boot 的本地缓存演示系统，核心不是数据库，而是你自己实现的缓存框架：

- 缓存核心：`LocalCache<K, V>`
- 淘汰策略：`LRU / LFU / FIFO`
- 过期机制：`TTL`
- 清理机制：定时任务 + 读写过程中的被动清理
- 展示方式：REST API + Thymeleaf 仪表盘

一句话理解：

> `web` 收请求，`service` 做流程编排，`core` 决定缓存行为。

---

## 1. 项目架构总览（你先记住这张图）

```text
src/main/java/com/playground/cache
├── LocalCacheDemoApplication.java   # 启动类 + 开启定时任务
├── core/                            # 缓存核心层（最重要）
│   ├── Cache.java                   # 缓存接口
│   ├── LocalCache.java              # 缓存实现（容量、TTL、统计、并发控制）
│   ├── CacheEntry*.java             # 缓存条目模型与只读视图
│   ├── CacheLookup*.java            # 查询结果状态（HIT/MISS/EXPIRED）
│   ├── CacheStats.java              # 统计快照
│   ├── EvictionPolicy.java          # 策略抽象
│   ├── Lru/Fifo/LfuEvictionPolicy   # 三种淘汰策略实现
│   └── AbstractLinkedPolicy.java    # LRU/FIFO 复用链表骨架
├── service/                         # 业务编排层（第二重要）
│   ├── CacheManagerService.java     # 查询流程中枢、预热、切策略、定时清理
│   ├── ProductRepository.java       # 模拟慢查询“数据库”
│   ├── ProductQueryResult.java      # 查询返回结构
│   └── QuerySource.java             # 来源标识（缓存/数据库/过期重载）
├── model/
│   └── Product.java                 # 商品模型
└── web/                             # 对外层
    ├── CacheRestController.java     # REST API
    └── DashboardController.java     # 页面路由与交互

src/main/resources
├── application.yml                  # 清理周期与端口等配置
└── templates/dashboard.html         # 可视化面板

src/test/java/com/playground/cache/core
└── LocalCacheTest.java              # 缓存行为测试（阅读起点）
```

---

## 2. 新手阅读顺序（严格按顺序，不容易迷路）

## 第 1 阶段：先看“项目承诺了什么行为”

1. `src/test/java/com/playground/cache/core/LocalCacheTest.java`

先读测试，因为测试是“需求说明书”。你先知道系统应该如何表现，再看实现会很快。

重点测试点：

- `shouldEvictLeastRecentlyUsedEntry`：LRU 是否正确淘汰
- `shouldEvictLeastFrequentlyUsedEntry`：LFU 是否正确淘汰
- `shouldEvictFirstInEntryUnderFifo`：FIFO 是否正确淘汰
- `shouldExpireEntryAfterTtl`：TTL 到期是否失效
- `shouldCleanExpiredEntriesInBackgroundStyleSweep`：批量清理是否有效
- `shouldSortHotKeysByAccessCount`：热点排行是否正确

---

## 第 2 阶段：看“请求是怎么走的”

2. `src/main/java/com/playground/cache/web/CacheRestController.java`
3. `src/main/java/com/playground/cache/web/DashboardController.java`

你此时只回答：

- 对外暴露了哪些操作（查、删、清空、切策略、看统计）？
- 请求最终都转给谁？（答案：`CacheManagerService`）

---

## 第 3 阶段：看“流程中枢”

4. `src/main/java/com/playground/cache/service/CacheManagerService.java`
5. `src/main/java/com/playground/cache/service/ProductRepository.java`
6. `src/main/java/com/playground/cache/service/QuerySource.java`

这一步你要理解项目主线：

- 查商品先查缓存：`cache.lookup(id)`
- 命中：直接返回
- 未命中/已过期：回源仓库查询，再写回缓存
- 返回时携带来源标签：缓存命中 / 数据库加载 / 过期重载

---

## 第 4 阶段：看缓存核心实现

7. `src/main/java/com/playground/cache/core/Cache.java`
8. `src/main/java/com/playground/cache/core/CacheEntry.java`
9. `src/main/java/com/playground/cache/core/CacheLookupResult.java`
10. `src/main/java/com/playground/cache/core/LocalCache.java`
11. `src/main/java/com/playground/cache/core/CacheStats.java`

这一步的主角是 `LocalCache.java`，建议看 3 遍：

- 第 1 遍：只看字段（数据结构）
- 第 2 遍：只看对外方法（put/get/remove/clear/snapshot）
- 第 3 遍：看内部私有方法（过期清理、淘汰细节）

---

## 第 5 阶段：看策略模式的落地

12. `src/main/java/com/playground/cache/core/EvictionPolicy.java`
13. `src/main/java/com/playground/cache/core/AbstractLinkedPolicy.java`
14. `src/main/java/com/playground/cache/core/LruEvictionPolicy.java`
15. `src/main/java/com/playground/cache/core/FifoEvictionPolicy.java`
16. `src/main/java/com/playground/cache/core/LfuEvictionPolicy.java`

顺序原因：先简单（LRU/FIFO），再复杂（LFU）。

---

## 3. 关键链路详细解释（面试可直接讲）

## 3.1 一次商品查询发生了什么

调用入口：`CacheManagerService#queryProductWithSource`。

流程：

1. 调 `cache.lookup(id)` 拿到三态结果：`HIT / MISS / EXPIRED`
2. 若 `HIT`：直接返回缓存值，来源标记 `CACHE_HIT`
3. 若 `MISS` 或 `EXPIRED`：查 `ProductRepository.findById(id)`（模拟慢查询）
4. 查到后 `cache.put(id, product, DEFAULT_TTL)` 写回缓存
5. 根据状态区分来源：
   - MISS -> `DATABASE_LOAD`
   - EXPIRED -> `RELOADED_AFTER_EXPIRE`

这段设计的亮点：

- 不只告诉你“有没有数据”，还告诉你“数据从哪来”，便于演示缓存价值。

---

## 3.2 `LocalCache` 是如何工作的

核心字段（理解缓存实现的钥匙）：

- `storage`：`ConcurrentHashMap`，存真实条目
- `evictionPolicy`：淘汰策略对象（可插拔）
- `capacity`：容量上限
- `lock`：`ReentrantLock`，保证复合操作原子性
- `hitCount/missCount/evictionCount/expiredCount`：统计指标

### 写入：`put`

逻辑分三段：

1. 先算过期时间 `expireAt`
2. 若 key 已存在：更新值与过期时间，并通知策略 `onAccess`
3. 若 key 不存在：
   - 先清理当前已过期条目
   - 若容量已满，触发 `evictOne`
   - 新建条目并 `evictionPolicy.onPut(entry)`

### 读取：`lookup`（比 `get` 更核心）

`lookup` 会返回状态，而不仅是值：

- 找不到：`MISS` + `missCount++`
- 找到但过期：删除 + `EXPIRED` + `missCount++` + `expiredCount++`
- 命中：记录访问次数与时间，策略 `onAccess`，`hitCount++`

`get` 只是对 `lookup` 的简单封装。

### 批量清理：`cleanUpExpiredEntries`

这个方法用于“后台清理”，本质是调用 `evictExpiredEntries(now)`。

---

## 3.3 为什么既有 `ConcurrentHashMap` 还要锁

`ConcurrentHashMap` 只能保证单次操作线程安全，不能保证“多步复合动作”原子性。

例如 `put` 涉及：

- 判断是否存在
- 可能清理过期
- 可能淘汰
- 插入新值
- 更新策略结构

这些步骤必须在一个临界区内保持一致，所以使用 `ReentrantLock`。

---

## 3.4 三种淘汰策略如何解耦

统一抽象：`EvictionPolicy` 提供 `onPut/onAccess/onRemove/evictKey/name`。

`LocalCache` 不关心“你怎么淘汰”，只在关键时机通知策略。

### LRU / FIFO

都继承 `AbstractLinkedPolicy`，底层用双向链表 + 索引表：

- `onPut`：节点追加到尾部
- `evictKey`：从头部弹出
- 差异只在 `onAccess`：
  - LRU：访问后移动到尾部
  - FIFO：访问不改变顺序

### LFU

`LfuEvictionPolicy` 使用三块状态：

- `frequencies`：key -> 访问频次
- `keysByFrequency`：频次 -> 该频次下的 key 集合（`LinkedHashSet` 保证同频次下先后顺序）
- `minFrequency`：当前最小频次

淘汰时直接从 `minFrequency` 对应集合取第一个 key，做到“最少使用优先，同频次按先入先出”。

---

## 3.5 过期机制是“读写时清理 + 定时清理”双保险

- 读路径：`lookup` 发现过期会即时剔除
- 写路径：`put` 前会先扫一轮过期
- 后台路径：`CacheManagerService#cleanExpiredEntries` 通过 `@Scheduled` 周期触发

配置位置：`src/main/resources/application.yml` 中 `cache.cleanup.fixed-delay-ms`。

---

## 4. 你应该重点读懂的文件（逐个说明）

## A. 启动与配置

- `src/main/java/com/playground/cache/LocalCacheDemoApplication.java`：`@EnableScheduling` 开启定时任务
- `src/main/resources/application.yml`：端口、清理周期、模板缓存配置
- `pom.xml`：Spring Boot Web + Thymeleaf + Validation + Test 依赖

## B. 展示层

- `web/CacheRestController.java`：API 风格接口，适合 Postman/curl 演示
- `web/DashboardController.java`：页面交互入口，提交表单后重定向回首页
- `templates/dashboard.html`：可视化展示缓存条目、热键、统计、策略切换

## C. 业务编排层

- `service/CacheManagerService.java`：
  - 启动预热 `1,2,3`
  - 查询回源并区分来源
  - 切换策略时“新建缓存实例”
  - 维护最近一次查询/清理元数据
- `service/ProductRepository.java`：内存 Map + 600ms 延时模拟数据库慢查询

## D. 核心层

- `core/LocalCache.java`：容量、TTL、统计、并发、策略协作全部在这里
- `core/EvictionPolicy.java` 及其实现：策略模式的关键

---

## 5. 为什么“切策略”是新建缓存实例

在 `CacheManagerService#switchPolicy` 里，切换策略会直接创建新的 `LocalCache`：

- 可以避免把“旧策略状态”迁移到“新策略结构”带来的复杂度
- 演示语义清晰：切换策略 = 用新的规则重新开始
- 随后做预热，确保页面有基础数据可观察

这是一个“演示项目里可维护性优先”的取舍。

---

## 6. 新手常见误区（提前避坑）

1. 以为 `get` 是核心读取方法
   - 实际核心是 `lookup`，因为它保留了状态语义

2. 以为 TTL 只靠定时任务
   - 实际是“即时 + 定时”双路径

3. 以为 LFU 很难维护一致性
   - 该实现用 `minFrequency` + 分桶集合，已把淘汰定位降到 O(1) 近似复杂度

4. 以为切策略要迁移旧数据
   - 本项目是教学演示，不追求热迁移

---

## 7. 上手演练脚本（建议你边操作边讲解）

运行：

```bash
mvn spring-boot:run
```

演示顺序：

1. 打开首页观察预热（默认有 1/2/3）
2. 连续查询 `1`，讲“缓存命中”
3. 查询 `8`，讲“数据库回源后写回缓存”
4. 查询多个新 key 触发淘汰，比较 LRU/LFU/FIFO 差异
5. 等待约 45 秒后再次查询，讲“过期重载”
6. 查看统计面板，解释命中率与过期计数变化

---

## 8. 学完后你必须能回答的 12 个问题

1. `CacheManagerService` 为什么是流程中枢？
2. `lookup` 与 `get` 的职责差异是什么？
3. 为什么要有 `CacheLookupStatus.EXPIRED`？
4. `LocalCache` 在写入前为什么先清理过期？
5. `evictionPolicy` 什么时候接收 `onPut/onAccess/onRemove`？
6. LRU 与 FIFO 共享了什么实现，差异在哪？
7. LFU 如何处理“同频次”淘汰顺序？
8. 为什么 `ConcurrentHashMap` 之外还要 `ReentrantLock`？
9. 命中率如何计算？
10. 过期计数和淘汰计数有什么区别？
11. 切换策略为何重建缓存实例？
12. 这个项目如何体现“策略模式 + 模板化复用”的设计思想？

如果你能完整回答这 12 题，说明你已经不是“看过”，而是“吃透”。

---

## 9. 一页速记（复试前 3 分钟）

- 主线：Controller -> `CacheManagerService` -> `LocalCache` / `ProductRepository`
- 核心对象：`LocalCache` + `EvictionPolicy`
- 三态查询：`HIT / MISS / EXPIRED`
- 三策略：
  - LRU：最近最少使用
  - FIFO：先进先出
  - LFU：最少访问频次
- TTL 清理：读取触发 + 写入触发 + 定时任务
- 并发安全：`ConcurrentHashMap` + `ReentrantLock`
- 统计指标：命中、未命中、淘汰、过期、命中率

---

## 10. 下一步精读建议

如果你现在只想抓住 80% 价值，按下面三个文件精读：

1. `src/test/java/com/playground/cache/core/LocalCacheTest.java`
2. `src/main/java/com/playground/cache/service/CacheManagerService.java`
3. `src/main/java/com/playground/cache/core/LocalCache.java`

这三份读透，你就能完整讲清项目。
