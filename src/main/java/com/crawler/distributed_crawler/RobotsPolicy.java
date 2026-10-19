package com.crawler.distributed_crawler;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RobotsPolicy {

    static final String BOT_NAME = "CrawlrBot";
    static final String USER_AGENT = "CrawlrBot/1.0 (local educational search project)";
    static final long HOST_DELAY_MS = 500;

    private final ConcurrentHashMap<String, RobotsTxtRules> rulesByHost = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastFetchByHost = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> hostLocks = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public boolean allows(String url) {
        String host = hostOf(url);
        if (host == null) {
            return false;
        }
        RobotsTxtRules rules = rulesByHost.computeIfAbsent(host, this::loadRules);
        return rules.allows(url);
    }

    public void throttle(String url) {
        String host = hostOf(url);
        if (host == null) {
            return;
        }
        Object lock = hostLocks.computeIfAbsent(host, ignored -> new Object());
        synchronized (lock) {
            long now = System.currentTimeMillis();
            Long previous = lastFetchByHost.get(host);
            if (previous != null) {
                long waitMs = HOST_DELAY_MS - (now - previous);
                if (waitMs > 0) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            lastFetchByHost.put(host, System.currentTimeMillis());
        }
    }

    private RobotsTxtRules loadRules(String host) {
        String robotsUrl = "https://" + host + "/robots.txt";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(robotsUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return RobotsTxtRules.parse(response.body(), BOT_NAME);
            }
        } catch (Exception e) {
            System.out.println("⚠️ robots.txt unavailable for " + host + ", allowing by default: " + e.getMessage());
        }
        return RobotsTxtRules.allowAll();
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
