package com.wuxiansheng.shieldarch.orchestrator.orchestrator.prompt.fewshot;

import java.util.List;

/**
 * 示例选择器接口
 * 
 * 用于从候选示例中选择最合适的Few-shot示例
 * 
 * 参考：LangChain ExampleSelector
 */
public interface ExampleSelector {
    
    /**
     * 选择示例
     * 
     * @param query 查询文本（用于相似度匹配）
     * @param topK 返回的示例数量
     * @return 选中的示例列表
     */
    List<Example> selectExamples(String query, int topK);
    
    /**
     * 添加示例到候选池
     * 
     * @param example 示例
     */
    void addExample(Example example);
    
    /**
     * 批量添加示例
     * 
     * @param examples 示例列表
     */
    default void addExamples(List<Example> examples) {
        if (examples != null) {
            for (Example example : examples) {
                addExample(example);
            }
        }
    }
    
    /**
     * 清除所有示例
     */
    void clearExamples();
    
    /**
     * Few-shot示例
     */
    @lombok.Data
    @lombok.Builder
    class Example {
        /**
         * 输入
         */
        private String input;
        
        /**
         * 输出
         */
        private String output;
        
        /**
         * 示例标签（可选）
         */
        private String label;
        
        /**
         * 示例元数据（可选）
         */
        private java.util.Map<String, Object> metadata;
        
        /**
         * 相似度分数（用于排序）
         */
        private Double similarityScore;
        
        /**
         * 转换为Prompt格式的字符串
         */
        public String toPromptString() {
            StringBuilder sb = new StringBuilder();
            sb.append("输入：").append(input).append("\n");
            sb.append("输出：").append(output);
            if (label != null) {
                sb.append("\n标签：").append(label);
            }
            return sb.toString();
        }
    }
}
