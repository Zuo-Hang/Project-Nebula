package com.wuxiansheng.shieldarch.marsdata.offline.image;

import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于ID的全局去重策略
 * 对应 Go 版本的 image.GlobalIDDedup
 */
@Slf4j
public class GlobalIDDedup implements DedupStrategy {

    private final boolean selectMiddle;

    public GlobalIDDedup() {
        this(true);
    }

    public GlobalIDDedup(boolean selectMiddle) {
        this.selectMiddle = selectMiddle;
    }

    @Override
    public PageDedupSummary deduplicate(List<PageDedupInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            PageDedupSummary summary = new PageDedupSummary();
            summary.setResults(Collections.emptyList());
            return summary;
        }

        // 按ID分组（使用第一个ID作为分组键）
        Map<String, List<PageDedupInput>> idGroups = new HashMap<>();
        for (PageDedupInput input : inputs) {
            if (input.getUniqueIDs() == null || input.getUniqueIDs().isEmpty()) {
                continue; // 跳过没有ID的页面
            }
            String primaryID = input.getUniqueIDs().get(0);
            idGroups.computeIfAbsent(primaryID, k -> new ArrayList<>()).add(input);
        }

        List<PageDedupResult> results = new ArrayList<>();
        int keptCount = 0;
        int removedCount = 0;

        // 处理每个ID组
        for (List<PageDedupInput> pages : idGroups.values()) {
            if (pages.size() == 1) {
                // 只有一个页面，直接保留
                PageDedupResult result = new PageDedupResult();
                result.setImagePath(pages.get(0).getImagePath());
                result.setUniqueIDs(pages.get(0).getUniqueIDs());
                result.setAction("kept");
                result.setReason("唯一页面");
                results.add(result);
                keptCount++;
            } else {
                // 多个页面，选择文件名处于最中间的那张
                PageDedupInput selectedPage = selectMiddle ? selectMiddlePage(pages) : pages.get(0);

                // 保留选中的页面
                PageDedupResult keptResult = new PageDedupResult();
                keptResult.setImagePath(selectedPage.getImagePath());
                keptResult.setUniqueIDs(selectedPage.getUniqueIDs());
                keptResult.setAction("kept");
                keptResult.setReason(String.format("中间页面(%d选1)", pages.size()));
                results.add(keptResult);
                keptCount++;

                // 移除其他页面
                for (PageDedupInput page : pages) {
                    if (!page.getImagePath().equals(selectedPage.getImagePath())) {
                        PageDedupResult removedResult = new PageDedupResult();
                        removedResult.setImagePath(page.getImagePath());
                        removedResult.setUniqueIDs(page.getUniqueIDs());
                        removedResult.setAction("removed");
                        removedResult.setReason("重复ID，保留中间页面");
                        results.add(removedResult);
                        removedCount++;
                    }
                }
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
     * 选择文件名处于最中间的页面
     */
    private PageDedupInput selectMiddlePage(List<PageDedupInput> pages) {
        if (pages.size() == 1) {
            return pages.get(0);
        }

        // 按文件名排序
        List<PageDedupInput> sorted = pages.stream()
            .sorted(Comparator.comparing(p -> new File(p.getImagePath()).getName()))
            .collect(Collectors.toList());

        // 选择中间的页面
        int middleIndex = sorted.size() / 2;
        return sorted.get(middleIndex);
    }
}

