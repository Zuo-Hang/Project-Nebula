package com.wuxiansheng.shieldarch.marsdata.business.bsaas.poster;

import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasDriverDetail;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasOrderListItem;
import com.wuxiansheng.shieldarch.marsdata.business.bsaas.BSaasPassengerDetail;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Poster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 页面关系匹配Poster
 * 
 * 匹配流程关注金额、时间与页面索引。具体规则：
 * 1. 订单 ↔ 司机收入：金额相等（订单金额≈驾驶员收入−安全服务费，容差0.01）、图片索引接近（差值≤100）
 * 2. 司机 ↔ 乘客明细：PassengerPay严格相等、图片索引接近（相对司机页面的index差值≤50）
 */
@Slf4j
@Component
public class PageRelationMatch implements Poster {
    
    private static final double AMOUNT_EPSILON = 0.01;
    private static final int DRIVER_INDEX_DIFF_THRESHOLD = 100;
    private static final int PASSENGER_INDEX_DIFF_THRESHOLD = 50;
    private static final double FLOAT_EPSILON = 1e-9;
    
    @Override
    public Business apply(BusinessContext bctx, Business business) {
        if (!(business instanceof BSaasBusiness)) {
            return business;
        }
        
        BSaasBusiness bs = (BSaasBusiness) business;
        if (bs.getReasonResult() == null || bs.getReasonResult().getOrderList() == null || 
            bs.getReasonResult().getOrderList().isEmpty()) {
            return business;
        }
        
        // 1. 统一按照图片索引升序排序
        sortOrders(bs);
        sortDrivers(bs);
        sortPassengers(bs);
        
        String estimateID = bs.getInput() != null && bs.getInput().getMeta() != null 
            ? bs.getInput().getMeta().getId() : "";
        
        // 2. 遍历订单，依次匹配司机与乘客明细
        for (BSaasOrderListItem order : bs.getReasonResult().getOrderList()) {
            // 2.1 匹配司机
            if (order.getDriverDetailRef() == null) {
                BSaasDriverDetail driver = findBestDriver(order, bs.getReasonResult().getDriverDetails());
                if (driver != null) {
                    order.setDriverDetailRef(driver);
                } else {
                    log.warn("page_relation_match: no driver match; estimate_id={} order_id={} order_index={} order_amount={}",
                        estimateID, order.getOrderID(), order.getImageIndex(), order.getAmount());
                }
            }
            
            // 2.2 匹配乘客（基于已匹配的司机页面）
            if (order.getDriverDetailRef() != null && order.getPassengerDetailRef() == null) {
                BSaasPassengerDetail passenger = findBestPassenger(order, order.getDriverDetailRef(), 
                    bs.getReasonResult().getPassengerDetails());
                if (passenger != null) {
                    order.setPassengerDetailRef(passenger);
                }
            }
            
            // 2.3 若只匹配到司机未匹配到乘客，则清空司机匹配
            if (order.getDriverDetailRef() != null && order.getPassengerDetailRef() == null) {
                log.warn("page_relation_match: matched driver but no passenger; estimate_id={} order_idx={} driver_idx={}",
                    estimateID, order.getImageIndex(), order.getDriverDetailRef().getImageIndex());
                order.setDriverDetailRef(null);
            }
            
            // 2.4 记录未匹配日志
            if (order.getDriverDetailRef() == null || order.getPassengerDetailRef() == null) {
                logUnmatchedOrder(order, estimateID);
            }
        }
        
        // 3. 匹配完成后，按订单ID去重
        bs.getReasonResult().setOrderList(dedupOrdersByID(bs.getReasonResult().getOrderList(), 
            bs.getReasonResult().getDriverDetails()));
        
        // 4. 汇总统计
        logMatchSummary(bs, estimateID);
        
        return business;
    }
    
    /**
     * 排序订单
     */
    private void sortOrders(BSaasBusiness bs) {
        if (bs.getReasonResult().getOrderList() != null) {
            bs.getReasonResult().getOrderList().sort(Comparator.comparingInt(
                o -> o.getImageIndex() != null ? o.getImageIndex() : Integer.MAX_VALUE));
        }
    }
    
    /**
     * 排序司机明细
     */
    private void sortDrivers(BSaasBusiness bs) {
        if (bs.getReasonResult().getDriverDetails() != null) {
            bs.getReasonResult().getDriverDetails().sort(Comparator.comparingInt(
                d -> d.getImageIndex() != null ? d.getImageIndex() : Integer.MAX_VALUE));
        }
    }
    
    /**
     * 排序乘客明细
     */
    private void sortPassengers(BSaasBusiness bs) {
        if (bs.getReasonResult().getPassengerDetails() != null) {
            bs.getReasonResult().getPassengerDetails().sort(Comparator.comparingInt(
                p -> p.getImageIndex() != null ? p.getImageIndex() : Integer.MAX_VALUE));
        }
    }
    
    /**
     * 查找最佳司机明细
     */
    private BSaasDriverDetail findBestDriver(BSaasOrderListItem order, List<BSaasDriverDetail> drivers) {
        if (drivers == null || drivers.isEmpty()) {
            return null;
        }
        
        int orderIndex = order.getImageIndex() != null ? order.getImageIndex() : 0;
        int lower = orderIndex - DRIVER_INDEX_DIFF_THRESHOLD;
        int upper = orderIndex + DRIVER_INDEX_DIFF_THRESHOLD;
        double orderAmount = order.getAmount() != null ? order.getAmount() : 0.0;
        
        BSaasDriverDetail best = null;
        int bestScore = -1;
        int bestIdxDiff = Integer.MAX_VALUE;
        int bestImageIndex = -1;
        
        for (BSaasDriverDetail d : drivers) {
            int driverIndex = d.getImageIndex() != null ? d.getImageIndex() : 0;
            if (driverIndex < lower || driverIndex > upper) {
                continue;
            }
            
            // 金额匹配
            double driverAmount = driverOrderAmount(d);
            if (!amountEqual(driverAmount, orderAmount)) {
                continue;
            }
            
            int score = driverInfoScore(d);
            int idxDiff = Math.abs(driverIndex - orderIndex);
            
            // 优先选择信息更丰富的；如果信息一样多，选择index差值最小的
            if (score > bestScore) {
                bestScore = score;
                best = d;
                bestIdxDiff = idxDiff;
                bestImageIndex = driverIndex;
            } else if (score == bestScore) {
                if (idxDiff < bestIdxDiff) {
                    best = d;
                    bestIdxDiff = idxDiff;
                    bestImageIndex = driverIndex;
                } else if (idxDiff == bestIdxDiff && driverIndex > bestImageIndex) {
                    best = d;
                    bestIdxDiff = idxDiff;
                    bestImageIndex = driverIndex;
                }
            }
        }
        
        return best;
    }
    
    /**
     * 查找最佳乘客明细
     */
    private BSaasPassengerDetail findBestPassenger(BSaasOrderListItem order, BSaasDriverDetail driver, 
                                                   List<BSaasPassengerDetail> passengers) {
        if (passengers == null || passengers.isEmpty()) {
            return null;
        }
        
        int driverIndex = driver.getImageIndex() != null ? driver.getImageIndex() : 0;
        int lower = driverIndex - PASSENGER_INDEX_DIFF_THRESHOLD;
        int upper = driverIndex + PASSENGER_INDEX_DIFF_THRESHOLD;
        double driverPassengerPay = floatValue(driver.getPassengerPay());
        
        BSaasPassengerDetail best = null;
        int bestScore = -1;
        int bestIdxDiff = Integer.MAX_VALUE;
        boolean bestSamePage = true;
        
        for (BSaasPassengerDetail p : passengers) {
            int passengerIndex = p.getImageIndex() != null ? p.getImageIndex() : 0;
            if (passengerIndex < lower || passengerIndex > upper) {
                continue;
            }
            
            // 严格匹配 PassengerPay 相等
            double passengerPay = floatValue(p.getPassengerPay());
            if (!amountEqual(passengerPay, driverPassengerPay)) {
                continue;
            }
            
            int score = passengerInfoScore(p);
            int idxDiff = Math.abs(passengerIndex - driverIndex);
            boolean samePage = passengerIndex == driverIndex;
            
            if (best == null) {
                best = p;
                bestScore = score;
                bestIdxDiff = idxDiff;
                bestSamePage = samePage;
                continue;
            }
            
            // 优先选择乘客页面与司机页面不同的候选
            if (samePage != bestSamePage) {
                if (bestSamePage && !samePage) {
                    best = p;
                    bestScore = score;
                    bestIdxDiff = idxDiff;
                    bestSamePage = samePage;
                }
                continue;
            }
            
            if (idxDiff < bestIdxDiff || (idxDiff == bestIdxDiff && score > bestScore)) {
                best = p;
                bestScore = score;
                bestIdxDiff = idxDiff;
                bestSamePage = samePage;
            }
        }
        
        return best;
    }
    
    /**
     * 判断金额是否在允许误差范围内相等
     */
    private boolean amountEqual(double a, double b) {
        return Math.abs(a - b) <= AMOUNT_EPSILON;
    }
    
    /**
     * 计算司机订单金额（收入-安全服务费）
     */
    private double driverOrderAmount(BSaasDriverDetail detail) {
        double amount = floatValue(detail.getDriverIncome());
        if (detail.getSecurityServiceFee() != null && detail.getSecurityServiceFee() > 0) {
            amount -= detail.getSecurityServiceFee();
        }
        return amount;
    }
    
    /**
     * 计算司机信息得分
     */
    private int driverInfoScore(BSaasDriverDetail d) {
        List<Double> values = Arrays.asList(
            d.getDriverIncome(), d.getPassengerPay(), d.getPassengerPayFee(), 
            d.getPassengerPreDiscountPay(), d.getPassengerDiscount(),
            d.getDriverRemuneration(), d.getDriverBaseIncome(), d.getInfoFee(), 
            d.getInfoFeeBeforeSubsidy(), d.getInfoFeeAfterSubsidy(),
            d.getTakeRate(), d.getDriverReward(), 
            d.getOrderDispatchServiceFeeBeforeSubsidy(), 
            d.getOrderDispatchServiceFeeAfterSubsidy(),
            d.getSecurityServiceFee()
        );
        
        int score = 0;
        for (Double v : values) {
            if (!isZeroFloat(v, FLOAT_EPSILON)) {
                score++;
            }
        }
        return score;
    }
    
    /**
     * 计算乘客信息得分
     */
    private int passengerInfoScore(BSaasPassengerDetail p) {
        List<Double> values = Arrays.asList(
            p.getPassengerPay(), p.getPassengerOrderPrice(), p.getStartFeeSf(), 
            p.getStartDis(), p.getDisChargeSf(), p.getOverDistance(),
            p.getDurChargeSf(), p.getOverDuration(), p.getLongFeeSf(), 
            p.getLongDis(), p.getDynamicFeeSf(), p.getDynamicFactor(), 
            p.getPassengerDiscount()
        );
        
        int score = 0;
        for (Double v : values) {
            if (!isZeroFloat(v, FLOAT_EPSILON)) {
                score++;
            }
        }
        return score;
    }
    
    /**
     * 按订单ID去重
     */
    private List<BSaasOrderListItem> dedupOrdersByID(List<BSaasOrderListItem> orders, 
                                                      List<BSaasDriverDetail> drivers) {
        if (orders == null || orders.isEmpty()) {
            return orders;
        }
        
        boolean hasDrivers = drivers != null && !drivers.isEmpty();
        int firstDriverIdx = hasDrivers && !drivers.isEmpty() ? 
            (drivers.get(0).getImageIndex() != null ? drivers.get(0).getImageIndex() : 0) : 0;
        int lastDriverIdx = hasDrivers && !drivers.isEmpty() ? 
            (drivers.get(drivers.size() - 1).getImageIndex() != null ? 
                drivers.get(drivers.size() - 1).getImageIndex() : 0) : 0;
        
        // 按订单ID分组
        Map<String, List<BSaasOrderListItem>> orderGroups = new HashMap<>();
        List<BSaasOrderListItem> noIdOrders = new ArrayList<>();
        
        for (BSaasOrderListItem order : orders) {
            if (order.getOrderID() == null || order.getOrderID().isEmpty()) {
                noIdOrders.add(order);
            } else {
                orderGroups.computeIfAbsent(order.getOrderID(), k -> new ArrayList<>()).add(order);
            }
        }
        
        List<BSaasOrderListItem> result = new ArrayList<>();
        
        // 处理没有订单ID的订单
        List<BSaasOrderListItem> unmatchedNoIDInside = new ArrayList<>();
        List<BSaasOrderListItem> unmatchedNoIDOutside = new ArrayList<>();
        
        for (BSaasOrderListItem order : noIdOrders) {
            if (!isUnmatched(order)) {
                result.add(order);
            } else {
                int orderIndex = order.getImageIndex() != null ? order.getImageIndex() : 0;
                if (hasDrivers && orderIndex >= firstDriverIdx && orderIndex <= lastDriverIdx) {
                    unmatchedNoIDInside.add(order);
                } else {
                    unmatchedNoIDOutside.add(order);
                }
            }
        }
        
        if (!unmatchedNoIDInside.isEmpty()) {
            result.addAll(unmatchedNoIDInside);
        } else {
            result.addAll(unmatchedNoIDOutside);
        }
        
        // 对每个订单ID组，选择最佳的一个
        for (List<BSaasOrderListItem> group : orderGroups.values()) {
            if (group.size() == 1) {
                result.add(group.get(0));
                continue;
            }
            
            BSaasOrderListItem best = group.get(0);
            int bestMatchScore = orderMatchScore(best);
            boolean preferInside = hasUnmatchedInside(group, hasDrivers, firstDriverIdx, lastDriverIdx);
            boolean bestInsidePreference = preferInside && isInsidePreferred(best, hasDrivers, firstDriverIdx, lastDriverIdx);
            
            for (int i = 1; i < group.size(); i++) {
                BSaasOrderListItem order = group.get(i);
                int matchScore = orderMatchScore(order);
                boolean candidateInsidePreference = preferInside && isInsidePreferred(order, hasDrivers, firstDriverIdx, lastDriverIdx);
                
                if (matchScore > bestMatchScore) {
                    best = order;
                    bestMatchScore = matchScore;
                    bestInsidePreference = candidateInsidePreference;
                    continue;
                }
                
                if (matchScore == bestMatchScore) {
                    if (candidateInsidePreference && !bestInsidePreference) {
                        best = order;
                        bestInsidePreference = true;
                        continue;
                    }
                    if (bestInsidePreference != candidateInsidePreference) {
                        continue;
                    }
                    
                    int bestIdxDiff = orderIndexDiff(best);
                    int orderIdxDiff = orderIndexDiff(order);
                    
                    if (orderIdxDiff < bestIdxDiff) {
                        best = order;
                    } else if (orderIdxDiff == bestIdxDiff) {
                        int bestIndex = best.getImageIndex() != null ? best.getImageIndex() : Integer.MAX_VALUE;
                        int orderIndex = order.getImageIndex() != null ? order.getImageIndex() : Integer.MAX_VALUE;
                        if (orderIndex < bestIndex) {
                            best = order;
                        }
                    }
                }
            }
            
            result.add(best);
        }
        
        return result;
    }
    
    /**
     * 判断订单是否未匹配
     */
    private boolean isUnmatched(BSaasOrderListItem order) {
        return order.getDriverDetailRef() == null && order.getPassengerDetailRef() == null;
    }
    
    /**
     * 判断组内是否有未匹配的订单在司机范围内
     */
    private boolean hasUnmatchedInside(List<BSaasOrderListItem> group, boolean hasDrivers, 
                                      int firstDriverIdx, int lastDriverIdx) {
        if (!hasDrivers) {
            return false;
        }
        return group.stream().anyMatch(order -> isInsidePreferred(order, hasDrivers, firstDriverIdx, lastDriverIdx));
    }
    
    /**
     * 判断订单是否在司机范围内且未匹配
     */
    private boolean isInsidePreferred(BSaasOrderListItem order, boolean hasDrivers, 
                                     int firstDriverIdx, int lastDriverIdx) {
        if (!hasDrivers || !isUnmatched(order)) {
            return false;
        }
        int orderIndex = order.getImageIndex() != null ? order.getImageIndex() : 0;
        return orderIndex >= firstDriverIdx && orderIndex <= lastDriverIdx;
    }
    
    /**
     * 计算订单的匹配信息得分
     */
    private int orderMatchScore(BSaasOrderListItem order) {
        boolean hasDriver = order.getDriverDetailRef() != null;
        boolean hasPassenger = order.getPassengerDetailRef() != null;
        
        if (hasDriver && hasPassenger) {
            return 3;
        }
        if (hasDriver) {
            return 2;
        }
        if (hasPassenger) {
            return 1;
        }
        return 0;
    }
    
    /**
     * 计算订单index和司机index的差值
     */
    private int orderIndexDiff(BSaasOrderListItem order) {
        if (order.getDriverDetailRef() == null) {
            return Integer.MAX_VALUE;
        }
        int orderIndex = order.getImageIndex() != null ? order.getImageIndex() : 0;
        int driverIndex = order.getDriverDetailRef().getImageIndex() != null ? 
            order.getDriverDetailRef().getImageIndex() : 0;
        return Math.abs(orderIndex - driverIndex);
    }
    
    /**
     * 记录未匹配订单日志
     */
    private void logUnmatchedOrder(BSaasOrderListItem order, String estimateID) {
        if (order == null) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (order.getDriverDetailRef() == null) {
            missing.add("driver");
        }
        if (order.getPassengerDetailRef() == null) {
            missing.add("passenger");
        }
        if (missing.isEmpty()) {
            return;
        }
        log.warn("page_relation_match: estimate_id={} order_id={} missing={} image_index={} amount={}",
            estimateID, order.getOrderID(), String.join(",", missing), 
            order.getImageIndex(), order.getAmount());
    }
    
    /**
     * 打印匹配结果汇总统计
     */
    private void logMatchSummary(BSaasBusiness bs, String estimateID) {
        if (bs == null || bs.getReasonResult() == null || 
            bs.getReasonResult().getOrderList() == null) {
            return;
        }
        
        int totalOrders = bs.getReasonResult().getOrderList().size();
        if (totalOrders == 0) {
            return;
        }
        
        int matchedBothCount = 0;
        for (BSaasOrderListItem order : bs.getReasonResult().getOrderList()) {
            if (order.getDriverDetailRef() != null && order.getPassengerDetailRef() != null) {
                matchedBothCount++;
            }
        }
        
        double matchedRatio = totalOrders > 0 ? (double) matchedBothCount / totalOrders * 100 : 0;
        
        log.info("page_relation_match_summary: estimate_id={} total_orders={} matched_both={} matched_ratio={:.2f}%",
            estimateID, totalOrders, matchedBothCount, matchedRatio);
    }
    
    /**
     * 获取浮点数值（null返回0）
     */
    private double floatValue(Double v) {
        return v != null ? v : 0.0;
    }
    
    /**
     * 判断浮点数是否为零
     */
    private boolean isZeroFloat(Double v, double epsilon) {
        if (v == null) {
            return true;
        }
        if (epsilon <= 0) {
            epsilon = 1e-9;
        }
        return Math.abs(v) <= epsilon;
    }
}

