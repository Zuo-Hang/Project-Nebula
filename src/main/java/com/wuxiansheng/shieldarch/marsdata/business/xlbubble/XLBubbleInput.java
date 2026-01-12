package com.wuxiansheng.shieldarch.marsdata.business.xlbubble;

import com.wuxiansheng.shieldarch.marsdata.config.LLMConfigHelper;
import com.wuxiansheng.shieldarch.marsdata.llm.ReasonRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 小拉冒泡输入数据
 */
@Data
@Component
public class XLBubbleInput {
    
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
    private Long submitTimestampMs;
    
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
     * 获取默认prompt
     */
    private String getDefaultPrompt() {
        return "你好！你将扮演一个专业的图像数据分析智能体。你的任务是精确地从网约车应用的截图中提取行程和价格信息，并以指定的JSON格式输出。请严格按照以下步骤和规则进行分析。\n" +
                "请确保你的最终输出是一个严格合法的JSON对象，结构如下。注意返回的字符串是一个json的数组格式，且务必不要包含json字符串内容外的任意字符，保证输出的json格式正确。最终输出的内容格式样例为：\n" +
                "\n" +
                "{\n" +
                "    \"start_point\": \"山东现代学院(北门)\",\n" +
                "    \"end_point\": \"马西村委会\",\n" +
                "    \"estimated_distance\": \"21.8公里\",\n" +
                "    \"estimated_time\": \"33分钟\",\n" +
                "    \"bubble_time\": \"2025-06-10 17:15:50\",\n" +
                "    \"vehicles\": [\n" +
                "    {  \n" +
                "      \"supplier\": \"小拉快车\",\n" +
                "      \"price\": \"22.11\",\n" +
                "      \"price_type\": \"一口价\",\n" +
                "      \"discount_amount\": \"12.01\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"supplier\": \"小拉特快\",\n" +
                "      \"price\": \"25.22\",\n" +
                "      \"price_type\": \"一口价\",\n" +
                "      \"discount_amount\": \"11.02\"\n" +
                "    },\n" +
                "    {\n" +
                "        \"supplier\": \"顺风车\",\n" +
                "        \"price\": \"34.1\",\n" +
                "        \"price_type\": \"预估价\",\n" +
                "        \"discount_amount\": \"8.52\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "\n" +
                "分析步骤与规则\n" +
                "\n" +
                "第一步：分析全局行程信息 (生成 json对象的关键字段)\n" +
                "\n" +
                "如果图片包含地图视图，请从地图区域提取以下全局信息。如果图片不含地图，则将对应值留空字符串 \"\"。\n" +
                "estimated_distance (预估里程)和estimated_time (预估时间):注意仅在有地图的图片上识别这两个字段。预估里程和预估时长一般在同一行，比如「全程12.3公里，约15分钟」。此时提取「12.3公里」作为预估里程，「15分钟」作为预估时长，注意都需要以数字作为开头，不要包含其他前缀；\n" +
                "bubble_time (截图时间): 图片中会包含一个时间悬浮窗，格式为 YYYY-MM-DD HH:MM:SS，识别出来放到这个字段中。\n" +
                "\n" +
                "start_point : 路线的起点，请严格遵守如下判定逻辑：\n" +
                "1、位置：地图中存在一个红色的圆圈，红色圆圈\"正上方\"的文本框内容即为起点；\n" +
                "2、规则：请忽略地图页面中的其他信息，如时间、速度、道路编号（如S02、S81）、高速名称、城市名（如XX市）、日期、运营商信号等；如果起点识别为城市名称：例如\"XX市\"，请忽略掉\"XX市\"，并重新进行起点的识别；如果未识别成功，则填写为\"\"；\n" +
                "3、注意事项：不要把起终点信息搞反\n" +
                "\n" +
                "end_point：路线的终点，请严格遵守如下判定逻辑：\n" +
                "1、位置：在绿色的路线上箭头指向的方向尽头存在一个蓝色的圆圈，蓝色圆圈上方包含\"预估里程\"和\"预估时长\"以及\"终点\"信息，需要提取其中的终点；\n" +
                "2、规则：请忽略地图页面中的其他信息，如时间、速度、道路编号（如S02、S81）、高速名称、城市名（如XX市）、日期、运营商信号等；如果终点识别为城市名称：例如\"XX市\"，请忽略掉\"XX市\"，并重新进行终点的识别；如果未识别成功，则填写为\"\"；\n" +
                "3、注意事项：不要把起终点信息搞反\n" +
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
                "提取该行项目中最主要的、大号加粗的黑色字体。这代表了供应商或服务类型。供应商一般会在同一行包含其价格信息。\n" +
                "重要：最重要的供应商主要有「小拉快车」、「小拉特快」、「顺风车」。\n" +
                "\n" +
                "price (价格):\n" +
                "提取供应商名称右侧的大号加粗价格数字。如 34.1元，提取 34.1。\n" +
                "\n" +
                "price_type (价格类型):\n" +
                "检查价格附近是否有\"预估\"、\"约\"等字样。如果有，则为 \"预估价\"。\n" +
                "如果价格附近有\"一口价\"字样，或没有任何标识（对于固定价格的服务），则默认为 \"一口价\"。\n" +
                "\n" +
                "discount_amount (优惠金额):\n" +
                "一个供应商价格下面会紧跟着优惠价格（也可能没有），优惠金额提取其中的数字，比如 立减12.3元，提取12.3.如果没有优惠金额，则记录 0。\n" +
                "\n" +
                "\n" +
                "请特别注意：\n" +
                "完整性: 确保提取了列表中的所有可选项，不要遗漏任何一个。\n" +
                "输出结构：只需要输出json。";
    }
}

