package com.wuxiansheng.shieldarch.marsdata.business.bsaas.poster;

import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasDriverDetail;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasInput;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasPassengerDetail;
import com.wuxiansheng.shieldarch.marsdata.io.AliResult;
import com.wuxiansheng.shieldarch.marsdata.io.OcrClient;
import com.wuxiansheng.shieldarch.marsdata.io.OcrConfig;
import com.wuxiansheng.shieldarch.marsdata.io.S3Client;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Poster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 识别结果修正Poster
 * 通过 OCR 纠正部分关键字段
 */
@Slf4j
@Component
public class RecognitionResultCorrection implements Poster {
    
    private static final String PASSENGER_CATEGORY = "passenger_order_details";
    private static final String DRIVER_CATEGORY = "driver_Income_analysis";
    private static final int RECOGNITION_BATCH_SIZE = 50;
    private static final String DEFAULT_CORRECTION_LOCAL_DIR = "./temp/ocr/correction";
    private static final String CORRECTION_LOG_PREFIX = "recognition_result_correction";
    private static final String URL_SCHEME_S3 = "s3://";
    private static final String URL_SCHEME_HTTP = "http://";
    private static final String URL_SCHEME_HTTPS = "https://";
    
    // 正则表达式模式（延迟初始化）
    private static Pattern startFeeRegex;
    private static Pattern startDisRegex;
    private static Pattern disChargeRegex;
    private static Pattern overDistanceRegex;
    private static Pattern durChargeRegex;
    private static Pattern overDurationRegex;
    private static Pattern longFeeRegex;
    private static Pattern longDistanceRegex;
    private static Pattern dynamicFeeRegex;
    private static Pattern dynamicFactorRegex;
    private static Pattern passengerPreDiscountRegex;
    private static Pattern passengerDiscountRegex;
    private static Pattern passengerPayRegex;
    private static Pattern passengerPayFeeRegex;
    private static Pattern driverIncomeRegex;
    private static Pattern driverRemunerationRegex;
    private static Pattern driverBaseIncomeRegex;
    private static Pattern driverRewardRegex;
    private static Pattern infoFeeRegex;
    private static Pattern infoFeeBeforeRegex;
    private static Pattern infoFeeAfterRegex;
    private static Pattern dispatchFeeBeforeRegex;
    private static Pattern dispatchFeeAfterRegex;
    private static Pattern takeRateRegex;
    private static Pattern securityServiceFeeRegex;
    private static Pattern productTypeRegex;
    
    static {
        initRegexPatterns();
    }
    
    @Autowired(required = false)
    private S3Client s3Client;
    
    @Autowired(required = false)
    private OcrConfig ocrConfig;
    
    @Value("${marsdata.local.ocr-result-dir:./temp/ocr}")
    private String ocrResultDir;
    
    @Override
    public Business apply(BusinessContext bctx, Business business) {
        if (!(business instanceof BSaasBusiness)) {
            return business;
        }
        
        BSaasBusiness bs = (BSaasBusiness) business;
        if (bs == null || bs.getReasonResult() == null) {
            return business;
        }
        
        // 收集需要修正的图片
        List<RecognitionImage> images = collectCorrectionImages(bs);
        if (images.isEmpty()) {
            return business;
        }
        
        if (s3Client == null) {
            log.warn("{}: s3 client not initialized", CORRECTION_LOG_PREFIX);
            return business;
        }
        
        OcrClient ocrClient = createOcrClient();
        if (ocrClient == null) {
            log.warn("{}: ocr client not initialized", CORRECTION_LOG_PREFIX);
            return business;
        }
        
        // 创建本地目录
        String localDir = getCorrectionLocalDir();
        try {
            Files.createDirectories(Paths.get(localDir));
        } catch (Exception e) {
            log.warn("{}: failed to create local dir {}: {}", CORRECTION_LOG_PREFIX, localDir, e.getMessage());
            return business;
        }
        
        // 下载图片
        Map<String, RecognitionImage> pathToImage = downloadRecognitionImages(s3Client, images, localDir, 
            bs.getInput() != null && bs.getInput().getMeta() != null ? bs.getInput().getMeta().getId() : "");
        if (pathToImage.isEmpty()) {
            return business;
        }
        
        // 批量OCR识别
        Map<String, AliResult> results = runOCRInBatches(ocrClient, pathToImage);
        if (results.isEmpty()) {
            return business;
        }
        
        // 应用修正
        applyRecognitionCorrections(bs, pathToImage, results);
        
        // 清理临时文件
        cleanupTempFiles(pathToImage.keySet());
        
        return business;
    }
    
    /**
     * 初始化正则表达式模式
     */
    private static void initRegexPatterns() {
        startFeeRegex = amountRegex(Pattern.quote("起步费"));
        startDisRegex = distanceRegex(Pattern.quote("起步费"), "公里");
        disChargeRegex = amountRegex(Pattern.quote("里程费"));
        overDistanceRegex = distanceRegex(Pattern.quote("里程费"), "公里");
        durChargeRegex = amountRegex(Pattern.quote("时长费"));
        overDurationRegex = durationRegex(Pattern.quote("时长费"), "分钟");
        longFeeRegex = amountRegex(Pattern.quote("远途费"));
        longDistanceRegex = distanceRegex(Pattern.quote("远途费"), "公里");
        dynamicFeeRegex = amountRegex(Pattern.quote("动调费"));
        dynamicFactorRegex = factorRegex(Pattern.quote("动调费"));
        passengerPreDiscountRegex = Pattern.compile("乘客(?:优惠|折扣)前金额[^\\d\\-]{0,5}([0-9]+(?:\\.[0-9]+)?)");
        passengerDiscountRegex = Pattern.compile("乘客优惠[^\\d\\-]{0,5}([0-9]+(?:\\.[0-9]+)?)");
        passengerPayRegex = amountRegex(Pattern.quote("乘客支付总金额"));
        passengerPayFeeRegex = amountRegex(Pattern.quote("乘客支付车费"));
        driverIncomeRegex = amountRegex(Pattern.quote("驾驶员收入"));
        driverRemunerationRegex = amountRegex(Pattern.quote("驾驶员劳动报酬"));
        driverBaseIncomeRegex = amountRegex(Pattern.quote("驾驶员基本收入"));
        driverRewardRegex = amountRegex(Pattern.quote("驾驶员奖励收入"));
        infoFeeRegex = amountRegex("(?:基础)?信息服务(?:费)?");
        infoFeeBeforeRegex = Pattern.compile("(?:基础)?信息服务(?:费)?[^\\n]*补贴前[^\\d]{0,5}([0-9]+(?:\\.[0-9]+)?)");
        infoFeeAfterRegex = amountRegex("(?:基础)?信息服务(?:费)?");
        dispatchFeeBeforeRegex = Pattern.compile("订单调度(?:服务)?(?:费)?[^\\n]*补贴前[^\\d]{0,5}([0-9]+(?:\\.[0-9]+)?)");
        dispatchFeeAfterRegex = amountRegex("订单调度(?:服务)?(?:费)?");
        takeRateRegex = Pattern.compile("平台抽佣比例[^\\d%]*(?:([0-9]+(?:\\.[0-9]{1,2})?)\\s*%|本单不抽成)");
        securityServiceFeeRegex = amountRegex(Pattern.quote("安全服务费"));
        productTypeRegex = Pattern.compile("([^\\x00-\\x7F]+(?:型|单|车))(?:订单)");
    }
    
    /**
     * 创建OCR客户端
     */
    private OcrClient createOcrClient() {
        if (ocrConfig == null) {
            return null;
        }
        try {
            return OcrClient.newOcrClient(ocrConfig);
        } catch (Exception e) {
            log.warn("{}: failed to create ocr client: {}", CORRECTION_LOG_PREFIX, e.getMessage());
            return null;
        }
    }
    
    /**
     * 收集需要修正的图片
     */
    private List<RecognitionImage> collectCorrectionImages(BSaasBusiness bs) {
        List<RecognitionImage> candidates = new ArrayList<>();
        Map<String, RecognitionImage> images = new HashMap<>();
        
        if (bs.getInput() == null || bs.getInput().getImages() == null) {
            return candidates;
        }
        
        for (BSaasInput.Image img : bs.getInput().getImages()) {
            String normalizedURL = normalizeImageURL(img.getUrl());
            if (normalizedURL == null || normalizedURL.isEmpty()) {
                continue;
            }
            
            boolean hasPassenger = false;
            boolean hasDriver = false;
            if (img.getTypes() != null) {
                for (String typ : img.getTypes()) {
                    if (PASSENGER_CATEGORY.equals(typ)) {
                        hasPassenger = true;
                    } else if (DRIVER_CATEGORY.equals(typ)) {
                        hasDriver = true;
                    }
                }
            }
            
            if (!hasPassenger && !hasDriver) {
                continue;
            }
            
            RecognitionImage info = images.get(normalizedURL);
            if (info == null) {
                S3Location location = parseS3Location(normalizedURL);
                if (location == null) {
                    log.warn("{}: parse image url failed url={}", CORRECTION_LOG_PREFIX, normalizedURL);
                    continue;
                }
                info = new RecognitionImage();
                info.url = normalizedURL;
                info.imageIndex = img.getIndex() != null ? img.getIndex() : 0;
                info.bucket = location.bucket;
                info.objectKey = location.key;
                info.estimateID = bs.getInput().getMeta() != null ? bs.getInput().getMeta().getId() : "";
                images.put(normalizedURL, info);
            }
            
            if (hasPassenger) {
                info.hasPassenger = true;
            }
            if (hasDriver) {
                info.hasDriver = true;
            }
        }
        
        for (RecognitionImage img : images.values()) {
            if (img.hasPassenger || img.hasDriver) {
                candidates.add(img);
            }
        }
        
        candidates.sort(Comparator.comparing(img -> img.url));
        return candidates;
    }
    
    /**
     * 下载识别图片
     */
    private Map<String, RecognitionImage> downloadRecognitionImages(S3Client s3Client, 
                                                                    List<RecognitionImage> images, 
                                                                    String localDir, 
                                                                    String estimateID) {
        Map<String, RecognitionImage> pathToImage = new HashMap<>();
        
        for (RecognitionImage img : images) {
            String localPath = Paths.get(localDir, sanitizeLocalFileName(img.objectKey, img.imageIndex)).toString();
            try {
                s3Client.downloadFile(img.bucket, img.objectKey, localPath);
                img.localPath = localPath;
                pathToImage.put(localPath, img);
            } catch (Exception e) {
                log.warn("{}: download failed estimate_id={} image_url={} bucket={} key={} err={}",
                    CORRECTION_LOG_PREFIX, estimateID, img.url, img.bucket, img.objectKey, e.getMessage());
                // 继续处理其他图片，不中断流程
            }
        }
        
        return pathToImage;
    }
    
    /**
     * 批量运行OCR
     */
    private Map<String, AliResult> runOCRInBatches(OcrClient ocrClient, Map<String, RecognitionImage> images) {
        if (images.isEmpty()) {
            return new HashMap<>();
        }
        
        List<String> paths = new ArrayList<>(images.keySet());
        paths.sort(String::compareTo);
        
        Map<String, AliResult> results = new HashMap<>();
        for (int start = 0; start < paths.size(); start += RECOGNITION_BATCH_SIZE) {
            int end = Math.min(start + RECOGNITION_BATCH_SIZE, paths.size());
            List<String> batch = paths.subList(start, end);
            
            try {
                Map<String, AliResult> batchResult = ocrClient.recognizeFilesOnce(batch);
                results.putAll(batchResult);
                
                for (String path : batch) {
                    RecognitionImage img = images.get(path);
                    if (img != null && batchResult.containsKey(path)) {
                        AliResult res = batchResult.get(path);
                        String text = res.getOcrData() != null ? 
                            String.join("\n", res.getOcrData()) : "";
                        log.info("{}: ocr result estimate_id={} image_url={} has_passenger={} has_driver={} text={}",
                            CORRECTION_LOG_PREFIX, img.estimateID, img.url, img.hasPassenger, img.hasDriver, text);
                    }
                }
            } catch (Exception e) {
                log.warn("{}: ocr batch failed start={} end={} err={}", 
                    CORRECTION_LOG_PREFIX, start, end, e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * 应用识别修正
     */
    private void applyRecognitionCorrections(BSaasBusiness bs, 
                                             Map<String, RecognitionImage> pathToImage, 
                                             Map<String, AliResult> results) {
        Map<String, List<BSaasPassengerDetail>> passengerByURL = groupPassengerDetails(
            bs.getReasonResult().getPassengerDetails());
        Map<String, List<BSaasDriverDetail>> driverByURL = groupDriverDetails(
            bs.getReasonResult().getDriverDetails());
        
        Set<String> visited = new HashSet<>();
        
        for (Map.Entry<String, AliResult> entry : results.entrySet()) {
            String path = entry.getKey();
            RecognitionImage img = pathToImage.get(path);
            if (img == null) {
                continue;
            }
            
            visited.add(path);
            String key = normalizeImageURL(img.url);
            AliResult res = entry.getValue();
            String text = res.getOcrData() != null ? String.join("\n", res.getOcrData()) : "";
            
            log.info("[OCR识别结果] estimate_id={} image_url={} has_passenger={} has_driver={} ocr_text={}",
                img.estimateID, img.url, img.hasPassenger, img.hasDriver, text);
            
            if (img.hasPassenger) {
                applyPassengerFieldRules(passengerByURL.get(key), text);
            }
            if (img.hasDriver) {
                applyDriverFieldRules(driverByURL.get(key), text);
            }
        }
        
        // 处理未访问的图片（重置字段）
        for (Map.Entry<String, RecognitionImage> entry : pathToImage.entrySet()) {
            if (visited.contains(entry.getKey())) {
                continue;
            }
            RecognitionImage img = entry.getValue();
            String key = normalizeImageURL(img.url);
            if (img.hasPassenger) {
                resetPassengerFields(passengerByURL.get(key));
            }
            if (img.hasDriver) {
                resetDriverFields(driverByURL.get(key));
            }
        }
    }
    
    /**
     * 应用乘客字段规则
     */
    private void applyPassengerFieldRules(List<BSaasPassengerDetail> details, String text) {
        if (details == null || details.isEmpty()) {
            return;
        }
        forcePassengerOrderPriceNil(details);
        for (PassengerFieldRule rule : getPassengerFieldRules()) {
            rule.apply(details, text);
        }
        applyProductTypeFallback(details, text);
    }
    
    /**
     * 重置乘客字段
     */
    private void resetPassengerFields(List<BSaasPassengerDetail> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        forcePassengerOrderPriceNil(details);
        for (PassengerFieldRule rule : getPassengerFieldRules()) {
            rule.reset(details);
        }
    }
    
    /**
     * 强制设置PassengerOrderPrice为null
     */
    private void forcePassengerOrderPriceNil(List<BSaasPassengerDetail> details) {
        for (BSaasPassengerDetail detail : details) {
            detail.setPassengerOrderPrice(null);
        }
    }
    
    /**
     * 应用产品类型兜底处理
     */
    private void applyProductTypeFallback(List<BSaasPassengerDetail> details, String text) {
        if (details == null || details.isEmpty() || text == null || text.isEmpty()) {
            return;
        }
        String productType = extractProductType(text);
        if (productType == null || productType.isEmpty()) {
            log.info("[ProductType识别] 未识别到产品类型, image_url={}", details.get(0).getImageURL());
            return;
        }
        log.info("[ProductType识别] 识别到产品类型={}, image_url={}", productType, details.get(0).getImageURL());
        for (BSaasPassengerDetail detail : details) {
            detail.setProductType(productType);
        }
    }
    
    /**
     * 应用司机字段规则
     */
    private void applyDriverFieldRules(List<BSaasDriverDetail> details, String text) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (DriverFieldRule rule : getDriverFieldRules()) {
            rule.apply(details, text);
        }
        applyTakeRateRule(details, text);
    }
    
    /**
     * 重置司机字段
     */
    private void resetDriverFields(List<BSaasDriverDetail> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (DriverFieldRule rule : getDriverFieldRules()) {
            rule.reset(details);
        }
        for (BSaasDriverDetail detail : details) {
            detail.setTakeRate(null);
            detail.setTakeRateRaw(null);
        }
    }
    
    /**
     * 应用抽成比例规则
     */
    private void applyTakeRateRule(List<BSaasDriverDetail> details, String text) {
        if (details == null || details.isEmpty()) {
            return;
        }
        TakeRateResult result = extractTakeRate(text);
        if (result != null && result.value != null) {
            for (BSaasDriverDetail detail : details) {
                detail.setTakeRate(result.value);
                detail.setTakeRateRaw(result.raw);
            }
        } else {
            for (BSaasDriverDetail detail : details) {
                detail.setTakeRate(null);
                detail.setTakeRateRaw(null);
            }
        }
    }
    
    /**
     * 获取乘客字段规则列表
     */
    private List<PassengerFieldRule> getPassengerFieldRules() {
        List<PassengerFieldRule> rules = new ArrayList<>();
        rules.add(new PassengerFieldRule("start_fee_sf", this::extractStartFee, 
            (detail, value) -> detail.setStartFeeSf(copyFloatPtr(value))));
        rules.add(new PassengerFieldRule("start_dis", this::extractStartDistance, 
            (detail, value) -> detail.setStartDis(copyFloatPtr(value))));
        rules.add(new PassengerFieldRule("dis_charge_sf", this::extractDisCharge, 
            (detail, value) -> detail.setDisChargeSf(copyFloatPtr(value))));
        rules.add(new PassengerFieldRule("over_distance", this::extractOverDistance, 
            (detail, value) -> detail.setOverDistance(copyFloatPtr(value))));
        rules.add(new PassengerFieldRule("dur_charge_sf", this::extractDurCharge, 
            (detail, value) -> detail.setDurChargeSf(copyFloatPtr(value))));
        rules.add(new PassengerFieldRule("over_duration", this::extractOverDuration, 
            (detail, value) -> detail.setOverDuration(copyFloatPtr(value))));
        rules.add(new PassengerFieldRule("long_fee_sf", this::extractLongFee, 
            (detail, value) -> detail.setLongFeeSf(copyFloatPtr(value))));
        rules.add(new PassengerFieldRule("long_dis", this::extractLongDistance, 
            (detail, value) -> detail.setLongDis(copyFloatPtr(value))));
        rules.add(new PassengerFieldRule("dynamic_fee_sf", this::extractDynamicFee, 
            (detail, value) -> detail.setDynamicFeeSf(copyFloatPtr(value))));
        rules.add(new PassengerFieldRule("dynamic_factor", this::extractDynamicFactor, 
            (detail, value) -> detail.setDynamicFactor(copyFloatPtr(value))));
        return rules;
    }
    
    /**
     * 获取司机字段规则列表
     */
    private List<DriverFieldRule> getDriverFieldRules() {
        List<DriverFieldRule> rules = new ArrayList<>();
        rules.add(new DriverFieldRule("passenger_pre_discount_pay", this::extractPassengerPreDiscountPay, 
            (detail, value) -> detail.setPassengerPreDiscountPay(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("passenger_discount", this::extractPassengerDiscount, 
            (detail, value) -> detail.setPassengerDiscount(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("passenger_pay", this::extractPassengerPay, 
            (detail, value) -> detail.setPassengerPay(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("passenger_pay_fee", this::extractPassengerPayFee, 
            (detail, value) -> detail.setPassengerPayFee(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("driver_income", this::extractDriverIncome, 
            (detail, value) -> detail.setDriverIncome(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("driver_remuneration", this::extractDriverRemuneration, 
            (detail, value) -> detail.setDriverRemuneration(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("driver_base_income", this::extractDriverBaseIncome, 
            (detail, value) -> detail.setDriverBaseIncome(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("driver_reward", this::extractDriverReward, 
            (detail, value) -> detail.setDriverReward(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("info_fee", (text) -> extractInfoFee(text), 
            (detail, value) -> detail.setInfoFee(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("info_fee_before_subsidy", this::extractInfoFeeBefore, 
            (detail, value) -> detail.setInfoFeeBeforeSubsidy(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("info_fee_after_subsidy", this::extractInfoFeeAfter, 
            (detail, value) -> detail.setInfoFeeAfterSubsidy(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("dispatch_service_fee_before", this::extractDispatchFeeBefore, 
            (detail, value) -> detail.setOrderDispatchServiceFeeBeforeSubsidy(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("dispatch_service_fee_after", (text) -> extractDispatchFeeAfter(text), 
            (detail, value) -> detail.setOrderDispatchServiceFeeAfterSubsidy(copyFloatPtr(value))));
        rules.add(new DriverFieldRule("security_service_fee", this::extractSecurityServiceFee, 
            (detail, value) -> detail.setSecurityServiceFee(copyFloatPtr(value))));
        return rules;
    }
    
    // ===================== 字段提取方法 =====================
    
    private Double extractStartFee(String text) {
        return matchFirstFloat(text, startFeeRegex);
    }
    
    private Double extractStartDistance(String text) {
        return matchFirstFloat(text, startDisRegex);
    }
    
    private Double extractDisCharge(String text) {
        return matchFirstFloat(text, disChargeRegex);
    }
    
    private Double extractOverDistance(String text) {
        return matchFirstFloat(text, overDistanceRegex);
    }
    
    private Double extractDurCharge(String text) {
        return matchFirstFloat(text, durChargeRegex);
    }
    
    private Double extractOverDuration(String text) {
        return matchFirstFloat(text, overDurationRegex);
    }
    
    private Double extractLongFee(String text) {
        return matchFirstFloat(text, longFeeRegex);
    }
    
    private Double extractLongDistance(String text) {
        return matchFirstFloat(text, longDistanceRegex);
    }
    
    private Double extractDynamicFee(String text) {
        return matchFirstFloat(text, dynamicFeeRegex);
    }
    
    private Double extractDynamicFactor(String text) {
        return matchFirstFloat(text, dynamicFactorRegex);
    }
    
    private Double extractPassengerPreDiscountPay(String text) {
        return matchFirstFloat(text, passengerPreDiscountRegex);
    }
    
    private Double extractPassengerDiscount(String text) {
        return matchFirstFloat(text, passengerDiscountRegex);
    }
    
    private Double extractPassengerPay(String text) {
        return matchFirstFloat(text, passengerPayRegex);
    }
    
    private Double extractPassengerPayFee(String text) {
        return matchFirstFloat(text, passengerPayFeeRegex);
    }
    
    private Double extractDriverIncome(String text) {
        return matchFirstFloat(text, driverIncomeRegex);
    }
    
    private Double extractDriverRemuneration(String text) {
        return matchFirstFloat(text, driverRemunerationRegex);
    }
    
    private Double extractDriverBaseIncome(String text) {
        return matchFirstFloat(text, driverBaseIncomeRegex);
    }
    
    private Double extractDriverReward(String text) {
        return matchFirstFloat(text, driverRewardRegex);
    }
    
    private Double extractInfoFee(String text) {
        return matchFloatExcludingKeywords(text, infoFeeRegex, "补贴");
    }
    
    private Double extractInfoFeeBefore(String text) {
        return matchFirstFloat(text, infoFeeBeforeRegex);
    }
    
    private Double extractInfoFeeAfter(String text) {
        return matchFirstFloat(text, infoFeeAfterRegex);
    }
    
    private Double extractDispatchFeeBefore(String text) {
        return matchFirstFloat(text, dispatchFeeBeforeRegex);
    }
    
    private Double extractDispatchFeeAfter(String text) {
        return matchFloatExcludingKeywords(text, dispatchFeeAfterRegex, "补贴前");
    }
    
    private Double extractSecurityServiceFee(String text) {
        return matchFirstFloat(text, securityServiceFeeRegex);
    }
    
    private TakeRateResult extractTakeRate(String text) {
        if (text == null || text.isEmpty() || takeRateRegex == null) {
            return null;
        }
        Matcher matcher = takeRateRegex.matcher(text);
        if (!matcher.find() || matcher.groupCount() < 1) {
            return null;
        }
        String raw = matcher.group(1);
        if (raw == null || raw.trim().isEmpty()) {
            return new TakeRateResult(0.0, "0");
        }
        raw = raw.trim();
        Double value = parseFloatValue(raw);
        if (value == null) {
            return null;
        }
        return new TakeRateResult(value, raw);
    }
    
    private String extractProductType(String text) {
        if (text == null || text.isEmpty() || productTypeRegex == null) {
            return null;
        }
        Matcher matcher = productTypeRegex.matcher(text);
        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            if (matcher.groupCount() >= 1) {
                matches.add(matcher.group(1));
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        // 取最后一个匹配
        String productType = matches.get(matches.size() - 1).trim();
        if (productType.isEmpty()) {
            return null;
        }
        // 转换：特惠单 -> 一口价
        if ("特惠单".equals(productType)) {
            productType = "一口价";
        }
        return productType;
    }
    
    // ===================== 正则匹配辅助方法 =====================
    
    private Double matchFirstFloat(String text, Pattern pattern) {
        if (text == null || text.isEmpty() || pattern == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find() || matcher.groupCount() < 1) {
            return null;
        }
        String raw = matcher.group(1);
        if (raw == null || raw.isEmpty() && matcher.groupCount() > 1) {
            raw = matcher.group(2);
        }
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return parseFloatValue(raw);
    }
    
    private Double matchFloatExcludingKeywords(String text, Pattern pattern, String... excludes) {
        if (text == null || text.isEmpty() || pattern == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (matcher.groupCount() < 1) {
                continue;
            }
            String matchText = matcher.group(0);
            boolean skip = false;
            for (String token : excludes) {
                if (token != null && !token.isEmpty() && matchText.contains(token)) {
                    skip = true;
                    break;
                }
            }
            if (skip) {
                continue;
            }
            String raw = matcher.group(1);
            if (raw == null || raw.isEmpty() && matcher.groupCount() > 1) {
                raw = matcher.group(2);
            }
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            return parseFloatValue(raw);
        }
        return null;
    }
    
    private Double parseFloatValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim();
        clean = clean.replaceAll("^元|%$", "");
        clean = clean.replace(",", "");
        clean = clean.replaceFirst("^-", ""); // 移除负号，只提取绝对值
        if (clean.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private static Pattern amountRegex(String keyword) {
        // 支持多种格式：带括号和不带括号
        String pattern1 = String.format("%s[,\\s]*[（(][^），)]*[）)][,\\s]*(-?[0-9]+(?:\\.[0-9]+)?)\\s*元", keyword);
        String pattern2 = String.format("%s[,\\s]*(-?[0-9]+(?:\\.[0-9]+)?)\\s*元(?:,|$)", keyword);
        return Pattern.compile(String.format("(?:%s|%s)", pattern1, pattern2));
    }
    
    private static Pattern distanceRegex(String keyword, String unit) {
        return Pattern.compile(String.format("%s\\s*[（(]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%s", keyword, unit));
    }
    
    private static Pattern durationRegex(String keyword, String unit) {
        return Pattern.compile(String.format("%s\\s*(?:[（(]\\s*)?([0-9]+(?:\\.[0-9]+)?)\\s*%s[）)]?", keyword, unit));
    }
    
    private static Pattern factorRegex(String keyword) {
        return Pattern.compile(String.format("%s[,\\s]*[（(]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*倍[）)]?", keyword));
    }
    
    // ===================== S3辅助方法 =====================
    
    private S3Location parseS3Location(String raw) {
        String trimmed = normalizeImageURL(raw);
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        
        if (trimmed.startsWith(URL_SCHEME_S3)) {
            return splitBucketAndKey(trimmed.substring(URL_SCHEME_S3.length()));
        } else if (trimmed.startsWith(URL_SCHEME_HTTP) || trimmed.startsWith(URL_SCHEME_HTTPS)) {
            return parseHTTPURL(trimmed);
        } else {
            int idx = trimmed.indexOf(":");
            if (idx > 0) {
                String bucket = trimmed.substring(0, idx);
                String key = trimmed.substring(idx + 1).replaceFirst("^/+", "");
                if (bucket != null && !bucket.isEmpty() && key != null && !key.isEmpty()) {
                    return new S3Location(bucket, key);
                }
            }
            return splitBucketAndKey(trimmed);
        }
    }
    
    private S3Location parseHTTPURL(String raw) {
        try {
            java.net.URL url = new java.net.URL(raw);
            String path = url.getPath().replaceFirst("^/+", "");
            String[] hostParts = url.getHost().split("\\.");
            String bucket = null;
            if (hostParts.length > 0 && !hostParts[0].startsWith("s3")) {
                bucket = hostParts[0];
            }
            if (bucket != null && !bucket.isEmpty()) {
                if (path == null || path.isEmpty()) {
                    return null;
                }
                return new S3Location(bucket, path);
            }
            return splitBucketAndKey(path);
        } catch (Exception e) {
            return null;
        }
    }
    
    private S3Location splitBucketAndKey(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim().replaceFirst("^/+", "");
        String[] parts = clean.split("/", 2);
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        return new S3Location(parts[0], parts[1]);
    }
    
    private String normalizeImageURL(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String trimmed = raw.trim();
        try {
            String decoded = URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
            if (!decoded.equals(trimmed)) {
                return decoded;
            }
        } catch (Exception e) {
            // 解码失败，使用原始URL
        }
        return trimmed;
    }
    
    private String sanitizeLocalFileName(String objectKey, int index) {
        String base = new File(objectKey).getName();
        if (base == null || base.isEmpty() || ".".equals(base) || "/".equals(base)) {
            base = String.format("image_%d.jpg", index);
        }
        return String.format("%d_%s", index, base);
    }
    
    private String getCorrectionLocalDir() {
        if (ocrResultDir != null && !ocrResultDir.isEmpty()) {
            return Paths.get(ocrResultDir, "correction").toString();
        }
        return DEFAULT_CORRECTION_LOCAL_DIR;
    }
    
    // ===================== 分组辅助方法 =====================
    
    private Map<String, List<BSaasPassengerDetail>> groupPassengerDetails(List<BSaasPassengerDetail> details) {
        Map<String, List<BSaasPassengerDetail>> result = new HashMap<>();
        if (details == null) {
            return result;
        }
        for (BSaasPassengerDetail detail : details) {
            String key = normalizeImageURL(detail.getImageURL());
            if (key == null || key.isEmpty()) {
                continue;
            }
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(detail);
        }
        return result;
    }
    
    private Map<String, List<BSaasDriverDetail>> groupDriverDetails(List<BSaasDriverDetail> details) {
        Map<String, List<BSaasDriverDetail>> result = new HashMap<>();
        if (details == null) {
            return result;
        }
        for (BSaasDriverDetail detail : details) {
            String key = normalizeImageURL(detail.getImageURL());
            if (key == null || key.isEmpty()) {
                continue;
            }
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(detail);
        }
        return result;
    }
    
    // ===================== 清理临时文件 =====================
    
    private void cleanupTempFiles(Set<String> paths) {
        for (String path : paths) {
            try {
                Files.deleteIfExists(Paths.get(path));
            } catch (Exception e) {
                log.warn("{}: failed to delete temp file {}: {}", CORRECTION_LOG_PREFIX, path, e.getMessage());
            }
        }
    }
    
    // ===================== 内部类 =====================
    
    /**
     * 识别图片信息
     */
    private static class RecognitionImage {
        String url;
        int imageIndex;
        String bucket;
        String objectKey;
        String localPath;
        boolean hasPassenger;
        boolean hasDriver;
        String estimateID;
    }
    
    /**
     * S3位置信息
     */
    private static class S3Location {
        String bucket;
        String key;
        
        S3Location(String bucket, String key) {
            this.bucket = bucket;
            this.key = key;
        }
    }
    
    /**
     * 抽成比例结果
     */
    private static class TakeRateResult {
        Double value;
        String raw;
        
        TakeRateResult(Double value, String raw) {
            this.value = value;
            this.raw = raw;
        }
    }
    
    /**
     * 乘客字段规则
     */
    private static class PassengerFieldRule {
        String name;
        java.util.function.Function<String, Double> extract;
        java.util.function.BiConsumer<BSaasPassengerDetail, Double> set;
        
        PassengerFieldRule(String name, 
                          java.util.function.Function<String, Double> extract,
                          java.util.function.BiConsumer<BSaasPassengerDetail, Double> set) {
            this.name = name;
            this.extract = extract;
            this.set = set;
        }
        
        void apply(List<BSaasPassengerDetail> details, String text) {
            if (details == null || details.isEmpty() || extract == null || set == null) {
                return;
            }
            Double val = extract.apply(text);
            for (BSaasPassengerDetail detail : details) {
                set.accept(detail, val);
            }
        }
        
        void reset(List<BSaasPassengerDetail> details) {
            if (details == null || details.isEmpty() || set == null) {
                return;
            }
            for (BSaasPassengerDetail detail : details) {
                set.accept(detail, null);
            }
        }
    }
    
    /**
     * 司机字段规则
     */
    private static class DriverFieldRule {
        String name;
        java.util.function.Function<String, Double> extract;
        java.util.function.BiConsumer<BSaasDriverDetail, Double> set;
        
        DriverFieldRule(String name,
                       java.util.function.Function<String, Double> extract,
                       java.util.function.BiConsumer<BSaasDriverDetail, Double> set) {
            this.name = name;
            this.extract = extract;
            this.set = set;
        }
        
        void apply(List<BSaasDriverDetail> details, String text) {
            if (details == null || details.isEmpty() || extract == null || set == null) {
                return;
            }
            Double val = extract.apply(text);
            for (BSaasDriverDetail detail : details) {
                set.accept(detail, val);
            }
        }
        
        void reset(List<BSaasDriverDetail> details) {
            if (details == null || details.isEmpty() || set == null) {
                return;
            }
            for (BSaasDriverDetail detail : details) {
                set.accept(detail, null);
            }
        }
    }
    
    /**
     * 复制浮点指针
     */
    private Double copyFloatPtr(Double src) {
        return src != null ? src : null;
    }
}

