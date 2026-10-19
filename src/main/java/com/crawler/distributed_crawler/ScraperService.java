package com.crawler.distributed_crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class ScraperService {

    private static final int TIMEOUT_MS = 15000;

    private final RobotsPolicy robotsPolicy;

    public ScraperService(RobotsPolicy robotsPolicy) {
        this.robotsPolicy = robotsPolicy;
    }

    public CrawlResult scrape(String url) {
        try {
            if (!robotsPolicy.allows(url)) {
                System.out.println("🚫 robots.txt disallows " + url);
                return null;
            }
            robotsPolicy.throttle(url);

            Document doc = Jsoup.connect(url)
                    .userAgent(RobotsPolicy.USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(false)
                    .maxBodySize(2_000_000)
                    .get();

            String title = doc.title();
            String rawText = doc.body() != null ? doc.body().text() : "";

            Set<String> discoveredLinks = new HashSet<>();
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String absoluteUrl = link.attr("abs:href");
                if (absoluteUrl.startsWith("http://") || absoluteUrl.startsWith("https://")) {
                    discoveredLinks.add(absoluteUrl.split("#")[0]);
                }
            }

            return new CrawlResult(url, title, rawText, discoveredLinks);
        } catch (Exception e) {
            System.err.println("Error scraping URL [" + url + "]: " + e.getMessage());
            return null;
        }
    }
}
