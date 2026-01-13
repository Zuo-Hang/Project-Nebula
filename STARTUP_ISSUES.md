# 服务启动问题检查报告

**生成时间**: 2026-01-13  
**检查范围**: 编译错误、循环依赖、配置问题、组件扫描

---

## ✅ 已修复的问题

### 1. Maven循环依赖 ✅
**问题**: Maven编译失败，存在循环依赖
```
orchestrator-core -> step-executors -> state-store -> orchestrator-core
governance-core -> step-executors -> orchestrator-core
```

**修复方案**:
- ✅ 将 `TaskStateMachine` 移到 `orchestrator-api`
- ✅ 将 `TaskStateStore` 接口移到 `orchestrator-api`
- ✅ 将 `LLMServiceClient` 接口移到 `orchestrator-api`
- ✅ 将 `MetricsClient` 接口移到 `orchestrator-api`
- ✅ 移除了 `step-executors` 对 `orchestrator-core` 的依赖
- ✅ 移除了 `governance-core` 对 `step-executors` 的依赖
- ✅ 移除了 `state-store` 对 `orchestrator-core` 的依赖

**当前依赖关系**:
```
orchestrator-api (无依赖)
  ├── state-store (只依赖 orchestrator-api)
  ├── step-executors (依赖 orchestrator-api, state-store)
  ├── governance-core (依赖 orchestrator-api, state-store)
  └── orchestrator-core (依赖 orchestrator-api, step-executors, governance-core, state-store)
```

### 2. 包路径不匹配 ✅
**问题**: orchestrator-api中的文件路径与包声明不匹配
- `StepExecutor.java` 在 `orchestrator/orchestrator/` 但包名是 `orchestrator.orchestrator.step`

**修复**: ✅ 已将文件移动到正确的目录结构 `orchestrator/orchestrator/step/`

### 3. 缺失的类型定义 ✅
**问题**: `FrameExtractOptions` 类型未定义

**修复**: ✅ 使用 `VideoExtractor.FrameExtractOptions` 正确引用

### 4. Spring Boot组件扫描 ✅
**问题**: `@SpringBootApplication` 默认只扫描当前包，无法扫描其他模块的组件

**修复**: ✅ 添加了 `@ComponentScan` 注解，明确指定扫描包：
```java
@ComponentScan(basePackages = {
    "com.wuxiansheng.shieldarch.orchestrator",
    "com.wuxiansheng.shieldarch.stepexecutors",
    "com.wuxiansheng.shieldarch.governance",
    "com.wuxiansheng.shieldarch.statestore"
})
```

### 5. 未使用的导入 ✅
**修复**: ✅ 移除了未使用的导入

---

## ✅ 已修复的导入问题

### 6. 导入路径修复 ✅
**问题**: orchestrator-core无法找到TaskStateMachine、TaskStateStore、MetricsClient

**修复**: ✅ 已添加正确的导入语句

---

## ⚠️ 需要用户处理的问题

### 1. Java版本不匹配 ⚠️ **关键问题**
**问题**: Maven使用Java 25，但项目配置为Java 21
```
Maven Java version: 25
Project Java version: 21
```

**错误信息**:
```
Fatal error compiling: java.lang.ExceptionInInitializerError: 
com.sun.tools.javac.code.TypeTag :: UNKNOWN
```

**当前状态**:
- Maven检测到的Java版本: **Java 25**
- 项目配置的Java版本: **Java 21**
- 编译器插件版本: 3.13.0

**错误信息**:
```
Fatal error compiling: java.lang.ExceptionInInitializerError: 
com.sun.tools.javac.code.TypeTag :: UNKNOWN
```

**解决方案**:
1. **推荐方案**: 使用Java 21运行Maven
   ```bash
   # 查找Java 21安装路径
   /usr/libexec/java_home -V
   
   # 设置JAVA_HOME为Java 21
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   
   # 验证
   java -version  # 应该显示Java 21
   
   # 重新编译
   mvn clean compile
   ```

2. **临时方案**: 如果只有Java 25，可以尝试更新编译器插件版本
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-compiler-plugin</artifactId>
       <version>3.13.0</version>
       <configuration>
           <release>25</release>  <!-- 改为25 -->
       </configuration>
   </plugin>
   ```
   **注意**: 不推荐，可能有兼容性问题

### 2. 占位实现 ⚠️
**问题**: `LangChain4jLLMServiceClient.createChatModel()` 返回占位实现

**影响**: LLM调用会抛出 `UnsupportedOperationException`

**位置**: 
```java
step-executors/src/main/java/com/wuxiansheng/shieldarch/stepexecutors/executors/LangChain4jLLMServiceClient.java
```

**需要实现**: 真正的 `ChatLanguageModel`，参考旧项目的 `DiSFChatModelNative`

---

## 📋 启动前检查清单

### 环境要求
- ✅ Java 21+ (当前Maven使用Java 25，需要配置为Java 21)
- ✅ Maven 3.6+
- ✅ Docker & Docker Compose（用于启动依赖服务）

### 必须配置的环境变量/配置
以下配置都有默认值，但建议根据实际环境配置：

1. **Redis** (默认: localhost:6379)
   - `REDIS_ADDRESS`: Redis地址
   - `REDIS_PASSWORD`: Redis密码（可选）

2. **MySQL** (默认: localhost:3306/ai_orchestrator)
   - `MYSQL_URL`: MySQL连接URL
   - `MYSQL_USERNAME`: 用户名
   - `MYSQL_PASSWORD`: 密码

3. **RocketMQ** (默认: localhost:9876)
   - `ROCKETMQ_NAME_SERVER`: NameServer地址

4. **Nacos** (默认: 127.0.0.1:8848)
   - `NACOS_SERVER_ADDR`: Nacos服务器地址
   - `NACOS_USERNAME`: 用户名（默认: nacos）
   - `NACOS_PASSWORD`: 密码（默认: nacos）

5. **MinIO** (默认: http://localhost:9000)
   - `MINIO_ENDPOINT`: MinIO端点
   - `MINIO_ACCESS_KEY`: Access Key
   - `MINIO_SECRET_KEY`: Secret Key

### 依赖服务启动
```bash
# 启动所有依赖服务
docker-compose up -d

# 检查服务状态
docker-compose ps
```

### 启动命令
```bash
# 1. 设置Java版本（如果系统默认不是Java 21）
export JAVA_HOME=/path/to/java21

# 2. 编译项目
cd /Users/didi/java_project/Project-Nebula
mvn clean package -DskipTests

# 3. 运行应用
cd orchestrator-core
mvn spring-boot:run

# 或使用jar包
java -jar orchestrator-core/target/orchestrator-core-1.0.0-SNAPSHOT.jar
```

### 验证服务
```bash
# 健康检查
curl http://localhost:8080/api/health

# Actuator健康检查
curl http://localhost:8080/actuator/health

# Prometheus指标
curl http://localhost:8080/actuator/prometheus
```

---

## 🔍 潜在问题（运行时可能遇到）

### 1. 组件未注册
**症状**: Spring Boot启动时找不到某些Bean

**可能原因**:
- 组件扫描配置不正确
- 模块未正确添加到依赖

**检查**: 查看启动日志，确认所有 `@Component` 都已注册

### 2. 配置缺失
**症状**: 某些功能不可用（如Redis、MySQL）

**处理**: 所有组件都使用 `@Autowired(required = false)`，缺失配置时功能会降级，不会阻止启动

### 3. ChatModel未实现
**症状**: LLM调用时抛出 `UnsupportedOperationException`

**处理**: 需要实现 `LangChain4jLLMServiceClient.createChatModel()` 方法

---

## 📊 修复状态总结

| 问题 | 状态 | 优先级 | 说明 |
|------|------|--------|------|
| Maven循环依赖 | ✅ 已修复 | 🔴 高 | 已将所有接口移到orchestrator-api |
| 包路径不匹配 | ✅ 已修复 | 🔴 高 | 文件已移动到正确目录 |
| 组件扫描配置 | ✅ 已修复 | 🔴 高 | 已添加@ComponentScan |
| 导入路径问题 | ✅ 已修复 | 🔴 高 | 已添加正确的import语句 |
| Java版本不匹配 | ⚠️ 需用户配置 | 🔴 高 | **阻止编译，需要Java 21** |
| ChatModel占位实现 | ⚠️ 待实现 | 🟡 中 | 不影响启动，但LLM调用会失败 |
| 未使用的导入 | ✅ 已修复 | 🟢 低 | 已清理 |

---

## 🚀 下一步操作

### 步骤1: 配置Java 21环境（必须）
```bash
# 查找Java 21安装路径（macOS）
/usr/libexec/java_home -V

# 设置JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 验证
java -version  # 应该显示 openjdk version "21.x.x"
mvn -version   # 应该显示 Java version: 21
```

### 步骤2: 编译项目
```bash
cd /Users/didi/java_project/Project-Nebula

# 先编译orchestrator-api（解决依赖）
mvn clean install -DskipTests -pl orchestrator-api

# 然后编译整个项目
mvn clean package -DskipTests
```

### 步骤3: 启动依赖服务
```bash
# 启动所有依赖服务（Redis、MySQL、RocketMQ、Nacos、Prometheus、Grafana）
docker-compose up -d

# 检查服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f
```

### 步骤4: 运行应用
```bash
cd orchestrator-core

# 方式1: 使用Maven运行
mvn spring-boot:run

# 方式2: 使用jar包
java -jar target/orchestrator-core-1.0.0-SNAPSHOT.jar
```

### 步骤5: 验证启动
```bash
# 健康检查
curl http://localhost:8080/api/health

# Actuator健康检查
curl http://localhost:8080/actuator/health

# Prometheus指标
curl http://localhost:8080/actuator/prometheus

# 检查启动日志
# 应该看到：
# - "Started OrchestratorApplication"
# - "Redis客户端初始化成功"（如果配置了Redis）
# - "MySQL连接初始化成功"（如果配置了MySQL）
# - "消费者启动成功"（如果配置了RocketMQ）
```

## ⚠️ 如果启动失败

### 常见错误及解决方案

1. **找不到Bean**
   - 检查组件扫描配置
   - 确认所有模块都已添加到依赖

2. **Redis连接失败**
   - 检查Redis是否启动: `docker-compose ps redis`
   - 检查配置: `REDIS_ADDRESS`, `REDIS_PASSWORD`

3. **MySQL连接失败**
   - 检查MySQL是否启动: `docker-compose ps mysql`
   - 检查配置: `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`
   - 确认数据库已创建: `CREATE DATABASE IF NOT EXISTS ai_orchestrator;`

4. **RocketMQ连接失败**
   - 检查RocketMQ是否启动: `docker-compose ps rocketmq`
   - 检查配置: `ROCKETMQ_NAME_SERVER`

5. **Nacos连接失败**
   - 检查Nacos是否启动: `docker-compose ps nacos`
   - 检查配置: `NACOS_SERVER_ADDR`
   - 访问控制台: http://localhost:8848/nacos (nacos/nacos)

---

**最后更新**: 2026-01-13
