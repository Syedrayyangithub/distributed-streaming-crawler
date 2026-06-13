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

    // A standard User-Agent header makes our crawler look like a regular browser request
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 5000; // 5 seconds network timeout

    public CrawlResult scrape(String url) {
        try {
            // 1. Connect to the URL and download the full HTML Document
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            // 2. Extract the page title and the clean, stripped text body
            String title = doc.title();
            String rawText = doc.body().text();

            // 3. Find all outbound anchor tags (<a href="...">)
            Set<String> discoveredLinks = new HashSet<>();
            Elements links = doc.select("a[href]");

            for (Element link : links) {
                // absUrl ensures relative links like "/wiki/Java" become "https://en.wikipedia.org/wiki/Java"
                String absoluteUrl = link.attr("abs:href");
                
                // Basic validation: Only collect HTTP/HTTPS web links
                if (absoluteUrl.startsWith("http://") || absoluteUrl.startsWith("https://")) {
                    discoveredLinks.add(absoluteUrl);
                }
            }

            return new CrawlResult(url, title, rawText, discoveredLinks);

        } catch (Exception e) {
            // If a page times out, returns a 404, or blocks us, catch it safely so the system doesn't crash
            System.err.println("Error scraping URL [" + url + "]: " + e.getMessage());
            return null; 
        }
    }
}