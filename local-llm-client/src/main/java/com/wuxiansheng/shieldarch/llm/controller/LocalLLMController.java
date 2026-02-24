package com.wuxiansheng.shieldarch.llm.controller;

import com.wuxiansheng.shieldarch.llm.service.LocalLLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 本地大模型控制器
 *
 * 提供 HTTP 接口用于调用本地大模型。
 * 本类使用 Java 8+ 特性（var、List.of、Optional 等），旧写法以注释保留在「对比学习」块中。
 */
@Slf4j
@RestController
@RequestMapping("/api/llm")
public class LocalLLMController {

    @Autowired
    private LocalLLMService localLLMService;

    @Autowired(required = false)
    private com.wuxiansheng.shieldarch.llm.service.VideoQualityService videoQualityService;

    @Value("${local-llm.upload.directory:./uploads}")
    private String uploadDirectory;

    @Value("${local-llm.upload.max-size-mb:10}")
    private long maxSizeMB;

    @Value("#{'${local-llm.upload.allowed-types:image/jpeg,image/png,image/gif,image/webp}'.split(',')}")
    private String[] allowedTypes;

    /**
     * 根路径 - 欢迎信息
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> index() {
        // ----- 对比学习：旧写法 -----
        // Map<String, Object> result = new HashMap<>();
        // ----- 新写法 + 特性含义 -----
        // var（Java 10+）：局部变量类型推断，编译器根据右侧推导类型，减少重复书写
        var result = new HashMap<String, Object>();
        result.put("service", "Local LLM Client");
        result.put("version", "1.0.0-SNAPSHOT");
        result.put("status", "running");
        // Map.of（Java 9+）：不可变 Map 工厂，键值对创建后不可修改，适合只读配置
        result.put("endpoints", Map.of(
            "health", "/api/llm/health",
            "infer", "/api/llm/infer"
        ));
        return ResponseEntity.ok(result);
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        // ----- 对比学习：旧写法 -----
        // Map<String, Object> result = new HashMap<>();
        // ----- 新写法 + 特性含义：var 局部变量类型推断 -----
        var result = new HashMap<String, Object>();
        result.put("status", "UP");
        result.put("service", "Local LLM Client");
        result.put("serviceAvailable", localLLMService.isServiceAvailable());
        result.put("availableModels", localLLMService.getAvailableModels());
        return ResponseEntity.ok(result);
    }

    /**
     * 调用本地大模型进行推理
     * 
     * @param request 推理请求
     * @return 推理结果
     */
    @PostMapping("/infer")
    public ResponseEntity<Map<String, Object>> infer(@RequestBody InferenceRequest request) {
        log.info("收到推理请求: promptLength={}, imageCount={}",
            request.prompt() != null ? request.prompt().length() : 0,
            request.imageUrls() != null ? request.imageUrls().size() : (request.imageUrl() != null ? 1 : 0));

        List<String> imageUrls = buildImageUrls(request);

        try {
            LocalLLMService.InferenceResult result = localLLMService.infer(
                request.prompt(),
                imageUrls,
                request.ocrText(),
                request.model()
            );

            // ----- 对比学习：旧写法 -----
            // Map<String, Object> response = new HashMap<>();
            // if (result.getOcrText() != null && !result.getOcrText().isEmpty()) {
            //     response.put("ocrText", result.getOcrText());
            // }
            // ----- 新写法 + 特性含义 -----
            // var：局部变量类型推断（同上）
            var response = new HashMap<String, Object>();
            response.put("success", true);
            response.put("content", result.content());
            response.put("inputTokens", result.inputTokens());
            response.put("outputTokens", result.outputTokens());
            response.put("totalTokens", result.totalTokens());
            // Optional（Java 8）：表示「可能为空」的容器；ofNullable 包装可能 null 的值，
            // filter 过滤空白串，ifPresent 仅非空时执行操作，避免显式 if (x != null && !x.isEmpty())
            java.util.Optional.ofNullable(result.ocrText())
                .filter(s -> !s.isBlank())
                .ifPresent(s -> response.put("ocrText", s));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("推理失败", e);
            String userMessage = resolveInferErrorMessage(e);
            // ----- 对比学习：旧写法 -----
            // Map<String, Object> response = new HashMap<>();
            // ----- 新写法 + 特性含义：var 局部变量类型推断 -----
            var response = new HashMap<String, Object>();
            response.put("success", false);
            response.put("error", userMessage);
            return ResponseEntity.internalServerError().body(response);
        } finally {
            deleteUploadedImagesAfterInfer(imageUrls);
        }
    }

    /**
     * 推理结束后删除本次请求中使用的、位于上传目录下的本地图片，避免磁盘堆积。
     * 仅删除 file:// 且路径在本服务上传目录内的文件。
     */
    private void deleteUploadedImagesAfterInfer(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        // ----- 对比学习：旧写法 -----
        // Path uploadPath = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        // ----- 新写法 + 特性含义：var 局部变量类型推断，Path 类型由右侧推导 -----
        var uploadPath = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        for (String url : imageUrls) {
            if (url == null || !url.startsWith("file://")) {
                continue;
            }
            String pathStr = url.substring(7).trim();
            if (pathStr.isEmpty()) {
                continue;
            }
            try {
                Path path = Paths.get(pathStr).toAbsolutePath().normalize();
                if (!path.startsWith(uploadPath) || !Files.isRegularFile(path)) {
                    continue;
                }
                Files.delete(path);
                log.debug("推理后删除上传图片: {}", path.getFileName());
            } catch (IOException e) {
                log.warn("删除上传图片失败: path={}, error={}", pathStr, e.getMessage());
            }
        }
    }

    /**
     * 根据异常类型返回对用户友好的错误说明（Ollama 未启动 / 超时 / 其他）。
     * 特性含义：instanceof 用于类型判断；Java 16+ pattern matching 可写
     * if (t instanceof SocketTimeoutException e) 同时完成判断与强转，省去手写 (SocketTimeoutException)t。
     * 此处 pattern 变量仅用于类型判断，未使用其引用，故抑制未使用警告。
     */
    @SuppressWarnings("unused")
    private String resolveInferErrorMessage(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String msg = t.getMessage() != null ? t.getMessage() : "";
            if (msg.contains("Connection refused") || msg.contains("Failed to connect")) {
                return "Ollama 未启动或不可达，请确认本机已启动 Ollama（如执行 ollama serve）并监听 11434 端口。";
            }
            // ----- 对比学习：旧写法 -----
            // if (msg.contains("timeout") || t instanceof java.net.SocketTimeoutException
            //     || t instanceof java.io.InterruptedIOException) {
            //     return "Ollama 响应超时...";
            // }
            // ----- 新写法 + 特性含义：Pattern matching for instanceof（Java 16+），判断同时声明变量 e，如需用异常信息可直接用 e.getMessage() -----
            if (msg.contains("timeout")) {
                return "Ollama 响应超时，多模态/大图推理较慢时可增大配置 local-llm.ollama.timeout（毫秒）或稍后重试。";
            }
            if (t instanceof java.net.SocketTimeoutException socketEx || t instanceof java.io.InterruptedIOException interruptedEx) {
                return "Ollama 响应超时，多模态/大图推理较慢时可增大配置 local-llm.ollama.timeout（毫秒）或稍后重试。";
            }
            t = t.getCause();
        }
        return e.getMessage() != null ? e.getMessage() : "推理失败，请查看服务端日志。";
    }

    /**
     * 构建图片 URL 列表：优先使用 imageUrls，否则用 imageUrl 转为单元素列表。
     */
    private List<String> buildImageUrls(InferenceRequest request) {
        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            // Stream（Java 8）：链式处理集合，filter 过滤、map 转换、collect 收集为 List；
            // String::trim 为方法引用，等价于 url -> url.trim()
            return request.imageUrls().stream()
                .filter(url -> url != null && !url.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toList());
        }
        // ----- 对比学习：旧写法 -----
        // if (request.getImageUrl() != null && !request.getImageUrl().trim().isEmpty()) {
        //     return Collections.singletonList(request.getImageUrl().trim());
        // }
        // return Collections.emptyList();
        // ----- 新写法 + 特性含义：List.of（Java 9+）不可变 List 工厂，创建后不可增删改，空列表/单元素语义清晰 -----
        if (request.imageUrl() != null && !request.imageUrl().trim().isEmpty()) {
            return List.of(request.imageUrl().trim());
        }
        return List.of();
    }

    /**
     * 获取文件清晰度信息
     * 
     * @param fileUrl 文件路径（支持 file:// 协议或本地路径）
     * @return 清晰度信息
     */
    @GetMapping("/quality")
    public ResponseEntity<Map<String, Object>> getFileQuality(
            @RequestParam("fileUrl") String fileUrl) {
        log.info("收到清晰度查询请求: fileUrl={}", fileUrl);
        
        if (videoQualityService == null) {
            // ----- 对比学习：旧写法 Map<String, Object> response = new HashMap<>(); -----
            // ----- 新写法 + 特性含义：var 局部变量类型推断 -----
            var response = new HashMap<String, Object>();
            response.put("success", false);
            response.put("error", "清晰度判断服务未启用，请确保已安装 ffmpeg");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            // ----- 对比学习：旧写法 VideoInfo info = videoQualityService.getVideoInfo(fileUrl); -----
            // ----- 新写法 + 特性含义：var 推断为 VideoInfo，右侧返回值类型即变量类型 -----
            var info = videoQualityService.getVideoInfo(fileUrl);
            var response = new HashMap<String, Object>();
            response.put("success", true);
            response.put("width", info.width());
            response.put("height", info.height());
            response.put("resolution", info.resolution());
            response.put("quality", info.quality());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取文件清晰度失败", e);
            var response = new HashMap<String, Object>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 上传图片文件
     * 
     * @param file 图片文件
     * @return 上传结果，包含本地文件路径
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        log.info("收到文件上传请求: fileName={}, size={} bytes, contentType={}", 
            file.getOriginalFilename(), file.getSize(), file.getContentType());
        // 本方法中多处 var response：var 为局部变量类型推断（Java 10+），含义同前
        try {
            // 1. 验证文件大小
            long maxSizeBytes = maxSizeMB * 1024 * 1024;
            if (file.getSize() > maxSizeBytes) {
                var response = new HashMap<String, Object>();
                response.put("success", false);
                response.put("error", String.format("文件大小超过限制: %d MB", maxSizeMB));
                return ResponseEntity.badRequest().body(response);
            }
            
            // 2. 验证文件类型
            String contentType = file.getContentType();
            boolean isAllowed = false;
            if (contentType != null) {
                for (String allowedType : allowedTypes) {
                    if (contentType.equals(allowedType)) {
                        isAllowed = true;
                        break;
                    }
                }
            }
            
            if (!isAllowed) {
                var response = new HashMap<String, Object>();
                response.put("success", false);
                response.put("error", "不支持的文件类型: " + contentType);
                return ResponseEntity.badRequest().body(response);
            }
            
            // 3. 创建上传目录（如果不存在）
            Path uploadPath = Paths.get(uploadDirectory);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("创建上传目录: {}", uploadPath.toAbsolutePath());
            }
            
            // 4. 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + extension;
            Path filePath = uploadPath.resolve(uniqueFilename);
            
            // 5. 保存文件
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            // 6. 构建本地文件路径（使用 file:// 协议）
            String localFilePath = filePath.toAbsolutePath().toString();
            // 转换为 file:// URL 格式
            String fileUrl = "file://" + localFilePath;
            
            log.info("文件上传成功: originalName={}, savedPath={}, fileUrl={}", 
                originalFilename, localFilePath, fileUrl);
            
            var response = new HashMap<String, Object>();
            response.put("success", true);
            response.put("fileUrl", fileUrl);
            response.put("localPath", localFilePath);
            response.put("originalName", originalFilename);
            response.put("size", file.getSize());
            
            // 7. 尝试获取文件清晰度信息（如果支持）
            if (videoQualityService != null && videoQualityService.isFfprobeAvailable()) {
                try {
                    var qualityInfo = videoQualityService.getVideoInfo(fileUrl);
                    response.put("quality", qualityInfo.quality());
                    response.put("width", qualityInfo.width());
                    response.put("height", qualityInfo.height());
                    response.put("resolution", qualityInfo.resolution());
                    log.info("文件清晰度信息: quality={}, resolution={}", 
                        qualityInfo.quality(), qualityInfo.resolution());
                } catch (Exception e) {
                    log.warn("获取文件清晰度失败，继续返回上传结果: {}", e.getMessage());
                    // 清晰度获取失败不影响文件上传，继续返回上传结果
                }
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("文件上传失败", e);
            var response = new HashMap<String, Object>();
            response.put("success", false);
            response.put("error", "文件上传失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 推理请求对象。
     * ----- 对比学习：旧写法为 class + 私有字段 + getter/setter（约 50 行）。
     * ----- 新写法 + 特性含义：record（Java 16+）不可变数据载体，仅声明组件即自动生成构造器、访问器、equals/hashCode/toString，适合 DTO；
     * Spring/Jackson 通过规范构造器反序列化 @RequestBody。
     */
    public record InferenceRequest(
        String prompt,
        /** 单张图片 URL（与 imageUrls 二选一，兼容旧版） */
        String imageUrl,
        /** 多张图片 URL 列表，一次识别多图时使用 */
        List<String> imageUrls,
        String ocrText,
        String model
    ) {}
}

