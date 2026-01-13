package com.wuxiansheng.shieldarch.marsdata.offline.image;

import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于ID的滑动窗口去重策略
 * 对应 Go 版本的 image.SlidingWindowIDDedup
 */
@Slf4j
public class SlidingWindowIDDedup implements DedupStrategy {

    private final int windowSize;
    private final boolean selectMiddle;

    public SlidingWindowIDDedup(int windowSize) {
        this(windowSize, true);
    }

    public SlidingWindowIDDedup(int windowSize, boolean selectMiddle) {
        this.windowSize = windowSize > 0 ? windowSize : 100;
        this.selectMiddle = selectMiddle;
    }

    @Override
    public PageDedupSummary deduplicate(List<PageDedupInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            PageDedupSummary summary = new PageDedupSummary();
            summary.setResults(Collections.emptyList());
            return summary;
        }

        // 过滤掉没有ID的页面
        List<PageDedupInput> validInputs = inputs.stream()
            .filter(input -> input.getUniqueIDs() != null && !input.getUniqueIDs().isEmpty())
            .collect(Collectors.toList());

        // 执行滑动窗口去重
        List<PageDedupInput> kept = deduplicateByWindow(validInputs);

        // 构建kept的map用于快速查找
        Set<String> keptPaths = kept.stream()
            .map(PageDedupInput::getImagePath)
            .collect(Collectors.toSet());

        List<PageDedupResult> results = new ArrayList<>();
        int keptCount = 0;
        int removedCount = 0;

        for (PageDedupInput page : validInputs) {
            if (keptPaths.contains(page.getImagePath())) {
                PageDedupResult result = new PageDedupResult();
                result.setImagePath(page.getImagePath());
                result.setUniqueIDs(page.getUniqueIDs());
                result.setAction("kept");
                result.setReason("滑动窗口去重保留");
                results.add(result);
                keptCount++;
            } else {
                PageDedupResult result = new PageDedupResult();
                result.setImagePath(page.getImagePath());
                result.setUniqueIDs(page.getUniqueIDs());
                result.setAction("removed");
                result.setReason("滑动窗口去重移除");
                results.add(result);
                removedCount++;
            }
        }

        // 计算去重率
        int totalPages = inputs.size();
        double deduplicationRate = totalPages > 0 ? (double) removedCount / totalPages * 100 : 0.0;

        PageDedupSummary summary = new PageDedupSummary();
        summary.setTotalPages(totalPages);
        summary.setKeptPages(keptCount);
        summary.setRemovedPages(removedCount);
        summary.setDeduplicationRate(deduplicationRate);
        summary.setResults(results);

        return summary;
    }

    /**
     * 在滑动窗口内去重
     */
    private List<PageDedupInput> deduplicateByWindow(List<PageDedupInput> pages) {
        if (pages.isEmpty()) {
            return Collections.emptyList();
        }

        // 按图片的 index 排序
        List<PageWithIndex> pagesWithIndex = pages.stream()
            .map(page -> new PageWithIndex(page, extractImageIndex(page.getImagePath())))
            .sorted(Comparator.comparingInt(p -> p.index))
            .collect(Collectors.toList());

        List<PageDedupInput> kept = new ArrayList<>();
        // 使用map记录窗口内已出现的ID及其对应的页面
        Map<String, List<PageWithIndex>> idToPagesInWindow = new HashMap<>();

        for (PageWithIndex pw : pagesWithIndex) {
            PageDedupInput page = pw.page;
            int currentIdx = pw.index;

            if (page.getUniqueIDs() == null || page.getUniqueIDs().isEmpty()) {
                // 没有ID的页面直接保留
                kept.add(page);
                continue;
            }

            String primaryID = page.getUniqueIDs().get(0);
            boolean shouldKeep = true;

            // 计算窗口范围（基于 idx 差值）
            int windowStartIdx = Math.max(0, currentIdx - windowSize);

            // 清理窗口外的ID记录
            idToPagesInWindow.entrySet().removeIf(entry -> {
                List<PageWithIndex> idPages = entry.getValue();
                return idPages.stream().noneMatch(idPage -> 
                    idPage.index >= windowStartIdx && idPage.index < currentIdx);
            });

            // 检查当前ID是否在窗口内已存在
            if (idToPagesInWindow.containsKey(primaryID)) {
                List<PageWithIndex> existingPages = idToPagesInWindow.get(primaryID);
                if (existingPages.stream().anyMatch(ep -> 
                    ep.index >= windowStartIdx && ep.index < currentIdx)) {
                    shouldKeep = false;
                }
            }

            if (shouldKeep) {
                kept.add(page);
                idToPagesInWindow.computeIfAbsent(primaryID, k -> new ArrayList<>()).add(pw);
            }
        }

        return kept;
    }

    /**
     * 从图片路径中提取index
     */
    private int extractImageIndex(String imagePath) {
        String name = new File(imagePath).getName();
        name = name.substring(0, name.lastIndexOf('.') > 0 ? name.lastIndexOf('.') : name.length());
        
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(name);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * 带index的页面
     */
    private static class PageWithIndex {
        final PageDedupInput page;
        final int index;

        PageWithIndex(PageDedupInput page, int index) {
            this.page = page;
            this.index = index;
        }
    }
}

