package com.crawler.distributed_crawler;

import org.springframework.context.ApplicationEvent;

public class CrawlerEvent extends ApplicationEvent {
    
    private final CrawlResult crawlResult;

    public CrawlerEvent(Object source, CrawlResult crawlResult) {
        super(source);
        this.crawlResult = crawlResult;
    }

    public CrawlResult getCrawlResult() {
        return crawlResult;
    }
}