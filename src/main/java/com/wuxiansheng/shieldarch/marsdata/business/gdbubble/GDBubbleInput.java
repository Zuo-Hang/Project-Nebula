package com.wuxiansheng.shieldarch.marsdata.business.gdbubble;

import com.wuxiansheng.shieldarch.marsdata.config.LLMConfigHelper;
import com.wuxiansheng.shieldarch.marsdata.llm.ReasonRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 高德冒泡输入数据
 */
@Data
@Component
public class GDBubbleInput {
    
    // 问卷id
    private String estimateId;
    // 问卷类型
    private String activityName;
    // 冒泡页图片
    private List<String> bubbleImageUrls = new ArrayList<>();
    // 城市名
    private String cityName;
    // 城市id
    private Integer cityId;
    // 时间段
    private String timeRange;
    // 里程段
    private String disRange;
    // 区县类型
    private String districtType;
    // 区县名
    private String districtName;
    // 提交人
    private String submitName;
    // 提交时间
    private String submitTime;
    // 提交时间戳（毫秒）
    private Long clientTime;
    // 是否为场站
    private Boolean isStation;
    // 场站名
    private String stationName;
    // 供应商名字
    private String ridePlatformName;
    // 供应商id
    private Integer ridePlatformId;
    
    @Autowired(required = false)
    private LLMConfigHelper llmConfigHelper;
    
    /**
     * 生成推理请求列表
     */
    public List<ReasonRequest> getReasonRequests(String businessName) {
        List<ReasonRequest> requests = new ArrayList<>();
        
        // 从配置中心获取prompt，如果没有则使用默认值
        String prompt = getDefaultPrompt();
        if (llmConfigHelper != null) {
            String configPrompt = llmConfigHelper.getLLMPrompt(businessName, prompt);
            if (configPrompt != null && !configPrompt.isEmpty()) {
                prompt = configPrompt;
            }
        }
        
        for (String url : bubbleImageUrls) {
            ReasonRequest request = new ReasonRequest();
            request.setPicUrl(url);
            request.setPrompt(prompt);
            requests.add(request);
        }
        
        return requests;
    }
    
    /**
     * 解析里程段范围
     */
    public List<Double> disRanges() throws Exception {
        if (disRange == null || disRange.isEmpty()) {
            throw new Exception("invalid dis_range");
        }
        
        // 匹配 0-3km, 3-6km 格式
        Pattern pattern = Pattern.compile("(\\d+)-(\\d+)");
        Matcher matcher = pattern.matcher(disRange);
        
        if (matcher.find()) {
            double left = Double.parseDouble(matcher.group(1));
            double right = Double.parseDouble(matcher.group(2));
            return List.of(left, right);
        }
        
        // 匹配 20km+ 格式
        com.wuxiansheng.shieldarch.marsdata.utils.QuestUtils questUtils = 
            new com.wuxiansheng.shieldarch.marsdata.utils.QuestUtils();
        double left = questUtils.extractFloatPrefix(disRange);
        if (left > 0.0) {
            return List.of(left, 9999.0);
        }
        
        throw new Exception("invalid dis_range: " + disRange);
    }
    
    /**
     * 计算延迟（秒）
     */
    public long delay(long nowTs) {
        return nowTs - clientTime / 1000;
    }
    
    /**
     * 获取默认prompt
     */
    private String getDefaultPrompt() {
        return "你好！你将扮演一个专业的图像数据分析智能体。你的任务是精确地从网约车应用的截图中提取行程和价格信息，并以指定的JSON格式输出。请严格按照以下步骤和规则进行分析。\n" +
                "请确保你的最终输出是一个严格合法的JSON对象，结构如下。注意返回的字符串是一个json的数组格式，且务必不要包含json字符串内容外的任意字符，保证输出的json格式正确。最终输出的内容格式样例为：\n" +
                "\n" +
                "{\n" +
                "    \"start_point\": \"山东现代学院(北门)\",\n" +
                "    \"end_point\": \"马西村委会\",\n" +
                "    \"order_estimated_distance\": \"21.8\",\n" +
                "    \"order_estimated_time\": \"33\",\n" +
                "    \"creation_time\": \"2025-06-10 17:15:50\",\n" +
                "    \"vehicles\": [\n" +
                "    {  \n" +
                "      \"supplier\": \"顺风车\",\n" +
                "      \"price\": \"18.37\",\n" +
                "      \"price_type\": \"一口价\",\n" +
                "      \"discount_type\": \"优惠已减6元\",\n" +
                "      \"discount_amount\": \"6\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"supplier\": \"极速拼车\",\n" +
                "      \"price\": \"35.5/48-59\",\n" +
                "      \"price_type\": \"一口价\",\n" +
                "      \"discount_type\": \"拼成省16.9元\",\n" +
                "      \"discount_amount\": \"16.9\"\n" +
                "    },\n" +
                "    {\n" +
                "        \"supplier\": \"特惠快车\",\n" +
                "        \"price\": \"34.1\",\n" +
                "        \"price_type\": \"一口价\",\n" +
                "        \"discount_type\": \"优惠8.52元\",\n" +
                "        \"discount_amount\": \"8.52\"\n" +
                "    },\n" +
                "    {\n" +
                "        \"supplier\": \"有象约车\",\n" +
                "        \"price\": \"46\",\n" +
                "        \"price_type\": \"预估\",\n" +
                "        \"discount_type\": \"特惠 已优惠5元\",\n" +
                "        \"discount_amount\": \"5\"\n" +
                "    },\n" +
                "    {\n" +
                "        \"supplier\": \"老兵打车\",\n" +
                "        \"price\": \"46\",\n" +
                "        \"price_type\": \"预估\",\n" +
                "        \"discount_type\": \"特惠 已优惠2元\",\n" +
                "        \"discount_amount\": \"2\"\n" +
                "    },\n" +
                "    {\n" +
                "        \"supplier\": \"及时用车\",\n" +
                "        \"price\": \"49\",\n" +
                "        \"price_type\": \"预估\",\n" +
                "        \"discount_type\": \"特惠 已优惠5元\",\n" +
                "        \"discount_amount\": \"5\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "\n" +
                "分析步骤与规则\n" +
                "\n" +
                "第一步：分析全局行程信息 (生成 json对象的关键字段)\n" +
                "\n" +
                "如果图片包含地图视图，请从地图区域提取以下全局信息。如果图片不含地图，则将对应值留空字符串 \"\"。\n" +
                "start_point (起点): 路线的起点，如果起点在地图页面上，记录为\"\",如果图中没有标注，则填写为\"\"。\n" +
                "end_point (终点): 路线的终点，如果终点在地图页面上，忽略，则填写为\"\"。\n" +
                "order_estimated_distance (预估里程):注意仅在有地图的图片上识别该字段。在推荐路线上找到里程数。以\"|\"为分隔符，固定格式是 \"AA|X公里|Y分钟\"，提取X；注意，不要将分隔符'|'识别为数字1。比如，红绿灯少|4.3公里|7分钟，提取\"4.3\",不是\"14.3\";\n" +
                "order_estimated_time (订单预估时间): \n" +
                "1.在地图页面提取，通常以\"分钟\"为单位。固定格式是 \"AA|X公里|Y分钟\"，提取Y；注意，不要将竖线('|')识别为 '1'。比如，红绿灯少|4.3公里|7分钟，提取\"7\",不是\"17\"。注意，忽略\"已选车型应答约Z秒\"信息，不要将Z填入order_estimated_time，填写\"\";\n" +
                "2.如果没有识别到order_estimated_distance (预估里程)，默认填写order_estimated_time为\"\";\n" +
                "creation_time (截图时间): 提取图片中明确标注的日期和时间，格式化为 YYYY-MM-DD HH:MM:SS。\n" +
                "\n" +
                "\n" +
                "\n" +
                "第二步：逐行分析车辆选项 (生成 vehicle 数组)\n" +
                "\n" +
                "现在，请仔细扫描界面中列出的每一个可供选择的打车服务。对于每一个可选项，提取一组信息并作为一个对象添加到 vehicle 数组中。\n" +
                "\n" +
                "核心识别规则：\n" +
                "识别可选项 vs 分组标签:\n" +
                "可选项 (行项目) 是用户可以直接选择并看到价格的条目。它们通常包含一个图标/Logo、一个服务名称 (大号黑体字) 和一个价格。例如：\"顺风车\"、\"极速拼车\"、\"特惠快车\"、\"有象约车\"。\n" +
                "分组标签 (侧边栏) 是位于最左侧、用于分类的灰色小字，例如\"推荐\"、\"拼车\"、\"经济\"、\"特快车\"、\"优享\"。这些是类别，绝不是服务名称，请务必忽略它们！\n" +
                "对每个可选项，提取以下字段：\n" +
                "\n" +
                "supplier (供应商名称):\n" +
                "提取该行项目中最主要的、大号加粗的黑色字体。这代表了供应商或服务类型。\n" +
                "重要：再次确认，这绝不是左侧边栏的灰色分类标签。例如，在\"经济\"这个分组下，supplier应该是\"有象约车\"、\"老兵打车\"等，而不是\"经济\"。\n" +
                "\n" +
                "price (价格):\n" +
                "提取服务名称右侧的大号加粗价格数字。\n" +
                "单一价格: 如 34.1元，提取 34.1。\n" +
                "价格范围: 如 48-59元，提取 48-59。\n" +
                "拼车价格: 对于\"极速拼车\"这类服务，通常会显示两个价格。请按 \"拼成价/未拼成价\" 的格式记录。例如，如果显示\"拼成 35.5元\"和\"未拼成 48-59元\"，则记录为 35.5/48-59。\n" +
                "\n" +
                "price_type (价格类型):\n" +
                "检查价格附近是否有\"预估\"、\"约\"等字样。如果有，则为 \"预估价\"。\n" +
                "如果价格附近有\"一口价\"字样，或没有任何标识（对于固定价格的服务），则默认为 \"一口价\"。\n" +
                "\n" +
                "discount_type (优惠描述):\n" +
                "查找价格下方或附近的小号、通常为灰色或红色的文字。完整记录描述性文本。\n" +
                "例如：\"优惠已减6元\"、\"拼成省16.9元\"、\"优惠8.52元\"。\n" +
                "如果没有，则留空字符串 \"\"。\n" +
                "\n" +
                "discount_amount (优惠金额):\n" +
                "从 discount_type 中提取纯数字。\n" +
                "例如，从\"优惠已减6元\"中提取 6，从\"拼成省16.9元\"中提取 16.9。\n" +
                "如果没有优惠金额，则记录 0。\n" +
                "\n" +
                "请特别注意：\n" +
                "完整性: 确保提取了列表中的所有可选项，不要遗漏任何一个。\n" +
                "底部价格栏: 忽略屏幕最下方蓝色按钮上方的总预估价（如 \"预估 34.1元起\"），它只是一个参考，不代表任何一个具体服务。\n" +
                "如果order_estimated_time (订单预估时间)单位为小时，请转换为分钟为单位；\n" +
                "如果order_estimated_distance (预估里程)单位为米，请转换为公里为单位；\n" +
                "如果路线的起终点在地图上，start_point、end_point记录为\"\";\n" +
                "输出结构：只需要输出json。";
    }
}

