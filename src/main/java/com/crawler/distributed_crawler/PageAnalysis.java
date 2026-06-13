package com.crawler.distributed_crawler;

import java.util.Map;

public class PageAnalysis {
    private final String url;
    private final String title;
    private final int totalWordCount;
    private final Map<String, Integer> topKeywords;

    public PageAnalysis(String url, String title, int totalWordCount, Map<String, Integer> topKeywords) {
        this.url = url;
        this.title = title;
        this.totalWordCount = totalWordCount;
        this.topKeywords = topKeywords;
    }

    // Getters
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public int getTotalWordCount() { return totalWordCount; }
    public Map<String, Integer> getTopKeywords() { return topKeywords; }
}