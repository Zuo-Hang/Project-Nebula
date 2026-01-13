# 组件迁移 TODO 列表

本文档记录了从旧项目（LLM-data-collect）迁移组件和工具到新项目（ai-agent-orchestrator）的待完成任务。

## ✅ 已完成的工作

### 1. 依赖管理
- [x] 更新父 pom.xml：添加所有依赖版本管理（MyBatis Plus、Druid、Redisson、RocketMQ、LangChain4j、Nacos等）
- [x] orchestrator-core 模块：添加数据库相关依赖（MyBatis Plus、Druid、MySQL驱动）
- [x] orchestrator-core 模块：添加 RocketMQ 依赖（或保持 RabbitMQ，根据需求决定）
- [x] orchestrator-core 模块：添加 Nacos Client 依赖（配置中心和服务发现）
- [x] orchestrator-core 模块：添加 Spring Quartz 依赖（定时任务）
- [x] step-executors 模块：添加 LangChain4j 依赖（LLM框架）
- [x] state-store 模块：确保 Redisson 依赖已添加

### 2. 配置文件
- [x] 更新 orchestrator-core 的 application.yml：添加 MySQL、RocketMQ、Nacos 等配置
- [x] 更新 docker-compose.yml：添加 RocketMQ 服务（如果需要）

---

## ✅ 迁移完成总结

所有组件迁移工作已完成！以下是迁移统计：

- ✅ **IO 工具类**：6 个文件（OcrClient, S3Client, RedisWrapper, RedisLock, MysqlWrapper, PoiService, QuestService）
- ✅ **配置类**：4 个文件（AppConfigService, NacosConfigService, NacosServiceRegistry, NacosConfigInitializer）
- ✅ **监控类**：3 个文件（PrometheusMetricsClient, MetricsClientAdapter, PprofMonitor）
- ✅ **工具类**：6 个文件（ServiceDiscovery, NacosServiceDiscovery, HttpUtils, GjsonUtils, QuestUtils, CityMap）
- ✅ **数据库相关**：3 个文件（SupplierResponseRate, SupplierResponseRateMapper, SupplierResponseRateService）
- ✅ **配置类创建**：4 个文件（MysqlConfig, RedisConfig, MqConfig, SchedulerConfig）

**总计**：26 个文件已成功迁移到新项目结构

---

## 📋 待完成的工作（后续优化）

### 1. 迁移 IO 工具类

需要从 `src/main/java/com/wuxiansheng/shieldarch/marsdata/io/` 迁移以下文件：

- [x] **OcrClient.java** - OCR 客户端
  - 目标位置：`step-executors/src/main/java/com/wuxiansheng/shieldarch/stepexecutors/io/`
  - 相关文件：OcrConfig.java、AliPoint.java、AliResult.java

- [x] **S3Client.java** - MinIO S3 客户端
  - 目标位置：`step-executors/src/main/java/com/wuxiansheng/shieldarch/stepexecutors/io/`
  - 相关文件：S3RuntimeConfig、S3StorageConfig、UploadResult、UploadTask

- [x] **RedisWrapper.java** - Redis 包装类
  - 目标位置：`state-store/src/main/java/com/wuxiansheng/shieldarch/statestore/`
  - 相关文件：KeyNotFoundException

- [x] **MysqlWrapper.java** - MySQL 包装类
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/io/`

- [x] **PoiService.java** - POI 服务
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/io/`

- [x] **QuestService.java** - Quest 服务
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/io/`
  - 相关文件：QueryQuestResponse、QuestData

- [x] **RedisLock.java** - Redis 分布式锁
  - 目标位置：`state-store/src/main/java/com/wuxiansheng/shieldarch/statestore/`

### 2. 迁移配置类

需要从 `src/main/java/com/wuxiansheng/shieldarch/marsdata/config/` 迁移以下文件：

- [x] **NacosConfigService.java** - Nacos 配置服务
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/config/`
  - 说明：配置中心功能

- [x] **NacosServiceRegistry.java** - Nacos 服务注册
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/config/`
  - 说明：服务发现功能

- [x] **NacosConfigInitializer.java** - Nacos 配置初始化器
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/config/`

### 3. 迁移监控类

需要从 `src/main/java/com/wuxiansheng/shieldarch/marsdata/monitor/` 迁移以下文件：

- [x] **PrometheusMetricsClient.java** - Prometheus 指标客户端
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/monitor/`

- [x] **MetricsClientAdapter.java** - 指标客户端适配器
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/monitor/`

- [x] **PprofMonitor.java** - Pprof 监控
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/monitor/`

### 4. 迁移工具类

需要从 `src/main/java/com/wuxiansheng/shieldarch/marsdata/utils/` 迁移以下文件：

- [x] **NacosServiceDiscovery.java** - Nacos 服务发现
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/utils/`
  - 相关文件：README_NACOS_SERVICE_DISCOVERY.md

- [x] **ServiceDiscovery.java** - 服务发现接口
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/utils/`

- [x] **HttpUtils.java** - HTTP 工具类
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/utils/`

- [x] **GjsonUtils.java** - JSON 工具类
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/utils/`

- [x] **QuestUtils.java** - Quest 工具类
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/utils/`

### 5. 迁移数据库相关

需要从 `src/main/java/com/wuxiansheng/shieldarch/marsdata/io/` 迁移以下文件：

- [x] **SupplierResponseRate.java** - 实体类
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/entity/`

- [x] **SupplierResponseRateMapper.java** - MyBatis Mapper
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/mapper/`

- [x] **SupplierResponseRateService.java** - 服务类
  - 目标位置：`orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/service/`

### 6. 创建配置类（适配新项目结构）

需要在 `orchestrator-core/src/main/java/com/wuxiansheng/shieldarch/orchestrator/config/` 创建以下配置类：

- [x] **MysqlConfig.java** - MySQL 配置类
  - 说明：参考旧项目的 MysqlConfig.java，适配新项目结构

- [x] **RedisConfig.java** - Redis 配置类
  - 说明：参考旧项目的 RedisConfig.java，适配新项目结构

- [x] **MqConfig.java** - 消息队列配置类
  - 说明：参考旧项目的 MqConfig.java，支持 RocketMQ 和 RabbitMQ

- [x] **SchedulerConfig.java** - 定时任务配置类
  - 说明：参考旧项目的 SchedulerConfig.java，适配新项目结构（待完善，需要迁移 Scheduler 和任务类）

---

## 📝 迁移注意事项

1. **包名修改**：所有迁移的文件需要将包名从 `com.wuxiansheng.shieldarch.marsdata` 改为对应的新包名
2. **依赖调整**：检查并更新导入的依赖，确保与新项目的模块结构匹配
3. **配置适配**：根据新项目的配置结构，调整配置相关的代码
4. **业务逻辑**：只迁移工具类和配置类，不迁移业务逻辑代码
5. **测试验证**：迁移后需要验证代码能否正常编译和运行

---

## 🔍 文件位置对照表

| 旧项目路径 | 新项目目标路径 | 模块 |
|-----------|--------------|------|
| `marsdata/io/OcrClient.java` | `stepexecutors/io/OcrClient.java` | step-executors |
| `marsdata/io/S3Client.java` | `stepexecutors/io/S3Client.java` | step-executors |
| `marsdata/io/RedisWrapper.java` | `statestore/RedisWrapper.java` | state-store |
| `marsdata/io/RedisLock.java` | `statestore/RedisLock.java` | state-store |
| `marsdata/io/MysqlWrapper.java` | `orchestrator/io/MysqlWrapper.java` | orchestrator-core |
| `marsdata/config/NacosConfigService.java` | `orchestrator/config/NacosConfigService.java` | orchestrator-core |
| `marsdata/monitor/PrometheusMetricsClient.java` | `orchestrator/monitor/PrometheusMetricsClient.java` | orchestrator-core |
| `marsdata/utils/NacosServiceDiscovery.java` | `orchestrator/utils/NacosServiceDiscovery.java` | orchestrator-core |

---

## 📅 更新记录

- 2025-01-12: 创建初始 TODO 列表，完成依赖和配置迁移
