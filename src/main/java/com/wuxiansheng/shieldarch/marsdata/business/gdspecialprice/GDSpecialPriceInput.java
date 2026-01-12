package com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice;

import com.wuxiansheng.shieldarch.marsdata.config.LLMConfigHelper;
import com.wuxiansheng.shieldarch.marsdata.llm.ReasonRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 高德特价车输入数据
 */
@Data
@Component
public class GDSpecialPriceInput {
    
    // 问卷ID，用于Hive表存储
    private String estimateId;
    // 冒泡图片URL列表，包含需要识别的特价车截图
    private List<String> bubbleImageUrls = new ArrayList<>();
    // 客户端时间戳（毫秒），用于消息过期检查
    private Long clientTime;
    // 城市ID
    private Integer cityId;
    // 城市名称
    private String cityName;
    
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
        return "你好！你将扮演一个专业的打车价格识别智能体。你的任务是精确地从网约车应用的截图中提取车型的价格和优惠信息，并以指定的JSON格式输出。请严格按照以下步骤和规则进行分析。\n" +
                "\n" +
                "# 输出规则：\n" +
                "1.  **你必须输出一个且只有一个合法的JSON对象。**\n" +
                "2.  **绝对不允许在JSON对象之外添加任何额外的解释、道歉、问候或说明文字。**\n" +
                "3.  输出的JSON必须完全遵循以下格式：\n" +
                "\n" +
                " {\n" +
                "     \"suppliers_info\": [\n" +
                "         {\n" +
                "             \"supplier\": \"AA出行\",\n" +
                "             \"cap_price\": \"7.3\",\n" +
                "             \"reduce_price\": \"4.81\"\n" +
                "         },\n" +
                "         {\n" +
                "             \"supplier\": \"火箭出行\",\n" +
                "             \"cap_price\": \"7.3\",\n" +
                "             \"reduce_price\": \"4.81\"\n" +
                "         },\n" +
                "         {\n" +
                "             \"supplier\": \"星徽出行\",\n" +
                "             \"cap_price\": \"7.4\",\n" +
                "             \"reduce_price\": \"4.83\"\n" +
                "         }\n" +
                "     ]\n" +
                " }\n" +
                "\n" +
                "分析步骤与规则\n" +
                "\n" +
                "第一步：确定识别范围\n" +
                "\n" +
                "此图片需要识别的只有界面的下半部分弹窗，请忽略其他信息。\n" +
                "\n" +
                "第二步：识别特价车弹窗信息 (生成 suppliers_info 数组)\n" +
                "\n" +
                "现在，请仔细扫描界面中列出的每一个可供选择的打车服务。对于每一个可选项，提取一组信息并作为一个对象添加到 vehicle 数组中。\n" +
                "\n" +
                "核心识别规则：\n" +
                "\n" +
                "识别可选项 ：可选项 (行项目) 是用户可以直接选择并看到价格的条目。它们通常包含一个图标/Logo、一个服务名称 (大号黑体字) 和一个价格。例如：\"AA出行\"、\"火箭出行\"、\"星徽出行\"、\"旗妙出行\"。\n" +
                "\n" +
                "现在，请对每个可选项，提取以下字段：\n" +
                "\n" +
                "supplier (供应商名称):\n" +
                "提取该行项目中最主要的、大号加粗的黑色字体。这代表了供应商或服务类型。\n" +
                "例如：\"AA出行\"、\"火箭出行\"、\"星徽出行\"、\"旗妙出行\"等。\n" +
                "\n" +
                "cap_price (特价):\n" +
                "提取服务名称右侧的大号加粗价格数字，这是特价车的实际价格。\n" +
                "例如：7.3元、7.4元、8.4元等。\n" +
                "注意：这是用户实际需要支付的价格。\n" +
                "\n" +
                "reduce_price (优惠金额):\n" +
                "位于价格下方，一般会有「优惠」相近的描述\n" +
                "例如，从\"特惠 已优惠4.81元\"中提取 4.81。\n" +
                "如果没有优惠金额，则记录 0。";
    }
}

