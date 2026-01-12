# Prometheus 迁移 TODO List

## 📋 迁移总览

- **总文件数**: 21 个文件
- **总调用数**: 约 52 处
- **预计工作量**: 4-6 小时
- **迁移策略**: 渐进式，分 5 个阶段

## 🎯 阶段1: 核心业务迁移（高优先级）

### 目标
迁移核心业务指标，确保 LLM 和 MQ 等关键功能监控正常。

### 任务清单

- [ ] **prometheus-1**: 迁移 LLMClient
  - 文件: `src/main/java/com/wuxiansheng/shieldarch/marsdata/llm/LLMClient.java`
  - 操作: 替换 `StatsdClient` 为 `MetricsClientAdapter`
  - 指标: `llm_req` (RPC 指标)
  - 验证: 在 Prometheus 中查询 `llm_req_total`, `llm_req_duration_ms`

- [ ] **prometheus-2**: 迁移 ReasonService
  - 文件: `src/main/java/com/wuxiansheng/shieldarch/marsdata/llm/ReasonService.java`
  - 操作: 替换 `StatsdClient` 为 `MetricsClientAdapter`
  - 指标: `llm_cache_hit`, `llm_cache_miss`, `llm_cache_error`
  - 验证: 查询缓存相关指标

- [ ] **prometheus-3**: 迁移 MQ Producer
  - 文件: `src/main/java/com/wuxiansheng/shieldarch/marsdata/mq/Producer.java`
  - 操作: 替换 `StatsDUtils` 为 `MetricsClientAdapter`
  - 指标: `ddmq_producer`
  - 验证: 查询 MQ 发送指标

- [ ] **prometheus-4**: 迁移 MQ Consumer
  - 文件: `src/main/java/com/wuxiansheng/shieldarch/marsdata/mq/Consumer.java`
  - 操作: 替换 `StatsdClient` 为 `MetricsClientAdapter`
  - 指标: `ddmq_req`, `ddmq_req_retry`
  - 验证: 查询 MQ 消费指标

- [ ] **prometheus-5**: 迁移 MessageHandler
  - 文件: `src/main/java/com/wuxiansheng/shieldarch/marsdata/llm/MessageHandler.java`
  - 操作: 替换 `StatsdClient` 为 `MetricsClientAdapter`
  - 指标: `poster_counter`, `sink_counter`, `sink_fail`, `HandlerBusiness`
  - 验证: 查询业务处理指标

- [ ] **prometheus-6**: 阶段1验证
  - 在 Prometheus 中验证所有指标
  - 对比 StatsD 数据一致性
  - 检查指标标签是否正确

**预计时间**: 1-2 小时

---

## 🔧 阶段2: 基础设施迁移（中优先级）

### 目标
迁移基础设施监控，确保调度器、Redis、外部服务等监控正常。

### 任务清单

- [ ] **prometheus-7**: 迁移 Scheduler
  - 文件: `src/main/java/com/wuxiansheng/shieldarch/marsdata/scheduler/Scheduler.java`
  - 指标: `scheduler_task`, `scheduler_task_duration`

- [ ] **prometheus-8**: 迁移 PriceFittingTask
  - 文件: `src/main/java/com/wuxiansheng/shieldarch/marsdata/scheduler/tasks/PriceFittingTask.java`
  - 指标: `price_fitting_missing_response_rate`

- [ ] **prometheus-9**: 迁移 IntegrityCheckTask
  - 文件: `src/main/java/com/wuxiansheng/shieldarch/marsdata/scheduler/tasks/IntegrityCheckTask.java`
  - 指标: `integrity_actual_count`, `integrity_missing_count`

- [ ] **prometheus-10**: 迁移 RedisWrapper
  - 文件: `src/main/java/com/wuxiansheng/shieldarch/marsdata/io/RedisWrapper.java`
  - 指标: `redis_get_req`, `redis_setex_req`

- [ ] **prometheus-11**: 迁移 PoiService
  - 文件: `src/main/java/com/wuxiansheng/shieldarch/marsdata/io/PoiService.java`
  - 指标: `mapapi_req`

- [ ] **prometheus-12**: 迁移 QuestService
  - 文件: `src/main/java/com/wuxiansheng/shieldarch/marsdata/io/QuestService.java`
  - 指标: `quest_req`

- [ ] **prometheus-13**: 阶段2验证
  - 验证所有基础设施指标
  - 确保监控正常

**预计时间**: 1 小时

---

## 📊 阶段3: 业务监控迁移（低优先级）

### 目标
迁移业务层监控指标，完善业务监控体系。

### 任务清单

- [ ] **prometheus-14**: 迁移 Business Posters
  - 文件:
    - `business/gdbubble/poster/GDFilterSupplier.java`
    - `business/gdspecialprice/poster/GDSpecialPriceFilterSupplier.java`
    - `business/xlbubble/poster/XLFilterSupplier.java`
  - 指标: `filtered_partner`

- [ ] **prometheus-15**: 迁移 Business Sinkers
  - 文件:
    - `business/bsaas/sinker/MonitorSinker.java` (7 处调用)
    - `business/gdbubble/sinker/GDSinkerMonitor.java` (3 处)
    - `business/xlbubble/sinker/XLSinkerMonitor.java` (3 处)
    - `business/gdspecialprice/sinker/GDSpecialPriceSinkerMonitor.java` (3 处)
  - 指标: 各种业务监控指标

- [ ] **prometheus-16**: 迁移其他 Sinkers
  - 文件:
    - `llm/sinker/LatencySinker.java`
    - `llm/sinker/HiveSinker.java`

- [ ] **prometheus-17**: 阶段3验证
  - 验证所有业务监控指标

**预计时间**: 30 分钟

---

## 📈 阶段4: Grafana 仪表盘和告警（完善）

### 目标
创建可视化仪表盘和告警规则，提升监控体验。

### 任务清单

- [ ] **prometheus-18**: 创建 LLM 监控仪表盘
  - QPS（每秒请求数）
  - 成功率（按业务分组）
  - 平均耗时（P50/P95/P99）
  - 错误率趋势

- [ ] **prometheus-19**: 创建缓存监控仪表盘
  - 命中率
  - 未命中率
  - 错误率
  - 缓存大小

- [ ] **prometheus-20**: 创建 MQ 监控仪表盘
  - 发送速率
  - 消费速率
  - 消费延迟
  - 重试次数

- [ ] **prometheus-21**: 创建业务监控仪表盘
  - 各业务处理量
  - 成功率
  - 平均耗时
  - 错误分布

- [ ] **prometheus-22**: 创建基础设施监控仪表盘
  - Redis 操作指标
  - 外部服务调用指标
  - 定时任务执行情况

- [ ] **prometheus-23**: 配置告警规则
  - LLM 请求失败率 > 5%
  - 缓存命中率 < 80%
  - MQ 消费延迟 > 10s
  - 业务处理错误率 > 3%

**预计时间**: 1-2 小时

---

## ✅ 阶段5: 最终验证和切换（收尾）

### 目标
全面验证，切换配置，完成迁移。

### 任务清单

- [ ] **prometheus-24**: 全面验证
  - 对比所有 StatsD 和 Prometheus 指标值
  - 确保指标一致性
  - 检查标签是否正确

- [ ] **prometheus-25**: 性能测试
  - 验证迁移后性能无影响
  - 检查内存和 CPU 使用

- [ ] **prometheus-26**: 切换配置
  - 将 `monitoring.type` 从 `both` 改为 `prometheus`
  - 验证仅 Prometheus 工作正常

- [ ] **prometheus-27**: 清理代码（可选）
  - 移除 StatsD 依赖（建议保留一段时间）
  - 清理未使用的 StatsD 配置

**预计时间**: 1 小时

---

## 🚀 快速迁移模板

### 代码替换模板

```java
// 旧代码
@Autowired
private StatsdClient statsdClient;

// 新代码
@Autowired
private MetricsClientAdapter metricsClient;

// 方法调用保持不变
metricsClient.recordRpcMetric(...);
```

### 验证步骤

1. **启动应用**
   ```bash
   mvn spring-boot:run
   ```

2. **检查指标端点**
   ```bash
   curl http://localhost:8080/actuator/prometheus | grep llm_req
   ```

3. **在 Prometheus 中查询**
   - 访问 http://localhost:9090
   - 查询: `llm_req_total`

4. **对比 StatsD 数据**
   - 确保指标值一致

---

## 📝 迁移记录

### 已完成
- [x] 基础设施搭建（Prometheus + Grafana）
- [x] 代码框架（PrometheusMetricsClient + MetricsClientAdapter）
- [x] 配置文件更新

### 进行中
- [ ] 阶段1: 核心业务迁移

### 待开始
- [ ] 阶段2: 基础设施迁移
- [ ] 阶段3: 业务监控迁移
- [ ] 阶段4: Grafana 仪表盘
- [ ] 阶段5: 最终验证

---

## 💡 提示

1. **使用 MetricsClientAdapter** 可以同时支持 StatsD 和 Prometheus，方便验证
2. **配置 `monitoring.type: both`** 可以双写，确保数据一致性
3. **逐个模块迁移**，每迁移一个模块就验证一次
4. **保留 StatsD** 一段时间，作为备份和对比

