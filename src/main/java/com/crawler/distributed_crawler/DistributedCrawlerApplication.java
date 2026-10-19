package com.crawler.distributed_crawler;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

@SpringBootApplication
public class DistributedCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedCrawlerApplication.class, args);
    }

    @Bean
    public CommandLineRunner startupCrawl(CrawlerEngine crawlerEngine, OllamaReadyService ollamaReadyService) {
        return args -> {
            Thread startupThread = new Thread(() -> {
                boolean ollamaReady = ollamaReadyService.waitUntilReady(Duration.ofMinutes(8));
                if (!ollamaReady) {
                    System.err.println("Skipping startup crawl. After models are pulled, open GET /api/crawl");
                    return;
                }
                crawlerEngine.startDefaultCrawl(20);
            }, "startup-crawler");
            startupThread.start();
        };
    }
}
