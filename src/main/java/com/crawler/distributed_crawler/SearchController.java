package com.crawler.distributed_crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class SearchController {

    private final DocumentSearchService documentSearchService;
    private final AiSynthesisService aiSynthesisService;
    private final ObjectMapper objectMapper;
    private final ExecutorService overviewExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "ai-overview");
        thread.setDaemon(true);
        return thread;
    });

    public SearchController(DocumentSearchService documentSearchService,
                            AiSynthesisService aiSynthesisService,
                            ObjectMapper objectMapper) {
        this.documentSearchService = documentSearchService;
        this.aiSynthesisService = aiSynthesisService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/search")
    public Map<String, Object> executeSearch(@RequestParam(value = "q", defaultValue = "") String query) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", new Date());
        response.put("query", query);

        if (query.isBlank()) {
            response.put("status", "EMPTY_QUERY");
            return response;
        }

        long started = System.currentTimeMillis();
        String fullUserPrompt = query.trim();
        List<WebPageDocument> matchedDocs = documentSearchService.findOrFetchRelevantDocuments(fullUserPrompt, 8);

        List<Map<String, String>> referencesList = new ArrayList<>();
        for (WebPageDocument doc : matchedDocs) {
            Map<String, String> item = new HashMap<>();
            item.put("title", doc.getTitle());
            item.put("url", doc.getUrl());
            item.put("snippet", makeSnippet(doc.getRawText(), fullUserPrompt));
            referencesList.add(item);
        }

        response.put("status", referencesList.isEmpty() ? "NO_RESULTS_FOUND" : "SUCCESS");
        response.put("results_count", referencesList.size());
        response.put("retrieval_ms", System.currentTimeMillis() - started);
        response.put("source_references", referencesList);
        return response;
    }

    @GetMapping(value = "/api/overview", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOverview(@RequestParam(value = "q", defaultValue = "") String query) {
        SseEmitter emitter = new SseEmitter(180_000L);
        overviewExecutor.execute(() -> {
            try {
                if (query == null || query.isBlank()) {
                    sendToken(emitter, "Empty query.");
                    finish(emitter);
                    return;
                }
                List<WebPageDocument> docs = documentSearchService.findRelevantDocuments(query.trim(), 2);
                aiSynthesisService.streamAnswer(query.trim(), docs, token -> sendToken(emitter, token));
                finish(emitter);
            } catch (Exception e) {
                try {
                    sendToken(emitter, "AI Processing Error: " + e.getMessage());
                    finish(emitter);
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    private void sendToken(SseEmitter emitter, String token) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("t", token == null ? "" : token));
            emitter.send(SseEmitter.event().name("token").data(payload));
        } catch (Exception e) {
            throw new IllegalStateException("SSE client closed", e);
        }
    }

    private void finish(SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event().name("done").data("ok"));
        emitter.complete();
    }

    private String makeSnippet(String text, String query) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String collapsed = text.replaceAll("\\s+", " ").trim();
        int idx = 0;
        for (String term : query.toLowerCase().split("\\s+")) {
            String cleaned = term.replaceAll("[^a-z0-9]", "");
            if (cleaned.length() < 5) {
                continue;
            }
            int found = collapsed.toLowerCase().indexOf(cleaned);
            if (found >= 0) {
                idx = Math.max(0, found - 48);
                break;
            }
        }
        int end = Math.min(collapsed.length(), idx + 190);
        String slice = collapsed.substring(idx, end);
        if (idx > 0) {
            slice = "..." + slice;
        }
        if (end < collapsed.length()) {
            slice = slice + "...";
        }
        return slice;
    }
}
