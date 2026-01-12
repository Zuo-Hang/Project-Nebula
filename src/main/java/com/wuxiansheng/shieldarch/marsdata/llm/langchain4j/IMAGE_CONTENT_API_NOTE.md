# LangChain4j ImageContent API 说明

## ⚠️ 重要提示

由于 LangChain4j 的 `ImageContent` API 可能因版本而异，当前实现中的 `DiSFChatModelNative` 使用了反射来尝试获取图片信息。

## 🔍 如何确定正确的 API

### 方法 1：查看 LangChain4j 源码

```bash
# 查看依赖的 jar 包
mvn dependency:tree | grep langchain4j

# 解压 jar 包查看源码
unzip ~/.m2/repository/dev/langchain4j/langchain4j/0.29.1/langchain4j-0.29.1.jar
```

### 方法 2：查看官方文档

访问 [LangChain4j 官方文档](https://docs.langchain4j.info/) 查看 `ImageContent` 的 API。

### 方法 3：使用 IDE 自动补全

在 IDE 中输入 `ImageContent.` 查看可用的方法。

## 📝 可能的 API 形式

### 形式 1：使用 source() 方法

```java
ImageContent imageContent = ImageContent.from(imageUrl);
String source = imageContent.source(); // 返回 URL 或 Base64
```

### 形式 2：使用 getter 方法

```java
ImageContent imageContent = ImageContent.from(imageUrl);
String url = imageContent.getUrl();
String base64 = imageContent.getBase64Data();
String mimeType = imageContent.getMimeType();
```

### 形式 3：使用 image() 方法

```java
ImageContent imageContent = ImageContent.from(imageUrl);
Image image = imageContent.image(); // 返回 Image 对象
```

## 🔧 当前实现的解决方案

由于不确定具体的 API，当前实现使用了以下策略：

1. **反射方式**：尝试通过反射获取图片信息
2. **备用方案**：如果反射失败，记录警告日志
3. **建议**：根据实际的 LangChain4j 版本调整代码

## ✅ 推荐的修复步骤

1. **确定 API**：
   ```java
   ImageContent imageContent = ImageContent.from("https://example.com/image.jpg");
   // 在 IDE 中查看 imageContent 的可用方法
   ```

2. **更新代码**：
   根据实际的 API 更新 `DiSFChatModelNative.convertMessage()` 方法

3. **测试验证**：
   确保多模态消息能正确转换为 API 格式

## 📚 参考资源

- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
- [LangChain4j 文档](https://docs.langchain4j.info/)
- [LangChain4j 示例](https://github.com/langchain4j/langchain4j-examples)

