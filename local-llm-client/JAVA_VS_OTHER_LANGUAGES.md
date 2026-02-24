# Java 新特性 vs 其他语言：对照与实现思路

本文对比 Java 各版本新特性与其他语言中的同类概念，以及各自的实现思路与异同。仅作归纳与讨论用，不涉及项目配置或代码变更。

---

## 一、总体关系

Java 8 及之后很多「新特性」在其它语言里早已存在。Java 主要是**把业界成熟的抽象用偏保守、兼容旧代码的方式收进语言和标准库**，而不是发明新范式。共性在于**抽象和目标**，差异多在**实现手段和约束**。

---

## 二、分项对照

| 特性 | 其他语言里的同类 | 异同与实现思路 |
|------|------------------|----------------|
| **Stream / 流式 API** | Scala 集合/`for`、C# LINQ、Kotlin `Sequence`/集合链、Rx 系 | **同**：声明式、链式、可惰性。**异**：Java 显式区分中间/终端操作，无真正集合字面量，和 Optional/异常结合更啰嗦。 |
| **Lambda** | JS/Python/Scala/Kotlin/C# 等「函数是一等公民」 | **同**：匿名函数、闭包。**异**：Java 用 SAM 接口承载，无独立函数类型，语法和类型推导比原生函数式语言弱。 |
| **var** | Scala `val`/`var`、Kotlin、C# `var`、Go `:=` | **同**：局部类型推导。**异**：Java 仅限局部变量且右侧必须有类型，不做全局推理，偏保守。 |
| **Record** | Scala case class、Kotlin data class、C# record、TS 等 | **同**：不可变数据载体、构造/访问器/equals/hashCode 生成。**异**：Java 不生成 copy/component 命名空间等，语义更「只做数据」。 |
| **Pattern matching（instanceof / switch）** | Scala/Kotlin/Rust 的 match、Swift 的 switch | **同**：按类型/结构分支、可绑定变量。**异**：Java 分多代逐步加（instanceof → switch → record patterns），语法和穷举检查比 Scala/Rust 弱。 |
| **Sealed class** | Scala sealed trait/ADT、Kotlin sealed class、TS 联合类型 | **同**：限定子类型集合、便于穷举。**异**：Java 用 `permits` 显式列出，和 pattern matching 一起用才完整。 |
| **文本块 `"""`** | 多语言多行字符串、Kotlin `"""`、Swift `"""`、Python `"""` | **同**：多行、少转义。**异**：Java 规定缩进和换行规则，避免随意格式。 |
| **虚拟线程** | Go goroutine、Kotlin 协程、Erlang process、async/await 系 | **同**：轻量并发单元、大并发数、写法近似「一线程一任务」。**异**：Go 语言层 M:N 调度；Java 在 JVM 上做「用户态调度」，挂到少量 OS 线程，和 Kotlin 协程/Loom 思路更接近，可说「模型对标 goroutine，实现是 JVM 版协程」。 |

---

## 三、实现思路上的共同点

- **渐进式**：不推翻旧模型（如不改现有线程模型），用新 API/新语法叠加。
- **兼容优先**：少破坏现有字节码和库（如 lambda 编译成 invokedynamic + 接口）。
- **类型安全**：多数特性在类型系统里可表达（Record、Sealed、Pattern matching 的穷举）。
- **「借鉴但收窄」**：借鉴其他语言的抽象，但约束使用范围（如 var 仅局部、Record 不做继承等）。

---

## 四、和其他语言的差异点

- **没有真正的一等函数类型**：用接口和泛型模拟，导致高阶 API 不如 Scala/Kotlin 简洁。
- **并发路线**：先有 CompletableFuture/响应式，再补虚拟线程，而不是像 Go 那样一开始就 goroutine。
- **模式匹配/ADT**：分批引入（instanceof → switch → record patterns），语法和表达能力比 Scala/Rust 弱，但更容易在现有代码上逐步用。
- **生态权重**：很多「新写法」要兼顾 Spring、Jackson、旧库的用法，所以 record、密封类等会考虑反射、序列化、代理等。

---

## 五、一句话总结

**Java 的新特性是在「不砸烂老 Java」的前提下，把其它语言验证过的抽象（流式、lambda、类型推导、数据类、模式匹配、轻量并发等）用更保守的实现和语法搬进来；和这些语言的「同」在目标和用法，「异」在实现路径和约束程度。**

---

*与本模块的 Java 8+ 特性说明、Java 17～21 速览并列使用，可参阅 [JAVA_FEATURES.md](JAVA_FEATURES.md)、[JAVA_17_TO_21_FEATURES.md](JAVA_17_TO_21_FEATURES.md)。Goroutine 与虚拟线程的详细对比见 [GOROUTINE_VS_VIRTUAL_THREADS.md](GOROUTINE_VS_VIRTUAL_THREADS.md)。*
