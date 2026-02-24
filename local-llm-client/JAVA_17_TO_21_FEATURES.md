# Java 17 ～ Java 21 新特性速览

本文按版本整理 **Java 17 到 Java 21** 的语言与重要 API 变化，便于选型与迁移。  
更早版本（8～16）的常用特性见 [JAVA_FEATURES.md](JAVA_FEATURES.md)。

---

## 版本与支持

| 版本   | 发布日期   | 类型   | 说明 |
|--------|------------|--------|------|
| Java 17 | 2021-09  | **LTS** | 当前常用 LTS，长期支持 |
| Java 18 | 2022-03  | 短期   | 6 个月支持 |
| Java 19 | 2022-09  | 短期   | 6 个月支持 |
| Java 20 | 2023-03  | 短期   | 6 个月支持 |
| Java 21 | 2023-09  | **LTS** | 最新 LTS，虚拟线程等正式落地 |

---

## Java 17（LTS）

### 语言

| 特性 | JEP | 说明 |
|------|-----|------|
| **Sealed Classes（密封类）** | 409 | **正式**。限制只有指定子类/实现类可继承或实现，便于穷举与模式匹配。`sealed class Shape permits Circle, Square { }` |
| **Pattern matching for switch** | 406 | **Preview**。在 switch 中对类型做模式匹配，支持 `case String s ->` 等，为后续 JEP 420/427/433 打基础 |
| 浮点语义 | — | 始终使用严格浮点语义，`strictfp` 不再必要（语义统一） |

### 库与平台

- **Context-Specific Deserialization Filters**：可配置反序列化过滤，提升安全
- **Enhanced Pseudo-Random Number Generators**：统一随机数接口，支持算法替换与流式
- **Foreign Function & Memory API**：Incubator，与原生代码/内存互操作（替代 JNI 的方向）
- **Deprecate Finalization**：标记 `Object.finalize()` 等为废弃，为未来移除做准备
- **macOS/AArch64、Windows/AArch64** 等端口与 Unix-domain socket 支持

---

## Java 18

### 语言

| 特性 | JEP | 说明 |
|------|-----|------|
| **Pattern matching for switch** | 420 | **Second Preview**。增强 dominance 检查、穷举检查（含泛型密封类） |

### 库与工具

- **UTF-8 by Default**：默认 charset 改为 UTF-8
- **Simple Web Server**：`jwebserver` 命令行，简易静态/开发用 HTTP 服务
- **Code Snippets in JavaDoc**：`@snippet` 等，在 API 文档中嵌入示例代码
- **Internet-Address Resolution SPI**：可插拔的 InetAddress 解析（如自定义 DNS）
- **Vector API**：Third Incubator
- **FFM API**：Second Incubator
- **Deprecate Finalization for Removal**：进一步明确将移除 Finalization

---

## Java 19

### 语言

| 特性 | JEP | 说明 |
|------|-----|------|
| **Record Patterns** | 405 | **Preview**。可对 record 做解构匹配，如 `if (o instanceof Point(int x, int y)) { ... }`，支持嵌套 |
| **Pattern matching for switch** | 427 | **Third Preview**。支持 `when` 子句（守卫）、null 与穷举语义更清晰 |

### 并发与 API

| 特性 | JEP | 说明 |
|------|-----|------|
| **Virtual Threads（虚拟线程）** | 425 | **Preview**。轻量线程，简化高并发编程，为 Java 21 正式版铺路 |
| **Structured Concurrency** | 428 | **Incubator**。将多线程任务视为一个工作单元，便于取消、错误与可观测性 |

### 其它

- **Foreign Function & Memory API**：Preview
- **Vector API**：Fourth Incubator
- **Linux/RISC-V Port**：JEP 422

---

## Java 20

### 语言

| 特性 | JEP | 说明 |
|------|-----|------|
| **Record Patterns** | 432 | **Second Preview**。泛型 record 模式类型推断、可在 enhanced for 头中使用 |
| **Pattern matching for switch** | 433 | **Fourth Preview**。switch 标签语法简化、泛型类型推断；枚举等无匹配时抛 `MatchException` |

### 并发与 API

| 特性 | JEP | 说明 |
|------|-----|------|
| **Virtual Threads** | 436 | **Second Preview** |
| **Structured Concurrency** | 437 | **Second Incubator** |
| **Scoped Values** | 429 | **Incubator**。在限定作用域内共享不可变数据，作为 ThreadLocal 的替代方案 |

### 其它

- **Vector API**：Fifth Incubator
- **FFM API**：Second Preview

---

## Java 21（LTS）

### 语言（正式 / 稳定）

| 特性 | JEP | 说明 |
|------|-----|------|
| **Record Patterns** | 440 | **正式**。对 record 解构匹配，可嵌套，便于数据导航与处理；增强 for 中的 record 模式支持在 21 中移除 |
| **Pattern matching for switch** | 441 | **正式**。类型模式、守卫、穷举检查；支持限定枚举常量作为 case；移除括号模式等 |

### 语言（Preview）

| 特性 | JEP | 说明 |
|------|-----|------|
| **String Templates** | 430 | 字符串模板，字面量中嵌入表达式与模板处理器，如 `STR."Hello \{name}"` |
| **Unnamed Patterns and Variables** | 443 | 使用 `_` 表示“不关心”的模式组件或未使用变量，简化书写 |
| **Unnamed Classes and Instance Main Methods** | 445 | 无类名顶层类与实例 main 方法，便于单文件/入门教学 |

### 并发与 API（正式）

| 特性 | JEP | 说明 |
|------|-----|------|
| **Virtual Threads** | 444 | **正式**。轻量虚拟线程，适合高吞吐、thread-per-request 风格，便于观测与调试 |
| **Sequenced Collections** | 431 | 有序集合统一接口（如 `addFirst`/`addLast`、`reversed()`） |
| **StringBuilder / StringBuffer** | — | 支持 `repeat(int)` 等 |

### 其它

- **Structured Concurrency**：从 Incubator 进入 Preview
- **Scoped Values**：从 Incubator 进入 Preview
- **Key Encapsulation Mechanism API**：新 API
- **Generational ZGC**：分代 ZGC，降低 GC 延迟
- **Deprecate Windows 32-bit x86** 等

---

## 小结表（17～21 常用点）

| 特性 | 17 | 18 | 19 | 20 | 21 |
|------|----|----|----|----|-----|
| Sealed Classes | ✅ 正式 | — | — | — | — |
| Pattern matching for switch | Preview | Preview | Preview | Preview | ✅ 正式 |
| Record Patterns | — | — | Preview | Preview | ✅ 正式 |
| Virtual Threads | — | — | Preview | Preview | ✅ 正式 |
| String Templates | — | — | — | — | Preview |
| Unnamed patterns/variables (_) | — | — | — | — | Preview |
| UTF-8 默认 | — | ✅ | — | — | — |
| FFM API | Incubator | Incubator | Preview | Preview | — |

---

## 与本模块的关系

- 当前模块使用 **Java 21**，已用到：**record**、**switch 表达式**、**文本块**、**Pattern matching for instanceof** 等（见 [JAVA_FEATURES.md](JAVA_FEATURES.md)）。
- 若升级或统一到 **Java 21**，可考虑：
  - **Record Patterns**：在 `instanceof`/`switch` 中对 `InferenceResult`、`VideoInfo` 等做解构，如 `case InferenceResult(String c, Integer it, Integer ot, Integer tt, String ocr) -> ...`
  - **Virtual Threads**：高并发 HTTP/LLM 调用时可选用虚拟线程池
  - **Unnamed variables**：在 pattern 中不需要的组件用 `_`，避免未使用变量告警
  - **String Templates**（Preview）：若启用预览，可用模板替代部分 `String.format`/拼接

---

*参考： [Oracle Java Language Changes](https://docs.oracle.com/en/java/javase/21/language/java-language-changes.html)、[OpenJDK JEPs](https://openjdk.org/jeps/)*
