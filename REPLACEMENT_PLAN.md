# 滴滴内部组件替换计划

## 📋 TODO 列表总览

### 🔴 高优先级：Apollo → Nacos 配置中心

#### 阶段 1：基础设施准备
- [ ] **apollo-1**: 创建 NacosConfigService 接口，定义配置管理方法
- [ ] **apollo-2**: 实现 NacosConfigService，使用 Nacos Config API
- [ ] **apollo-3**: 添加 Nacos Config 依赖（检查 nacos-client 是否已包含）
- [ ] **apollo-4**: 在 application.yml 中添加 Nacos 配置中心配置

#### 阶段 2：兼容和迁移
- [ ] **apollo-5**: 创建 ApolloConfigService 兼容层（@Deprecated）
- [ ] **apollo-6**: 更新所有使用 ApolloConfigService 的文件（19个文件）
- [ ] **apollo-7**: 在 Nacos 控制台创建配置命名空间
- [ ] **apollo-8**: 迁移配置数据从 Apollo 到 Nacos

#### 阶段 3：测试和验证
- [ ] **apollo-9**: 测试配置读取、热更新等功能

---

### 🟡 中优先级：Dufe 特征服务

#### 阶段 1：需求分析
- [ ] **dufe-1**: 分析 Dufe 特征服务的实际需求
  - 查看 `PriceFittingTask` 中的使用场景
  - 了解特征数据的格式和来源
  - 确定特征服务的接口规范

#### 阶段 2：方案设计
- [ ] **dufe-2**: 设计 Dufe 替换方案
  - 方案 A: 自定义 HTTP 服务
  - 方案 B: Feature Store（如 Feast）
  - 方案 C: Redis/数据库存储

#### 阶段 3：实现和测试
- [ ] **dufe-3**: 实现 DufeClient 的真实逻辑
- [ ] **dufe-4**: 测试 PriceFittingTask 中的特征获取功能

---

### 🟢 低优先级：清理和替换

#### DirPC 清理
- [ ] **dirpc-1**: 确认 DirPC 是否在项目中被实际使用
- [ ] **dirpc-2**: 如果未使用，删除 DirPCInitializer.java
- [ ] **dirpc-3**: 如果使用，设计替换方案并实现

#### Odin 监控替换
- [ ] **odin-1**: 评估 OdinMonitor 的实际使用情况
- [ ] **odin-2**: 使用 Spring Boot Actuator 或 StatsD 替换
- [ ] **odin-3**: 配置 Prometheus 监控（可选）

#### DiSF 清理
- [ ] **disf-cleanup**: 删除或标记废弃 DiSFInitializer.java

---

### ✅ 最终检查
- [ ] **final-check**: 确保所有滴滴内部组件都已替换或删除

---

## 🎯 执行顺序建议

### 第一周：Apollo 替换（高优先级）

**Day 1-2: 基础设施**
1. 创建 NacosConfigService 接口和实现
2. 添加配置和依赖
3. 测试基本的配置读取功能

**Day 3-4: 兼容层**
1. 创建 ApolloConfigService 兼容层
2. 逐步迁移调用点
3. 在 Nacos 中创建配置命名空间

**Day 5: 迁移和测试**
1. 迁移配置数据
2. 完整测试
3. 验证热更新功能

### 第二周：清理和优化

**Day 1: 清理未使用的组件**
1. 删除 DiSFInitializer
2. 确认并删除 DirPC（如果未使用）

**Day 2-3: Dufe 替换**
1. 分析需求
2. 设计和实现替换方案
3. 测试

**Day 4-5: Odin 替换**
1. 评估使用情况
2. 使用 Actuator 替换
3. 配置监控

---

## 📊 进度跟踪

### Apollo 替换进度
- [ ] 基础设施准备（0/4）
- [ ] 兼容和迁移（0/4）
- [ ] 测试验证（0/1）

### Dufe 替换进度
- [ ] 需求分析（0/1）
- [ ] 方案设计（0/1）
- [ ] 实现测试（0/2）

### 清理进度
- [ ] DirPC（0/3）
- [ ] Odin（0/3）
- [ ] DiSF（0/1）

---

## 🔧 技术细节

### Apollo → Nacos 配置映射

| Apollo 概念 | Nacos 概念 | 说明 |
|------------|-----------|------|
| Namespace | Namespace | 配置命名空间 |
| App ID | Group | 配置分组 |
| Key-Value | Data ID | 配置项 |
| 配置热更新 | 监听配置变化 | Nacos 支持配置监听 |

### 配置命名空间映射

```
Apollo Namespace          →  Nacos Namespace
─────────────────────────────────────────────
OCR_LLM_CONF             →  OCR_LLM_CONF
PRICE_FITTING_CONF       →  PRICE_FITTING_CONF
QUALITY_MONITOR_CONF     →  QUALITY_MONITOR_CONF
OCR_BUSINESS_CONF        →  OCR_BUSINESS_CONF
```

---

## 📝 注意事项

1. **向后兼容**: 在替换过程中保持向后兼容，避免影响现有功能
2. **配置迁移**: 确保配置数据完整迁移，避免配置丢失
3. **测试充分**: 每个阶段都要充分测试
4. **文档更新**: 及时更新相关文档
5. **回滚方案**: 准备回滚方案，以防替换失败

---

## 🚀 开始替换

建议从 **Apollo → Nacos** 开始，因为：
1. 影响范围最大（19个文件）
2. 可以复用现有的 Nacos 基础设施
3. 配置管理是核心功能

准备好后，可以开始执行第一个任务：**apollo-1**

