package com.crawler.distributed_crawler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CrawlerEngine {

    public static final List<String> DEFAULT_SEEDS = List.of(
            "https://en.wikipedia.org/wiki/Sleep",
            "https://en.wikipedia.org/wiki/Circadian_rhythm",
            "https://en.wikipedia.org/wiki/Sleep_hygiene",
            "https://en.wikipedia.org/wiki/Habit",
            "https://en.wikipedia.org/wiki/Alarm_clock",
            "https://simple.wikipedia.org/wiki/Sleep"
    );

    private static final String REDIS_SET_KEY = "crawler:visited:urls";

    private final ScraperService scraperService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final Counter pagesScrapedCounter;
    private final AtomicBoolean crawlRunning = new AtomicBoolean(false);

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

    public boolean isCrawlRunning() {
        return crawlRunning.get();
    }

    public void startDefaultCrawl(int maxPages) {
        startCrawl(DEFAULT_SEEDS, maxPages);
    }

    public void startCrawl(List<String> seedUrls, int maxPages) {
        if (!crawlRunning.compareAndSet(false, true)) {
            System.out.println("⚠️ A crawl is already running. Skipping overlapping start request.");
            return;
        }

        System.out.println("\n🚀 Initializing crawl for up to " + maxPages + " successfully scraped pages...");
        redisTemplate.delete(REDIS_SET_KEY);

        AtomicInteger scrapedPages = new AtomicInteger(0);
        AtomicInteger inflight = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(6);

        try {
            for (String url : seedUrls) {
                submitCrawlTask(url, executor, maxPages, scrapedPages, inflight, done);
            }

            if (inflight.get() == 0) {
                done.countDown();
            }
            if (!done.await(3, TimeUnit.MINUTES)) {
                System.err.println("⚠️ Crawl timed out after 3 minutes.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
            crawlRunning.set(false);
        }

        Long totalLogged = redisTemplate.opsForSet().size(REDIS_SET_KEY);
        System.out.println("\n🏁 Spider run complete. Scraped pages: " + scrapedPages.get()
                + ", URLs seen in Redis: " + totalLogged);
    }

    private void submitCrawlTask(String url,
                                 ExecutorService executor,
                                 int maxPages,
                                 AtomicInteger scrapedPages,
                                 AtomicInteger inflight,
                                 CountDownLatch done) {
        if (scrapedPages.get() >= maxPages || executor.isShutdown()) {
            return;
        }

        Long added = redisTemplate.opsForSet().add(REDIS_SET_KEY, url);
        if (added == null || added == 0) {
            return;
        }

        inflight.incrementAndGet();
        try {
            executor.submit(() -> {
                try {
                    crawlOne(url, executor, maxPages, scrapedPages, inflight, done);
                } finally {
                    if (inflight.decrementAndGet() == 0) {
                        done.countDown();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            if (inflight.decrementAndGet() == 0) {
                done.countDown();
            }
        }
    }

    private void crawlOne(String url,
                          ExecutorService executor,
                          int maxPages,
                          AtomicInteger scrapedPages,
                          AtomicInteger inflight,
                          CountDownLatch done) {
        if (scrapedPages.get() >= maxPages) {
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return;
        }

        CrawlResult result = scraperService.scrape(url);
        if (result == null) {
            return;
        }

        int scraped = scrapedPages.incrementAndGet();
        if (scraped > maxPages) {
            return;
        }

        pagesScrapedCounter.increment();
        eventPublisher.publishEvent(new CrawlerEvent(this, result));

        if (scraped >= maxPages) {
            return;
        }

        int linksProcessedFromPage = 0;
        for (String discoveredLink : result.getDiscoveredLinks()) {
            if (scrapedPages.get() >= maxPages || linksProcessedFromPage >= 4) {
                break;
            }

            String lowerLink = discoveredLink.toLowerCase();
            if (lowerLink.contains("facebook.com") || lowerLink.contains("twitter.com")
                    || lowerLink.contains("x.com/") || lowerLink.contains("linkedin.com")
                    || lowerLink.contains("instagram.com") || lowerLink.contains("login")
                    || lowerLink.contains("signup") || lowerLink.contains("auth")
                    || lowerLink.contains("donate") || lowerLink.contains("special:")) {
                continue;
            }
            if (!lowerLink.contains("wikipedia.org")) {
                continue;
            }

            linksProcessedFromPage++;
            submitCrawlTask(discoveredLink, executor, maxPages, scrapedPages, inflight, done);
        }
    }
}
