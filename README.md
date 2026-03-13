# Local Cache Demo

一个适合复试展示的 Java 本地缓存项目，核心目标是实现一个支持 `LRU / LFU / FIFO` 三种淘汰策略、`TTL` 过期机制、容量限制和基础监控统计的轻量缓存框架，并通过 Spring Boot 提供接口与可视化面板。

## 功能概览

- 泛型缓存接口：`put / get / remove / clear / size`
- 三种淘汰策略：`LRU`、`LFU`、`FIFO`
- `TTL` 过期控制
- 定时后台清理过期数据
- 热点 Key Top N 排行
- 查询来源标识：来自缓存、来自数据库、过期后重载
- 启动预热：默认预热商品 `1、2、3`
- 线程安全访问：`ConcurrentHashMap + ReentrantLock`
- 统计信息：命中次数、未命中次数、命中率、淘汰次数、过期次数
- Spring Boot 演示接口
- Thymeleaf 监控页面

## 项目结构

```text
src/main/java/com/playground/cache
├── core        # 缓存核心接口、条目模型、统计对象、淘汰策略实现
├── model       # 演示用数据模型
├── service     # 模拟数据库查询与缓存管理服务
└── web         # REST 接口与监控页面控制器
```

## 运行方式

需要本机安装 JDK 17 和 Maven。

```bash
mvn spring-boot:run
```

启动后访问：

- 页面监控台：`http://localhost:8080/`
- 查询产品：`http://localhost:8080/api/cache/products/1`
- 查看缓存条目：`http://localhost:8080/api/cache/entries`
- 查看统计信息：`http://localhost:8080/api/cache/stats`

## 演示建议

1. 应用启动后先观察首页，可以看到商品 `1、2、3` 已经完成缓存预热。
2. 连续访问 `products/1`，观察“最近查询来源”显示为“来自缓存”。
3. 再访问未预热的数据，例如 `products/8`，观察来源切换为“来自数据库”。
4. 依次访问 `1` 到 `9`，缓存容量为 `8`，访问第 `9` 个不同键时即可触发淘汰。
5. 在首页切换 `LRU / LFU / FIFO`，解释三种策略下被淘汰的 key 为什么不同。
6. 等待 `45` 秒后再次访问，展示 `TTL` 过期、自动清理与“过期后重载”状态。

## 测试

```bash
mvn test
```

已包含缓存核心测试：

- `LRU` 淘汰是否正确
- `LFU` 淘汰是否正确
- `FIFO` 淘汰是否正确
- `TTL` 过期是否生效
