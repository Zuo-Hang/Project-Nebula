package com.wuxiansheng.shieldarch.marsdata.business.couponsp;

import com.wuxiansheng.shieldarch.marsdata.llm.ReasonRequest;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 券包人群标签识别输入数据
 */
@Data
public class CouponSpecialPopulationInput {
    
    private String estimateId;
    private String activityName;
    private String date;
    private String cityName;
    private Integer cityId;
    private String label;
    private Boolean hasActInMainPage;
    private List<String> actUrlsInMainPage = new ArrayList<>();
    private Boolean hasActInVenue;
    private List<String> actUrlsInVenue = new ArrayList<>();
    private Boolean hasOtherAct;
    private List<String> couponListAndDetailUrls = new ArrayList<>();
    private Long submitTimestampMs;
    private Boolean isCouponComplete;
    
    /**
     * 生成推理请求列表
     */
    public List<ReasonRequest> getReasonRequests() {
        List<ReasonRequest> requests = new ArrayList<>();
        
        // TODO: 从配置中心获取prompt，目前使用默认值
        String prompt = getDefaultPrompt();
        
        for (String url : couponListAndDetailUrls) {
            ReasonRequest request = new ReasonRequest();
            request.setPicUrl(url);
            request.setPrompt(prompt);
            requests.add(request);
        }
        
        return requests;
    }
    
    /**
     * 获取默认prompt
     */
    private String getDefaultPrompt() {
        return "你好！你将扮演一个专业的打车券包识别智能体。你的任务是精确地从截图中提取券包的各种信息，并以指定的JSON格式输出。请严格按照以下步骤和规则进行分析。\n" +
                "\n" +
                "# 输出规则：\n" +
                "1.  **你必须输出一个且只有一个合法的JSON对象。**\n" +
                "2.  **绝对不允许在JSON对象之外添加任何额外的解释、道歉、问候或说明文字。**\n" +
                "3.  输出的JSON必须完全遵循以下格式：\n" +
                "\n" +
                " {\n" +
                "\t\"page_catagory\": \"列表页\",\n" +
                "\t\"coupon_name\": \"xxx打车券\",\n" +
                "\t\"deadline\": \"2025-08-01\",\n" +
                "\t\"coupon_type\": \"折扣券\",\n" +
                "\t\"discount\": \"xx折或xxx元\",\n" +
                "\t\"cap\": \"xxx元\",\n" +
                "\t\"threshold\": \"xxx元\",\n" +
                "\t\"valid_days\": \"xxx\",\n" +
                "\t\"valid_period\": \"xxx\",\n" +
                "\t\"supplier_rule\": \"xxx\",\n" +
                "\t\"valid_channel\": \"xx\",\n" +
                "\t\"valid_city\": \"xxx\",\n" +
                "\t\"valid_car_type\": \"xxx\",\n" +
                "\t\"valid_order\": \"xxx\",\n" +
                "\t\"valid_route\": \"xxx\",\n" +
                " }\n" +
                "分析步骤与规则:\n" +
                "1. page_catagory: 图片可能有多种类型，一种是券包列表页，此时为「券包列表页」，一种是券包详情页，此时为「券包详情页」；如果是一个打车列表页而不是一个券包列表页，返回「打车列表页」；\n" +
                "1. coupon_name: 券的名字，一般在图片最上方，深色框内部，字体较大，如果提取不到，直接置为空；\n" +
                "2. deadline: 使用截止日期，格式\"2025-08-01\"；\n" +
                "3. coupon_type: 折扣券类型：\"折扣券\" or \"立减券\" 之一。如果显示「xx折」，是折扣券；如果显示「立减xx元」，是立减券，如果识别不到，直接设置为空；\n" +
                "4. discount: 优惠力度：如果是折扣券，则为\"9折\"；如果未立减券，则为\"5元\"；\n" +
                "5. cap: 最多折扣多少元，如 5.0元；\n" +
                "6. threshold: 使用门槛，一般需要达到多少钱才可以使用此券，如12.0元；\n" +
                "7. valid_days: 透传「可用星期」的值，没有则置为空；\n" +
                "8. valid_period: 透传「可用时段」的值，没有则置为空；\n" +
                "9. supplier_rule: 透传「服务商规则」的值，没有则置为空；\n" +
                "10. valid_channel: 透传「可用渠道」的值，没有则置为空；\n" +
                "11. valid_city: 透传「可用城市」的值，没有则置为空；\n" +
                "12. valid_car_type: 透传「可用车型」的值，没有则置为空；\n" +
                "13. valid_order: 透传「可用订单」的值，没有则置为空；\n" +
                "14. valid_route: 透传「可用路线」的值，没有则置为空；\n";
    }
}

