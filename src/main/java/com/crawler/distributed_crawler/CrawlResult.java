package com.crawler.distributed_crawler;

import java.util.Set;

public class CrawlResult {
    private String url;
    private String title;
    private String rawText;
    private Set<String> discoveredLinks;

    // Explicit constructor that Eclipse can easily see
    public CrawlResult(String url, String title, String rawText, Set<String> discoveredLinks) {
        this.url = url;
        this.title = title;
        this.rawText = rawText;
        this.discoveredLinks = discoveredLinks;
    }

    // Explicit Getters
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getRawText() { return rawText; }
    public Set<String> getDiscoveredLinks() { return discoveredLinks; }

    // Explicit Setters
    public void setUrl(String url) { this.url = url; }
    public void setTitle(String title) { this.title = title; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public void setDiscoveredLinks(Set<String> discoveredLinks) { this.discoveredLinks = discoveredLinks; }
}