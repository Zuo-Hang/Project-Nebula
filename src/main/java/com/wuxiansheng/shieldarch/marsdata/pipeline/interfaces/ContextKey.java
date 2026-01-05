package com.wuxiansheng.shieldarch.marsdata.pipeline.interfaces;

/**
 * 上下文键类型
 * 对应 Go 版本的 interfaces.ContextKey
 */
public enum ContextKey {
    VIDEO_KEY("video_key"),
    VIDEO_KEYS("video_keys"),
    LINK_NAME("link_name"),
    IMAGE_PATHS("image_paths"),
    KEPT_IMAGE_PATHS("kept_image_paths"),
    LOCAL_VIDEO("local_video"),
    OCR_TEXT_BY_IMAGE("ocr_text_by_image"),
    CLASSIFICATION("classification"),
    S3_UPLOAD_INFO("s3_upload_info"),
    SUBMIT_DATE("submit_date"),
    PAGE_TYPE_DEDUP("page_type_dedup"),
    CLEANUP_PATHS("cleanup_paths"),
    VIDEO_METADATA("video_metadata");

    private final String value;

    ContextKey(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

