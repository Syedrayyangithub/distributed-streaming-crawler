package com.crawler.distributed_crawler;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class CrawlController {

    private final CrawlerEngine crawlerEngine;
    private final DocumentSearchService documentSearchService;
    private final OllamaReadyService ollamaReadyService;
    private final ExecutorService crawlExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "manual-crawler");
        thread.setDaemon(true);
        return thread;
    });

    public CrawlController(CrawlerEngine crawlerEngine,
                           DocumentSearchService documentSearchService,
                           OllamaReadyService ollamaReadyService) {
        this.crawlerEngine = crawlerEngine;
        this.documentSearchService = documentSearchService;
        this.ollamaReadyService = ollamaReadyService;
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", new Date());
        response.put("indexed_documents", documentSearchService.indexedDocumentCount());
        response.put("crawl_running", crawlerEngine.isCrawlRunning());
        response.put("ollama_ready", ollamaReadyService.isReady());
        response.put("missing_ollama_models", ollamaReadyService.missingModels());
        return response;
    }

    @RequestMapping(value = "/api/crawl", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> startCrawl(@RequestParam(value = "maxPages", defaultValue = "20") int maxPages) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", new Date());

        if (crawlerEngine.isCrawlRunning()) {
            response.put("status", "CRAWL_ALREADY_RUNNING");
            return response;
        }

        int boundedMaxPages = Math.max(1, Math.min(maxPages, 100));
        crawlExecutor.submit(() -> crawlerEngine.startDefaultCrawl(boundedMaxPages));
        response.put("status", "CRAWL_STARTED");
        response.put("max_pages", boundedMaxPages);
        response.put("message", "Crawl is running in the background. Check GET /api/status for indexed_documents.");
        return response;
    }
}
