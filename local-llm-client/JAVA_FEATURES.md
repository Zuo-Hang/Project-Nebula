# 本模块使用的 Java 8+ 新特性说明

本文档整理 `local-llm-client` 中采用的 Java 8 及以上版本特性，并说明各特性的含义与选用原因，便于对比学习与面试陈述。代码中在「新写法」处保留了旧写法注释，可对照阅读。  
**Java 17～21 语言与 API 新特性速览**见 [JAVA_17_TO_21_FEATURES.md](JAVA_17_TO_21_FEATURES.md)。**与其他语言的对照与实现思路**见 [JAVA_VS_OTHER_LANGUAGES.md](JAVA_VS_OTHER_LANGUAGES.md)。

---

## LocalLLMController 中的特性

### 1. var（Java 10+）

**出现位置**：`index()`、`health()`、`infer()`、`deleteUploadedImagesAfterInfer()`、`getFileQuality()`、`uploadImage()` 等处的局部变量。

**特性含义**：**局部变量类型推断**。编译器根据右侧表达式自动推导变量类型，无需重复书写类型名，可读性不降低。例如 `var result = new HashMap<String, Object>()` 中 `result` 的类型为 `HashMap<String, Object>`。

**选用原因**：右侧类型已明确时，减少重复、保持简洁；`uploadPath` 处由 `Paths.get(...).normalize()` 推导为 `Path`，`info` 处由 `getVideoInfo()` 推导为 `VideoInfo`。

---

### 2. Map.of（Java 9+）

**出现位置**：`index()` 中 `result.put("endpoints", Map.of("health", "...", "infer", "..."));`

**特性含义**：**不可变 Map 工厂方法**。用于创建键值对个数已知、创建后不再修改的 Map；创建后不可 put/remove，适合只读配置或常量映射。

**选用原因**：端点信息为固定只读，用 `Map.of` 语义清晰且防止误改。

---

### 3. Optional（Java 8）

**出现位置**：`infer()` 成功分支中，对 `result.getOcrText()` 的处理。

**特性含义**：**表示「可能为空」的容器类型**。  
- `Optional.ofNullable(x)`：将可能为 null 的 `x` 包成 Optional。  
- `filter(Predicate)`：保留满足条件的值。  
- `ifPresent(Consumer)`：仅当有值时执行操作，避免显式 `if (x != null && !x.isEmpty())`。

**选用原因**：仅当 ocrText 非空且非空白时才放入 response，用链式调用替代多重 if，意图更清晰。

---

### 4. instanceof 类型判断（Java 8；Java 16+ Pattern Matching）

**出现位置**：`resolveInferErrorMessage(Throwable e)` 中，根据异常类型返回不同提示语。

**特性含义**：**类型判断**。用于在异常链中识别 `SocketTimeoutException`、`InterruptedIOException` 等。  
Java 16+ 的 **pattern matching for instanceof** 可写为 `if (t instanceof SocketTimeoutException e)`，在判断的同时完成强转，省去手写 `(SocketTimeoutException)t`。

**选用原因**：按异常类型分支返回友好文案，便于扩展更多错误类型。

---

### 5. Stream + 方法引用（Java 8）

**出现位置**：`buildImageUrls()` 中 `request.getImageUrls().stream().filter(...).map(String::trim).collect(Collectors.toList())`。

**特性含义**：  
- **Stream**：对集合做链式处理，`filter` 过滤、`map` 转换、`collect` 收集为 List，替代手写 for 循环与临时集合。  
- **方法引用 `String::trim`**：等价于 lambda `s -> s.trim()`，语法更简洁。

**选用原因**：对 URL 列表做「去空 + 去空白 + 转 trim」一气呵成，可读性好。

---

### 6. List.of（Java 9+）

**出现位置**：`buildImageUrls()` 中单元素列表与空列表的返回：`List.of(request.getImageUrl().trim())`、`List.of()`。

**特性含义**：**不可变 List 工厂方法**。创建后不可 add/remove/set，空列表与单元素列表语义清晰，且比 `Collections.singletonList`/`Collections.emptyList()` 更简洁。

**选用原因**：返回值仅作只读使用，用不可变列表避免调用方误改；与 Stream 链的 mutable 结果区分开。

---

### 7. uploadImage 中的 var

**出现位置**：`uploadImage()` 方法内多处 `var response = new HashMap<String, Object>();`。

**说明**：本方法中多处使用 var，含义同前（局部变量类型推断）；在方法开头已用一句注释概括，避免每行重复。

---

## LocalLLMService 中的特性

### 1. ConcurrentHashMap（Java 8）

**出现位置**：`modelCache` 字段：`private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();`

**特性含义**：**线程安全的 HashMap**。支持高并发读写，内部采用细粒度锁（如分段锁或 CAS），无需在业务代码里对整张 map 做 `synchronized`，适合做「按 key 缓存」的场景。

**选用原因**：多请求可能并发触发同一模型的首次加载，用 ConcurrentHashMap 保证线程安全且性能优于「HashMap + 外层 synchronized」。

---

### 2. List.of（Java 9+）

**出现位置**：`infer()` 中 `model.generate(List.of(userMessage))`。

**特性含义**：**不可变 List 工厂**，单元素列表语义清晰，创建后不可修改。

**选用原因**：LangChain4j 的 `generate` 接受 `List<Message>`，单条消息用 `List.of(userMessage)` 表达「只读单元素列表」，比 `Collections.singletonList` 更直观。

---

### 3. Optional（Java 8）

**出现位置**：`infer()` 中从 `response.tokenUsage()` 取 input/output/total tokens。

**特性含义**：**表示可能为空的容器**。  
- `Optional.ofNullable(x)`：包装可能为 null 的 `x`。  
- `map(Function)`：仅当有值时做转换。  
- `orElse(null)`：空时返回 null，避免重复写 `tokenUsage != null ? tokenUsage.inputTokenCount() : null`。

**选用原因**：Ollama 部分响应可能不包含 tokenUsage，用 Optional 链式解包，避免 NPE 与冗长三元表达式。

---

### 4. computeIfAbsent + Duration（Java 8）

**出现位置**：`getOrCreateModel(String modelName)` 中 `modelCache.computeIfAbsent(modelName, name -> { ... })`，以及 `OllamaChatModel.builder().timeout(Duration.ofMillis(ollamaTimeoutMs))`。

**特性含义**：  
- **computeIfAbsent**：Map 的原子方法。若 key 不存在，则用传入的 lambda 计算 value 并放入 map，若已存在则直接返回已有 value，保证「每个 key 只计算一次」，适合懒加载缓存。  
- **Duration.ofMillis(long)**：表示**一段时长**的类型（Java 8 时间 API），比裸的 `long` 毫秒数语义更清晰，且便于与接受 `Duration` 的 API（如 LangChain4j 的 timeout）对接。

**选用原因**：按模型名懒加载并缓存 ChatLanguageModel，避免重复创建；超时配置用 Duration 表达「多长时间」更明确。

---

## 小结表

| 特性 | 版本 | 含义简述 | 在本模块中的用途 |
|------|------|----------|------------------|
| var | 10+ | 局部变量类型推断 | 简化 HashMap/Path/VideoInfo 等局部变量声明 |
| Map.of | 9+ | 不可变 Map 工厂 | 只读端点信息 |
| List.of | 9+ | 不可变 List 工厂 | 只读单元素列表、空列表 |
| Optional | 8 | 可能为空的容器 | 安全解包 ocrText、tokenUsage，避免 NPE |
| Stream + 方法引用 | 8 | 链式处理集合；String::trim | 过滤并 trim 图片 URL 列表 |
| instanceof | 8（16+ pattern matching） | 类型判断（及强转） | 按异常类型返回不同错误提示 |
| ConcurrentHashMap | 8 | 线程安全 HashMap | 按模型名缓存 ChatLanguageModel |
| computeIfAbsent | 8 | 无则计算并放入 | 懒加载并缓存模型实例 |
| Duration | 8 | 时间量类型 | 配置 Ollama 调用超时 |

---

## 可进一步落地的特性及推荐位置

下面按特性列出本模块中**适合改写的具体位置**。**以下 1～4 已在代码中落地**，并保留「对比学习」旧写法注释及新特性含义说明。

### 1. record（Java 16+）✅ 已落地

| 位置 | 说明 |
|------|------|
| **LocalLLMService.InferenceResult** | 已改为 `record InferenceResult(content, inputTokens, outputTokens, totalTokens, ocrText)`，创建处使用规范构造器，调用处使用 `result.content()`、`result.ocrText()` 等。 |
| **LocalLLMController.InferenceRequest** | 已改为 `record InferenceRequest(prompt, imageUrl, imageUrls, ocrText, model)`，Spring/Jackson 通过规范构造器反序列化；调用处已改为 `request.prompt()`、`request.imageUrls()` 等。 |
| **VideoQualityService.VideoInfo** | 已改为 `record VideoInfo(int width, int height, String quality)`，`resolution` 由 accessor 推导；调用处已改为 `info.width()`、`info.resolution()` 等。 |

### 2. switch 表达式（Java 14+）✅ 已落地

| 位置 | 说明 |
|------|------|
| **VideoQualityService.determineQuality** | 已改为先算 `tier` 再 `return switch (tier) { case 8 -> "4K (2160p)"; ... default -> ...; }`，旧 if-return 链已以注释保留。 |
| **LocalLLMController.uploadImage** 中文件类型校验 | 当前用 `for (allowedType) if (contentType.equals(allowedType))`。若允许类型固定且不多，也可用 `Set.of(allowedTypes).contains(contentType)` 或后续按「类型→说明」用 switch 返回错误文案。 |

### 3. 文本块（Java 15+）✅ 已落地

| 位置 | 说明 |
|------|------|
| **LocalLLMService.buildFullPrompt** | 已改用 `String.format("""%s%n%nOCR识别结果：%n%s""", prompt, ocrText)` 文本块多行字面量，旧单行 format 已以注释保留。今后若有更长多行 prompt 或 JSON 模板可继续用 `"""..."""`。 |

### 4. Pattern matching for instanceof（Java 16+）✅ 已落地

| 位置 | 说明 |
|------|------|
| **LocalLLMController.resolveInferErrorMessage** | 已改为 `if (t instanceof SocketTimeoutException socketEx || t instanceof InterruptedIOException interruptedEx)` 的 pattern matching 写法，旧 if 条件已注释保留。 |
