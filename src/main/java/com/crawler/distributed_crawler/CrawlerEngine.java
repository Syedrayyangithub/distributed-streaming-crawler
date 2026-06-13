package com.crawler.distributed_crawler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class CrawlerEngine {

    private final ScraperService scraperService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    
    // Custom monitoring tracker for our system analytics dashboard
    private final Counter pagesScrapedCounter;
    
    private static final String REDIS_SET_KEY = "crawler:visited:urls";

    public CrawlerEngine(ScraperService scraperService, 
                         RedisTemplate<String, String> redisTemplate, 
                         ApplicationEventPublisher eventPublisher,
                         MeterRegistry meterRegistry) { // Injecting Spring's monitoring registry
        this.scraperService = scraperService;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
        
        // Registering our custom metric identifier
        this.pagesScrapedCounter = Counter.builder("crawler.pages.scraped")
                .description("Total number of web pages processed by this crawler cluster")
                .register(meterRegistry);
    }

    public void startCrawl(String startUrl, int maxPages) {
        System.out.println("\n🚀 Initializing Distributed Redis-Backed Streaming Engine...");
        redisTemplate.delete(REDIS_SET_KEY);

        ExecutorService executor = Executors.newFixedThreadPool(10);

        redisTemplate.opsForSet().add(REDIS_SET_KEY, startUrl);
        submitCrawlTask(startUrl, executor, maxPages);

        try {
            executor.shutdown();
            if (!executor.awaitTermination(45, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        Long totalLogged = redisTemplate.opsForSet().size(REDIS_SET_KEY);
        System.out.println("\n🏁 Engine Finished! Total unique pages saved in Redis cache: " + totalLogged);
    }

    private void submitCrawlTask(String url, ExecutorService executor, int maxPages) {
        Long currentSize = redisTemplate.opsForSet().size(REDIS_SET_KEY);
        if (currentSize != null && currentSize >= maxPages) {
            return;
        }

        executor.submit(() -> {
            System.out.println("🧵 Thread [" + Thread.currentThread().getName() + "] is fetching: " + url);
            CrawlResult result = scraperService.scrape(url);

            if (result != null) {
                System.out.println("✅ Successfully scraped: " + result.getTitle());
                
                // Increment our operational analytics monitor counter safely across threads
                pagesScrapedCounter.increment();
                
                eventPublisher.publishEvent(new CrawlerEvent(this, result));
                
                for (String discoveredLink : result.getDiscoveredLinks()) {
                    Long sizeCheck = redisTemplate.opsForSet().size(REDIS_SET_KEY);
                    if (sizeCheck != null && sizeCheck < maxPages) {
                        Long addedCount = redisTemplate.opsForSet().add(REDIS_SET_KEY, discoveredLink);
                        if (addedCount != null && addedCount > 0) {
                            submitCrawlTask(discoveredLink, executor, maxPages);
                        }
                    }
                }
            }
        });
    }
}