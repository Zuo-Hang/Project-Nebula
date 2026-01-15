package com.wuxiansheng.shieldarch.llm.controller;

import com.wuxiansheng.shieldarch.llm.service.LocalLLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 本地大模型控制器
 * 
 * 提供HTTP接口用于调用本地大模型
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
        Map<String, Object> result = new HashMap<>();
        result.put("service", "Local LLM Client");
        result.put("version", "1.0.0-SNAPSHOT");
        result.put("status", "running");
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
        Map<String, Object> result = new HashMap<>();
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
        log.info("收到推理请求: promptLength={}", 
            request.getPrompt() != null ? request.getPrompt().length() : 0);
        
        try {
            LocalLLMService.InferenceResult result = localLLMService.infer(
                request.getPrompt(),
                request.getImageUrl(),
                request.getOcrText(),
                request.getModel()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("content", result.getContent());
            response.put("inputTokens", result.getInputTokens());
            response.put("outputTokens", result.getOutputTokens());
            response.put("totalTokens", result.getTotalTokens());
            // 返回OCR识别结果（如果有）
            if (result.getOcrText() != null && !result.getOcrText().isEmpty()) {
                response.put("ocrText", result.getOcrText());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("推理失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
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
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "清晰度判断服务未启用，请确保已安装 ffmpeg");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            com.wuxiansheng.shieldarch.llm.service.VideoQualityService.VideoInfo info = 
                videoQualityService.getVideoInfo(fileUrl);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("width", info.getWidth());
            response.put("height", info.getHeight());
            response.put("resolution", info.getResolution());
            response.put("quality", info.getQuality());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取文件清晰度失败", e);
            Map<String, Object> response = new HashMap<>();
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
        
        try {
            // 1. 验证文件大小
            long maxSizeBytes = maxSizeMB * 1024 * 1024;
            if (file.getSize() > maxSizeBytes) {
                Map<String, Object> response = new HashMap<>();
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
                Map<String, Object> response = new HashMap<>();
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
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileUrl", fileUrl);
            response.put("localPath", localFilePath);
            response.put("originalName", originalFilename);
            response.put("size", file.getSize());
            
            // 7. 尝试获取文件清晰度信息（如果支持）
            if (videoQualityService != null && videoQualityService.isFfprobeAvailable()) {
                try {
                    com.wuxiansheng.shieldarch.llm.service.VideoQualityService.VideoInfo qualityInfo = 
                        videoQualityService.getVideoInfo(fileUrl);
                    response.put("quality", qualityInfo.getQuality());
                    response.put("width", qualityInfo.getWidth());
                    response.put("height", qualityInfo.getHeight());
                    response.put("resolution", qualityInfo.getResolution());
                    log.info("文件清晰度信息: quality={}, resolution={}", 
                        qualityInfo.getQuality(), qualityInfo.getResolution());
                } catch (Exception e) {
                    log.warn("获取文件清晰度失败，继续返回上传结果: {}", e.getMessage());
                    // 清晰度获取失败不影响文件上传，继续返回上传结果
                }
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("文件上传失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "文件上传失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 推理请求对象
     */
    public static class InferenceRequest {
        private String prompt;
        private String imageUrl;
        private String ocrText;
        private String model;

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getOcrText() {
            return ocrText;
        }

        public void setOcrText(String ocrText) {
            this.ocrText = ocrText;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}

