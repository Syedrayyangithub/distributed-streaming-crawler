package com.crawler.distributed_crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class WikipediaLiveFetcher {

    private static final String API = "https://en.wikipedia.org/w/api.php";
    private static final String USER_AGENT = "CrawlrSearch/1.0 (local RAG student project)";

    private final WebPageIngestService ingestService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WikipediaLiveFetcher(WebPageIngestService ingestService, ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public int fetchAndIndex(String searchQuery, int limit) {
        try {
            List<String> titles = discoverTitles(searchQuery, limit);
            if (titles.isEmpty()) {
                return 0;
            }
            return ingestTitles(titles);
        } catch (Exception e) {
            System.err.println("⚠️ Live Wikipedia fetch failed: " + e.getMessage());
            return 0;
        }
    }

    private List<String> discoverTitles(String searchQuery, int limit) throws Exception {
        Set<String> titles = new LinkedHashSet<>();
        titles.addAll(openSearchTitles(searchQuery, limit));
        titles.addAll(fullTextSearchTitles(searchQuery, limit));
        return titles.stream().limit(limit).toList();
    }

    private List<String> openSearchTitles(String searchQuery, int limit) throws Exception {
        String url = API + "?action=opensearch&namespace=0&format=json&utf8=1&limit=" + limit
                + "&search=" + enc(searchQuery);
        JsonNode root = getJson(url);
        List<String> titles = new ArrayList<>();
        if (root.isArray() && root.size() > 1 && root.get(1).isArray()) {
            for (JsonNode title : root.get(1)) {
                if (isIndexableTitle(title.asText())) {
                    titles.add(title.asText());
                }
            }
        }
        return titles;
    }

    private List<String> fullTextSearchTitles(String searchQuery, int limit) throws Exception {
        String url = API + "?action=query&list=search&format=json&utf8=1&srlimit=" + limit
                + "&srsearch=" + enc(searchQuery);
        JsonNode root = getJson(url);
        List<String> titles = new ArrayList<>();
        for (JsonNode hit : root.path("query").path("search")) {
            String title = hit.path("title").asText();
            if (isIndexableTitle(title)) {
                titles.add(title);
            }
        }
        return titles;
    }

    private int ingestTitles(List<String> titles) throws Exception {
        int saved = 0;
        for (String requestedTitle : titles) {
            String url = API + "?action=query&prop=" + enc("extracts|info")
                    + "&explaintext=1&exlimit=1&inprop=url&redirects=1&format=json&utf8=1&titles="
                    + enc(requestedTitle);
            JsonNode pages = getJson(url).path("query").path("pages");
            var fields = pages.fields();
            while (fields.hasNext()) {
                JsonNode page = fields.next().getValue();
                String title = page.path("title").asText("");
                String extract = page.path("extract").asText("");
                String canonical = page.path("fullurl").asText("");
                if (canonical.isBlank() && !title.isBlank()) {
                    canonical = "https://en.wikipedia.org/wiki/" + title.replace(' ', '_');
                }
                if (extract.isBlank() || canonical.isBlank() || !isIndexableTitle(title)) {
                    continue;
                }
                if (ingestService.ingest(canonical, title + " - Wikipedia", extract, false) != null) {
                    saved++;
                }
            }
        }
        return saved;
    }

    private boolean isIndexableTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        return !lower.startsWith("category:")
                && !lower.startsWith("template:")
                && !lower.startsWith("file:")
                && !lower.startsWith("wikipedia:")
                && !lower.startsWith("help:")
                && !lower.startsWith("portal:")
                && !lower.contains("disambiguation");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Wikipedia API HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }
}
