//package com.crawler.distributed_crawler;
//
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.context.annotation.Bean;
//
//@SpringBootApplication
//public class DistributedCrawlerApplication {
//
//    public static void main(String[] args) {
//        SpringApplication.run(DistributedCrawlerApplication.class, args);
//    }
//
//    @Bean
//    public CommandLineRunner testRun(ScraperService scraperService) {
//        return args -> {
//            System.out.println("--- Executing Single Scraper Component Test ---");
//            
//            String targetUrl = "https://en.wikipedia.org/wiki/Main_Page";
//            CrawlResult result = scraperService.scrape(targetUrl);
//
//            if (result != null) {
//                System.out.println("Successfully Scraped!");
//                System.out.println("Title: " + result.getTitle());
//                System.out.println("Text length character count: " + result.getRawText().length());
//                System.out.println("Total unique links found on page: " + result.getDiscoveredLinks().size());
//            } else {
//                System.out.println("Scrape failed.");
//            }
//        };
//    }
//}


package com.crawler.distributed_crawler;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DistributedCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedCrawlerApplication.class, args);
    }

    @Bean
    public CommandLineRunner testRun(CrawlerEngine crawlerEngine) {
        return args -> {
            // Let's scrape Wikipedia and dynamically fan out to 20 unique pages concurrently
            String seedUrl = "https://en.wikipedia.org/wiki/Main_Page";
            int limit = 20; 
            
            crawlerEngine.startCrawl(seedUrl, limit);
        };
    }
}