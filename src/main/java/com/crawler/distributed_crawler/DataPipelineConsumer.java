package com.crawler.distributed_crawler;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DataPipelineConsumer {

    private final WebPageIngestService ingestService;

    public DataPipelineConsumer(WebPageIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @EventListener
    public void processIncomingScrapedData(CrawlerEvent event) {
        CrawlResult rawData = event.getCrawlResult();
        System.out.println("\n📡 [Pipeline Consumer] Intercepted stream payload for: " + rawData.getTitle());
        ingestService.ingest(rawData);
    }
}
