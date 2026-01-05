package com.wuxiansheng.shieldarch.marsdata.offline.text;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 针对订单列表页面的ID生成策略
 * 对应 Go 版本的 text.OrderListStrategy
 */
public class OrderListStrategy implements IDStrategy {

    private static final Pattern ORDER_LIST_YEAR_MONTH_RE = Pattern.compile("(\\d{4})年(\\d{1,2})月");
    private static final Pattern ORDER_LIST_MONTH_DAY_RE = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern ORDER_LIST_TIME_AMOUNT_RE = Pattern.compile("((?:[01]?\\d|2[0-3]):[0-5]\\d)[^\\n]{0,40}?\\d+(?:\\.\\d+)?元");
    private static final Pattern DIGIT_REGEX = Pattern.compile("[0-9]+");
    private static final Pattern TIME_ONLY_REGEX = Pattern.compile("([01]?\\d|2[0-3]):[0-5]\\d");

    @Override
    public List<String> generateIDs(String ocrText, List<String> patterns) throws Exception {
        // 默认使用内置正则，不依赖外部配置
        String yearMonthCode = extractYearMonthCode(ocrText);
        if (yearMonthCode == null || yearMonthCode.isEmpty()) {
            throw new Exception("OrderListStrategy: failed to extract year-month code");
        }

        List<MonthDayTimeBlock> blocks = extractMonthDayTimeBlocks(ocrText);
        if (blocks.isEmpty()) {
            throw new Exception("OrderListStrategy: no month-day/time blocks matched");
        }

        List<String> ids = new ArrayList<>();
        for (MonthDayTimeBlock block : blocks) {
            for (String timeCode : block.timeCodes) {
                ids.add(String.join("-", yearMonthCode, block.monthDayCode, timeCode));
            }
        }

        if (ids.isEmpty()) {
            throw new Exception("OrderListStrategy: failed to build ids");
        }
        return ids;
    }

    /**
     * 提取年月代码
     */
    private String extractYearMonthCode(String text) {
        Matcher matcher = ORDER_LIST_YEAR_MONTH_RE.matcher(text);
        if (matcher.find() && matcher.groupCount() >= 2) {
            String year = normalizeYear(matcher.group(1));
            String month = normalizeTwoDigits(matcher.group(2));
            if (year != null && month != null) {
                return year + month;
            }
        }

        Matcher firstMatcher = ORDER_LIST_YEAR_MONTH_RE.matcher(text);
        String first = firstMatcher.find() ? firstMatcher.group() : null;
        if (first == null || first.isEmpty()) {
            return null;
        }

        List<String> digits = new ArrayList<>();
        Matcher digitMatcher = DIGIT_REGEX.matcher(first);
        while (digitMatcher.find()) {
            digits.add(digitMatcher.group());
        }

        if (digits.size() < 2) {
            return null;
        }

        String year = normalizeYear(digits.get(0));
        String month = normalizeTwoDigits(digits.get(1));
        if (year == null || month == null) {
            return null;
        }
        return year + month;
    }

    /**
     * 提取月日时间块
     */
    private List<MonthDayTimeBlock> extractMonthDayTimeBlocks(String text) {
        List<int[]> monthIndices = new ArrayList<>();
        Matcher monthMatcher = ORDER_LIST_MONTH_DAY_RE.matcher(text);
        while (monthMatcher.find()) {
            monthIndices.add(new int[]{monthMatcher.start(), monthMatcher.end()});
        }

        List<int[]> timeIndices = new ArrayList<>();
        Matcher timeMatcher = ORDER_LIST_TIME_AMOUNT_RE.matcher(text);
        while (timeMatcher.find()) {
            timeIndices.add(new int[]{timeMatcher.start(), timeMatcher.end()});
        }

        List<MonthDayTimeBlock> blocks = new ArrayList<>();
        int tPointer = 0;

        for (int i = 0; i < monthIndices.size(); i++) {
            int[] md = monthIndices.get(i);
            String monthDay = buildMonthDayCode(text, md);
            if (monthDay == null || monthDay.isEmpty()) {
                continue;
            }

            int nextStart = i + 1 < monthIndices.size() ? monthIndices.get(i + 1)[0] : text.length();

            MonthDayTimeBlock block = new MonthDayTimeBlock();
            block.monthDayCode = monthDay;

            while (tPointer < timeIndices.size()) {
                int[] t = timeIndices.get(tPointer);
                if (t[0] <= md[0]) {
                    tPointer++;
                    continue;
                }
                if (t[1] > nextStart) {
                    break;
                }

                String timeCode = buildTimeCode(text, t);
                if (timeCode != null && !timeCode.isEmpty()) {
                    block.timeCodes.add(timeCode);
                }
                tPointer++;
            }

            if (!block.timeCodes.isEmpty()) {
                blocks.add(block);
            }
        }

        return blocks;
    }

    /**
     * 构建月日代码
     */
    private String buildMonthDayCode(String text, int[] idx) {
        String raw = text.substring(idx[0], idx[1]);
        Matcher matcher = ORDER_LIST_MONTH_DAY_RE.matcher(raw);
        if (matcher.find() && matcher.groupCount() >= 2) {
            String month = normalizeTwoDigits(matcher.group(1));
            String day = normalizeTwoDigits(matcher.group(2));
            if (month != null && day != null) {
                return month + day;
            }
        }

        List<String> digits = new ArrayList<>();
        Matcher digitMatcher = DIGIT_REGEX.matcher(raw);
        while (digitMatcher.find()) {
            digits.add(digitMatcher.group());
        }

        if (digits.size() >= 2) {
            String month = normalizeTwoDigits(digits.get(0));
            String day = normalizeTwoDigits(digits.get(1));
            if (month != null && day != null) {
                return month + day;
            }
        }
        return null;
    }

    /**
     * 构建时间代码
     */
    private String buildTimeCode(String text, int[] idx) {
        String raw = text.substring(idx[0], idx[1]);
        Matcher matcher = ORDER_LIST_TIME_AMOUNT_RE.matcher(raw);
        if (matcher.find() && matcher.groupCount() >= 1) {
            return matcher.group(1).replace(":", "");
        }

        Matcher timeMatcher = TIME_ONLY_REGEX.matcher(raw);
        String candidate = timeMatcher.find() ? timeMatcher.group() : null;
        if (candidate == null || candidate.isEmpty()) {
            return null;
        }

        candidate = candidate.replace(":", "");
        if (candidate.length() >= 4) {
            return candidate.substring(0, 4);
        }
        if (candidate.length() == 3) {
            return "0" + candidate;
        }
        return candidate;
    }

    /**
     * 规范化年份（取后两位）
     */
    private String normalizeYear(String value) {
        value = value.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() >= 2) {
            return value.substring(value.length() - 2);
        }
        return "0" + value;
    }

    /**
     * 规范化两位数字
     */
    private String normalizeTwoDigits(String value) {
        value = value.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() >= 2) {
            return value.substring(value.length() - 2);
        }
        return "0" + value;
    }

    /**
     * 月日时间块
     */
    private static class MonthDayTimeBlock {
        String monthDayCode;
        List<String> timeCodes = new ArrayList<>();
    }
}

