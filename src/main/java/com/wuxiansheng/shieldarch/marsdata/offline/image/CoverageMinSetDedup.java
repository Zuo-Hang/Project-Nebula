package com.wuxiansheng.shieldarch.marsdata.offline.image;

import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于ID列表的覆盖率最小集合去重策略
 * 对应 Go 版本的 image.CoverageMinSetDedup
 */
@Slf4j
public class CoverageMinSetDedup implements DedupStrategy {

    @Override
    public PageDedupSummary deduplicate(List<PageDedupInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            PageDedupSummary summary = new PageDedupSummary();
            summary.setResults(Collections.emptyList());
            return summary;
        }

        // 从输入中收集所有需要覆盖的ID
        Set<String> allIDs = new HashSet<>();
        for (PageDedupInput input : inputs) {
            if (input.getUniqueIDs() != null) {
                allIDs.addAll(input.getUniqueIDs());
            }
        }

        List<String> targetIDs = new ArrayList<>(allIDs);
        if (targetIDs.isEmpty()) {
            // 如果没有目标ID，返回空结果
            List<PageDedupResult> results = inputs.stream()
                .map(page -> {
                    PageDedupResult result = new PageDedupResult();
                    result.setImagePath(page.getImagePath());
                    result.setUniqueIDs(page.getUniqueIDs());
                    result.setAction("removed");
                    result.setReason("无目标ID需要覆盖");
                    return result;
                })
                .collect(Collectors.toList());
            
            PageDedupSummary summary = new PageDedupSummary();
            summary.setTotalPages(inputs.size());
            summary.setKeptPages(0);
            summary.setRemovedPages(inputs.size());
            summary.setDeduplicationRate(100.0);
            summary.setResults(results);
            return summary;
        }

        // 对页面进行排序，并提取index
        List<PageWithIndex> pagesWithIndex = inputs.stream()
            .map(page -> new PageWithIndex(page, extractImageIndex(page.getImagePath())))
            .sorted(Comparator.comparingInt(p -> p.index))
            .collect(Collectors.toList());

        // 划分窗口（index连续的一段视为一个窗口）
        List<List<PageWithIndex>> windows = divideIntoWindows(pagesWithIndex);

        // 对每个窗口进行去重，并汇总结果
        List<PageDedupInput> allKept = new ArrayList<>();
        for (List<PageWithIndex> window : windows) {
            // 汇总窗口内所有ID
            Set<String> windowIDs = new HashSet<>();
            for (PageWithIndex pw : window) {
                if (pw.page.getUniqueIDs() != null) {
                    windowIDs.addAll(pw.page.getUniqueIDs());
                }
            }

            // 只保留在目标ID集合中的ID
            List<String> windowTargetIDs = windowIDs.stream()
                .filter(targetIDs::contains)
                .collect(Collectors.toList());

            // 提取窗口内的页面
            List<PageDedupInput> windowPages = window.stream()
                .map(pw -> pw.page)
                .collect(Collectors.toList());

            // 对窗口内的图片使用贪心去重
            List<PageDedupInput> windowKept = selectMinCoverageSet(windowTargetIDs, windowPages);
            allKept.addAll(windowKept);
        }

        // 构建kept的map用于快速查找
        Set<String> keptPaths = allKept.stream()
            .map(PageDedupInput::getImagePath)
            .collect(Collectors.toSet());

        List<PageDedupResult> results = new ArrayList<>();
        int keptCount = 0;
        int removedCount = 0;

        for (PageDedupInput page : inputs) {
            if (keptPaths.contains(page.getImagePath())) {
                PageDedupResult result = new PageDedupResult();
                result.setImagePath(page.getImagePath());
                result.setUniqueIDs(page.getUniqueIDs());
                result.setAction("kept");
                result.setReason("覆盖率最小集合保留");
                results.add(result);
                keptCount++;
            } else {
                PageDedupResult result = new PageDedupResult();
                result.setImagePath(page.getImagePath());
                result.setUniqueIDs(page.getUniqueIDs());
                result.setAction("removed");
                result.setReason("覆盖率最小集合移除");
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
     * 使用贪心算法选择最小覆盖集合
     */
    private List<PageDedupInput> selectMinCoverageSet(List<String> targetIDs, List<PageDedupInput> pages) {
        if (targetIDs.isEmpty() || pages.isEmpty()) {
            return Collections.emptyList();
        }

        // 将目标ID列表转换为set以便快速查找
        Set<String> targetIDSet = new HashSet<>(targetIDs);

        // 使用贪心算法：每次选择覆盖最多未覆盖ID的页面
        Set<String> coveredIDs = new HashSet<>();
        List<PageDedupInput> selectedPages = new ArrayList<>();
        Set<String> pageSelected = new HashSet<>();

        while (coveredIDs.size() < targetIDSet.size()) {
            PageDedupInput bestPage = null;
            int bestNewCoverage = 0;

            // 找到覆盖最多未覆盖ID的页面
            for (PageDedupInput page : pages) {
                if (pageSelected.contains(page.getImagePath())) {
                    continue;
                }

                int newCoverage = 0;
                if (page.getUniqueIDs() != null) {
                    for (String id : page.getUniqueIDs()) {
                        if (targetIDSet.contains(id) && !coveredIDs.contains(id)) {
                            newCoverage++;
                        }
                    }
                }

                if (newCoverage > bestNewCoverage) {
                    bestNewCoverage = newCoverage;
                    bestPage = page;
                }
            }

            if (bestNewCoverage == 0) {
                // 没有更多页面可以覆盖新ID，跳出
                break;
            }

            // 选择该页面并更新已覆盖的ID
            selectedPages.add(bestPage);
            pageSelected.add(bestPage.getImagePath());
            if (bestPage.getUniqueIDs() != null) {
                for (String id : bestPage.getUniqueIDs()) {
                    if (targetIDSet.contains(id)) {
                        coveredIDs.add(id);
                    }
                }
            }
        }

        return selectedPages;
    }

    /**
     * 将页面按index划分为窗口
     */
    private List<List<PageWithIndex>> divideIntoWindows(List<PageWithIndex> pagesWithIndex) {
        if (pagesWithIndex.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<PageWithIndex>> windows = new ArrayList<>();
        List<PageWithIndex> currentWindow = new ArrayList<>();

        for (int i = 0; i < pagesWithIndex.size(); i++) {
            PageWithIndex pw = pagesWithIndex.get(i);
            if (currentWindow.isEmpty()) {
                // 第一个页面，开始新窗口
                currentWindow.add(pw);
            } else {
                // 检查是否连续（当前index = 上一个index + 1）
                int prevIndex = pagesWithIndex.get(i - 1).index;
                if (pw.index == prevIndex + 1) {
                    // 连续，加入当前窗口
                    currentWindow.add(pw);
                } else {
                    // 不连续，保存当前窗口，开始新窗口
                    windows.add(new ArrayList<>(currentWindow));
                    currentWindow.clear();
                    currentWindow.add(pw);
                }
            }
        }

        // 添加最后一个窗口
        if (!currentWindow.isEmpty()) {
            windows.add(currentWindow);
        }

        return windows;
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

