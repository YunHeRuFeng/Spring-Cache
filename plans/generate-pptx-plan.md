# 直接生成 .pptx 文件方案

## 技术选型

- **工具**：Python `python-pptx` 库
- **输出**：`docs/Spring-Cache-Demo.pptx`（标准 Office Open XML 格式）
- **兼容性**：PowerPoint 2007+ / WPS Office / LibreOffice Impress 均可打开
- **字体**：微软雅黑（中文） + Arial（英文），Windows 系统自带

## 幻灯片结构（共 7 页）

### Slide 1：封面页
- 项目名：Local Cache Framework
- 副标题：基于 Spring Boot 的本地缓存框架
- 技术栈标签：Java 17 / Spring Boot 3.3 / Thymeleaf / ConcurrentHashMap / ReentrantLock
- 设计模式标签：Strategy / Template Method / Facade

### Slide 2：系统架构全景图
- 来源：`01-architecture.html`
- 五层分层卡片竖向排列：
  1. CLIENT 层：浏览器 Dashboard + REST API Client
  2. WEB 层：DashboardController + CacheRestController
  3. SERVICE 层：CacheManagerService + ProductRepository + ProductQueryResult
  4. CACHE CORE 层：Cache 接口 + LocalCache + EvictionPolicy + AbstractLinkedPolicy + LRU/LFU/FIFO
  5. DATA STRUCTURES 层：ConcurrentHashMap + Doubly Linked List + Frequency Buckets
- 层间用箭头连接

### Slide 3：请求生命周期流程图
- 来源：`02-request-lifecycle.html`
- 核心流程：
  1. HTTP GET Request → CacheManagerService.lookup
  2. 判断：存在且未过期？
  3. HIT 分支 → recordAccess → 返回结果
  4. MISS 分支 → ProductRepository.findById → cache.put → 容量检查 → 可能淘汰
  5. EXPIRED 分支 → 移除旧条目 → 回源 DB
  6. 返回 ProductQueryResult
- 底部四个统计指标：HIT / MISS / EXPIRED / EVICTION

### Slide 4：LRU 策略原理
- 来源：`03-lru-policy.html`
- 上半部分：数据结构说明 - HashMap + 双向链表
- 中间：可视化链表 head → K1 → K2 → K3 → K4 → tail
- 访问 K2 后：head → K1 → K3 → K4 → K2 → tail
- 底部三个原则卡片：写入=尾插 / 访问=移尾 / 淘汰=弹头

### Slide 5：LFU 策略原理
- 来源：`04-lfu-policy.html`
- 左侧：frequencies Map 展示 K1→1, K2→2, K3→3
- 右侧：keysByFrequency 按桶分组 freq=1/2/3
- 淘汰流程：定位 minFrequency → 读取桶首元素 → evict → 更新 min
- 四步流程：新元素写入 → 访问升级 → 最小频次维护 → 淘汰 victim

### Slide 6：FIFO 策略原理
- 来源：`05-fifo-policy.html`
- 链表可视化：head → K1 → K2 → K3 → K4 → tail（插入顺序）
- 核心差异高亮：访问 K2/K4 后顺序不变（onAccess = no-op）
- 淘汰后：K1 被移除，K5 尾插 → head → K2 → K3 → K4 → K5 → tail
- 三列对比：写入规则 / 访问规则 / 淘汰规则

### Slide 7：三种策略对比总结
- 对比表格（4列 x 5行）：
  - 维度：数据结构 / 写入行为 / 访问行为 / 淘汰对象 / 时间复杂度
  - LRU / LFU / FIFO 三列并排
- 适用场景说明
- 一句话总结

## 视觉风格

```
背景色：深蓝黑 RGB(10, 14, 26) 即 #0A0E1A
主文字色：RGB(224, 230, 240) 即 #E0E6F0
辅助文字色：RGB(148, 163, 184) 即 #94A3B8
```

**渐变色系（用于形状填充）：**
- 蓝色系：#38BDF8 → #0EA5E9（Client 层）
- 靛蓝系：#818CF8 → #6366F1（Web 层）
- 紫色系：#A78BFA → #8B5CF6（Service 层）
- 粉色系：#F472B6 → #EC4899（Core 层）
- 绿色系：#34D399 → #10B981（Data 层）
- 红色系：#F87171 → #EF4444（淘汰/警告）
- 橙色系：#FB923C → #EA580C（MISS）
- 黄色系：#FBBF24 → #D97706（EXPIRED）

## 实施步骤

1. 安装依赖：`pip install python-pptx`
2. 编写 Python 脚本 `scripts/generate_pptx.py`
3. 运行脚本生成 `docs/Spring-Cache-Demo.pptx`
4. 验证文件可正常打开

## 注意事项

- 幻灯片尺寸：宽屏 16:9（13.33 x 7.5 英寸）
- 所有文字使用微软雅黑 + Arial，不依赖特殊字体
- 避免使用 python-pptx 不支持的高级特效（如 blur/glow）
- 形状使用基本矩形、圆角矩形、箭头、菱形
- 渐变填充使用 python-pptx 支持的 GradientFill
