package com.crawler.distributed_crawler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.*;

@Service
public class CrawlerEngine {

    private final ScraperService scraperService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final Counter pagesScrapedCounter;
    
    private static final String REDIS_SET_KEY = "crawler:visited:urls";

    public CrawlerEngine(ScraperService scraperService, 
                         RedisTemplate<String, String> redisTemplate, 
                         ApplicationEventPublisher eventPublisher,
                         MeterRegistry meterRegistry) {
        this.scraperService = scraperService;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
        
        this.pagesScrapedCounter = Counter.builder("crawler.pages.scraped")
                .description("Total number of web pages processed by this crawler cluster")
                .register(meterRegistry);
    }

    public void startCrawl(List<String> seedUrls, int maxPages) {
        System.out.println("\n🚀 Initializing Distributed Multi-Domain Ingestion Spider...");
        // Clear old cache runs so we can start fresh
        redisTemplate.delete(REDIS_SET_KEY);

        // Standard thread pool for concurrent scraping
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // Inject multiple distinct starting points into our shared brain
        for (String url : seedUrls) {
            redisTemplate.opsForSet().add(REDIS_SET_KEY, url);
            submitCrawlTask(url, executor, maxPages);
        }

        try {
            executor.shutdown();
            // Let it run for up to 60 seconds to gather a wide index
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        Long totalLogged = redisTemplate.opsForSet().size(REDIS_SET_KEY);
        System.out.println("\n🏁 Spider Run Complete! Total diverse sites indexed in Redis: " + totalLogged);
    }

    private void submitCrawlTask(String url, ExecutorService executor, int maxPages) {
        Long currentSize = redisTemplate.opsForSet().size(REDIS_SET_KEY);
        if (currentSize != null && currentSize >= maxPages) {
            return;
        }

        executor.submit(() -> {
            // Clean up the URL format slightly for safety
            if (!url.startsWith("http://") && !url.startsWith("https://")) return;

            CrawlResult result = scraperService.scrape(url);

            if (result != null) {
                pagesScrapedCounter.increment();
                eventPublisher.publishEvent(new CrawlerEvent(this, result));
                
                // Track how many branches we spawn from this single page context
                int linksProcessedFromPage = 0;
                
                for (String discoveredLink : result.getDiscoveredLinks()) {
                    Long sizeCheck = redisTemplate.opsForSet().size(REDIS_SET_KEY);
                    if (sizeCheck != null && sizeCheck >= maxPages) {
                        break;
                    }
                    
                    // Guardrail 1: Restrict to top 8 outbound URLs per page to prevent a thread avalanche
                    if (linksProcessedFromPage >= 8) {
                        break;
                    }

                    // Guardrail 2: Filter out noisy social hooks and structural loops
                    String lowerLink = discoveredLink.toLowerCase();
                    if (lowerLink.contains("facebook.com") || lowerLink.contains("twitter.com") || 
                        lowerLink.contains("linkedin.com") || lowerLink.contains("login") || 
                        lowerLink.contains("signup") || lowerLink.contains("auth")) {
                        continue;
                    }
                    
                    // Check centralized Redis memory to prevent visiting duplicates across the cluster
                    Long addedCount = redisTemplate.opsForSet().add(REDIS_SET_KEY, discoveredLink);
                    if (addedCount != null && addedCount > 0) {
                        linksProcessedFromPage++;
                        // Asynchronously hand the fresh discovery to our task loop pool
                        submitCrawlTask(discoveredLink, executor, maxPages);
                    }
                }
            }
        });
    }
}