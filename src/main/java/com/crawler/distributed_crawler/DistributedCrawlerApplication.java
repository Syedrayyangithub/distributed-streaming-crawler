package com.crawler.distributed_crawler;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.util.Arrays;
import java.util.List;

@SpringBootApplication
public class DistributedCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedCrawlerApplication.class, args);
    }

    @Bean
    public CommandLineRunner testRun(CrawlerEngine crawlerEngine) {
        return args -> {
            // Broadening the discipline by introducing news, geography, tech, and encyclopedia portals
            List<String> seedTargets = Arrays.asList(
            	"https://www.google.com",
                "https://en.wikipedia.org/wiki/Main_Page",
                "https://en.wikipedia.org/wiki/India", // Explicit target seed to capture geographic vocabulary
                "https://www.bbc.com/news",
                "https://www.geeksforgeeks.org/",
                "https://en.wikipedia.org/wiki/Early_rising",
                "https://www.bbc.com/news" // your other seeds...
            );

            // Crank the processing capacity up to 1000 pages!
            // This gives your 10 threads room to recursively branch across domains
            crawlerEngine.startCrawl(seedTargets, 1000);
        };
    }
}