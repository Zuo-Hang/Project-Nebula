package com.wuxiansheng.shieldarch.marsdata.business.bsaas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * B SaaS输入数据
 */
@Data
public class BSaasInput {
    
    private Meta meta;
    private List<Image> images = new ArrayList<>();
    private String business;
    
    @JsonProperty("sub_line")
    private String subLine;
    
    @JsonProperty("submit_date")
    private String submitDate;
    
    private LocalDateTime submitDateTime;
    
    @Data
    public static class Meta {
        private String id;
        
        @JsonProperty("video_url")
        private String videoURL;
        
        @JsonProperty("city_name")
        private String cityName;
        
        @JsonProperty("supplier_name")
        private String supplierName;
        
        @JsonProperty("driver_name")
        private String driverName;
        
        @JsonProperty("file_city_name")
        private String fileCityName;
        
        @JsonProperty("city_illegal")
        private Boolean cityIllegal;
    }
    
    @Data
    public static class Image {
        private Integer index;
        private String url;
        private List<String> types = new ArrayList<>();
    }
}

