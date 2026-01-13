package com.wuxiansheng.shieldarch.marsdata.offline.text;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于正则拼接的ID生成策略
 * 使用每个正则的匹配结果，按顺序用 "-" 连接
 * 对应 Go 版本的 text.RegStrategy
 */
public class RegStrategy implements IDStrategy {

    private static final Pattern TIME_STAMP_REGEX = Pattern.compile("\\b(?:[01]?\\d|2[0-3]):[0-5]\\d\\b");

    @Override
    public List<String> generateIDs(String ocrText, List<String> patterns) throws Exception {
        if (patterns == null || patterns.isEmpty()) {
            return Collections.emptyList();
        }

        String cleanText = removeTimeStamps(ocrText);
        List<String> parts = new ArrayList<>();

        for (String pattern : patterns) {
            pattern = pattern.trim();
            if (pattern.isEmpty()) {
                continue;
            }

            try {
                Pattern p = Pattern.compile(pattern);
                Matcher m = p.matcher(cleanText);
                
                while (m.find()) {
                    String value = "";
                    if (m.groupCount() > 0) {
                        for (int i = 1; i <= m.groupCount(); i++) {
                            String group = m.group(i);
                            if (group != null && !group.trim().isEmpty()) {
                                value = group;
                                break;
                            }
                        }
                    }
                    if (value.isEmpty()) {
                        value = m.group(0);
                    }
                    parts.add(value);
                }
            } catch (Exception e) {
                throw new Exception("无效的正则表达式: " + pattern, e);
            }
        }

        if (parts.isEmpty()) {
            return Collections.emptyList();
        }

        String joined = String.join("-", parts);
        return Collections.singletonList(joined);
    }

    /**
     * 移除时间戳
     */
    private String removeTimeStamps(String text) {
        return TIME_STAMP_REGEX.matcher(text).replaceAll("");
    }
}

