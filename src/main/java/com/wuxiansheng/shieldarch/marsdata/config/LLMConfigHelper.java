package com.wuxiansheng.shieldarch.marsdata.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 配置辅助服务
 * 
 * 提供 LLM 相关的配置读取辅助方法
 */
@Slf4j
@Service
public class LLMConfigHelper {
    
    @Autowired
    private AppConfigService appConfigService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 获取LLM Prompt
     * 
     * @param businessName 业务名称
     * @param defaultVal 默认值
     * @return Prompt字符串
     */
    public String getLLMPrompt(String businessName, String defaultVal) {
        return getLLMPromptWithClassify(businessName, "", defaultVal);
    }
    
    /**
     * 获取LLM Prompt（带分类）
     * 
     * @param businessName 业务名称
     * @param category 分类
     * @param defaultVal 默认值
     * @return Prompt字符串
     */
    public String getLLMPromptWithClassify(String businessName, String category, String defaultVal) {
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        if (params.isEmpty()) {
            log.warn("invalid conf: {}, err: config is empty", AppConfigService.OCR_LLM_CONF);
            return defaultVal;
        }
        
        String key;
        if (category != null && !category.isEmpty()) {
            key = businessName + "_" + category + "_prompt";
        } else {
            key = businessName + "_prompt";
        }
        
        String valStr = params.get(key);
        if (valStr != null && !valStr.isEmpty()) {
            return valStr;
        }
        
        return defaultVal;
    }
    
    /**
     * 获取OD配置
     * 
     * @param businessName 业务名称
     * @return OD映射（OD -> 城市名）
     */
    public Map<String, String> getODs(String businessName) {
        Map<String, String> defaultVal = new HashMap<>();
        
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        if (params.isEmpty()) {
            log.warn("invalid conf: {}, err: config is empty", AppConfigService.OCR_LLM_CONF);
            return defaultVal;
        }
        
        String key = businessName + "_ods";
        String valStr = params.get(key);
        if (valStr == null || valStr.isEmpty()) {
            return defaultVal;
        }
        
        try {
            // 解析JSON
            return objectMapper.readValue(valStr, 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (Exception e) {
            log.warn("{} ods err: {}", businessName, e.getMessage());
            return defaultVal;
        }
    }
    
    /**
     * 检查供应商是否有效
     * 
     * @param supplier 供应商名称
     * @param businessName 业务名称
     * @return 是否有效
     */
    public boolean isValidSupplier(String supplier, String businessName) {
        String defaultValidSupplier = "小拉出行,小拉特选,顺风车";
        
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        if (params.isEmpty()) {
            log.error("invalid conf: {}, err: config is empty", AppConfigService.OCR_LLM_CONF);
            return stringSplitContains(defaultValidSupplier, ",", supplier);
        }
        
        String key = businessName + "_valid_supplier";
        String valStr = params.get(key);
        if (valStr == null || valStr.isEmpty()) {
            log.error("empty supplier conf: {}, business_name: {}", AppConfigService.OCR_LLM_CONF, businessName);
            valStr = defaultValidSupplier;
        }
        
        return stringSplitContains(valStr, ",", supplier);
    }
    
    /**
     * 检查高德供应商是否有效
     * 
     * @param supplier 供应商名称
     * @return 是否有效
     */
    public boolean isValidGDSupplier(String supplier) {
        String defaultGDValidSupplier = "曹操出行,添猫出行,AA出行,900出行,风韵出行,玖玖出行,光彩出行,桔子出行,J刻出行,安易出行,博度出行,甘薯出行,国泰出行,极速拼车,特价拼车,免佣联盟精选司机,远途极速拼,远途特价拼,搭顺出行,哈喽优行,蔷薇出行,飞豹出行,T3出行,前行出行,腾飞出行,捎点宝出行,熙客出行,呼我出行,云途出行,众车出行,开心出行,黄金出行,易至出行,联途出行,联友出行,顺道出行,悦行出行,羊城出行,雷利出行,欧亚出行,优e出行,星徽出行,小马出行,新动出行,悦道出行,招招出行,中交出行,有鹏出行,大雁出行,富安出行,享道出行,麦巴出行,阳光出行,鹰明出行,江南出行,叮叮出行,罗伦士出行,三合出行,华哥出行,逸乘出行,携华出行,来了出行,佰联出租,任行出租,江西出租,天津出租,深圳出租,国泰出租,南京出租,聚的新出租,优选新出租,东莞出租,大众出租,星城出租,腾飞出租,湖南的士,任行专车,神州专车,幸福专车,多彩约车,玖玖约车,有象约车,普惠约车,旅程约车,大众约车,享约车,365约车,和行约车,吉汽约车,首汽约车,双创打车,飞嘀打车,哎哟喂打车,悠搭打车,众至用车,K9用车,安安用车,恒好用车,及时用车,全在用车,滴滴快车,麒麟优车,旅程e车,吉刻上车,快来车,合易接送,有滴旅程,斑马快跑,僖行天下,小牛快跑,方舟行,麦田商旅,妥妥E行,搜谷365,好久来,智体行,国民初行,国民出行,百靓出行,易约出行,吉林出租,享道悠搭,大象出行,快客出行,旗妙出行,如一出行,沈城出租,和行神州,乐拼用车,博约出行,云滴约车,昆明打车,全民GO,宝利出租,老兵约车,三合e行,楚天出租,日初出行,飞马出行,享道出行特惠,美程出行,Uto悠途,旅程e行,蓉橙出行,陕水务出行,动力出行,e车出行,果橙打车,重庆出租,迪尔出行,易通出行,小巷约车,易达出行,鑫钜专车,途途行,金宇出租,E车出行,环旅出行,Uto悠途,雷利约车,鞍马出行,喜行约车,神骅飞鹏,福小鹿,优讯快车,中军出行,伙力出行,旅程易到,来回出行,火箭出行,哈啰出行,三秦出行,陕水务出行,全在用车,博约出行,快客出行,如嘀出行,及客出行,幸福千万家,E定行,轩轩出行,妥妥出租,打表出租车,优e出租,飞嘀-经济型,62580约车,力力出行,轻快联盟,致行约车,T3特选,顶风出行,株洲出租车,鞍马出行轻快,哈哈出行,e族出行,着急打车,旅程出租,神州专车（携华）,飞滴打车,摩登出行,沛途出行,拼客出行,启滴出行,嗒个滴,礼帽专车,昆明打车特惠,天津出行,喜行约车轻快,海汽e行,如祺出行,e路合乘,礼帽出行,优迅快车,民途出行,易骐出行,胖哒出行,携华出行轻快,燕抖出行,车马上到,金银建出行,蔚蓝出行,天虎出行,掌上行,5U出行,首约特惠,蛋卷出行,放心出行,果粒出行,蛋卷出租,大国出行,车马出行,悦来月行,快客约车,风韵出行轻快,万峰畅行,沛途行,双中出行,妥妥E行轻快,大象出行经TE,帮邦行,有序出行,交通约车,捐点宝出行,云南出行,聚优出租,橄榄绿出租,北京的士,橄榄绿出行,纷享出行,东风出行,泉州95128,怃尤出行,优e出行轻快,创业者出行,速的出行,五福出租,仟嘉出租,天府出行,凤韵出行,铁航专线,云能行,365约车轻快,Uto途悠,及时用车轻快,奇华出行,E车电驴,天府行,如滴出行,犇犇约车,客多多出行,迅达出行,北方出行,北汽出租,青岛出租,老兵打车,腾飞新出租,易来客运,一起召,轻快型,利好出行,东潮出行,黄鹤行,幸福专行,麦卡出行,众约出行,鲸志出行,有滴出行,旅程约车轻快,一口价,平价车,特惠快车,特价车,出租车";
        
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        if (params.isEmpty()) {
            log.error("invalid conf: {}, err: config is empty", AppConfigService.OCR_LLM_CONF);
            return isValidGDSupplierInternal(defaultGDValidSupplier, supplier);
        }
        
        String valStr = params.get("gd_valid_supplier");
        if (valStr == null || valStr.isEmpty()) {
            return isValidGDSupplierInternal(defaultGDValidSupplier, supplier);
        }
        
        return isValidGDSupplierInternal(valStr, supplier);
    }
    
    /**
     * 检查高德供应商是否有效（内部实现）
     */
    private boolean isValidGDSupplierInternal(String confStr, String supplier) {
        confStr = confStr.replace(" ", "");
        if (confStr.isEmpty()) {
            return false;
        }
        
        String[] parts = confStr.split(",");
        String[] suffixes = {"", "特选"};
        
        for (String part : parts) {
            for (String suffix : suffixes) {
                if ((part + suffix).equals(supplier)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 获取小拉规则ID到城市名称的映射
     * 
     * @return 规则ID到城市名称的映射
     */
    public Map<Integer, String> getXLRuleIdToCityNameMap() {
        Map<Integer, String> defaultVal = new HashMap<>();
        defaultVal.put(800, "东莞市");
        defaultVal.put(844, "成都市");
        defaultVal.put(842, "福州市");
        defaultVal.put(893, "合肥市");
        defaultVal.put(810, "威海市");
        defaultVal.put(632, "遵义市");
        
        Map<String, String> params = appConfigService.getConfig(AppConfigService.OCR_LLM_CONF);
        if (params.isEmpty()) {
            log.warn("invalid conf: {}, err: config is empty", AppConfigService.OCR_LLM_CONF);
            return defaultVal;
        }
        
        String valStr = params.get("xl_rule_id_city_name_map");
        if (valStr == null || valStr.isEmpty()) {
            return defaultVal;
        }
        
        try {
            // 解析JSON
            return objectMapper.readValue(valStr, 
                objectMapper.getTypeFactory().constructMapType(Map.class, Integer.class, String.class));
        } catch (Exception e) {
            log.warn("xl_rule_id_city_name_map err: {}", e.getMessage());
            return defaultVal;
        }
    }
    
    /**
     * 检查逗号分隔的字符串是否包含目标值
     */
    private boolean stringSplitContains(String str, String sep, String target) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        String[] parts = str.split(sep);
        for (String part : parts) {
            if ("all".equals(part) || part.equals(target)) {
                return true;
            }
        }
        return false;
    }
}

