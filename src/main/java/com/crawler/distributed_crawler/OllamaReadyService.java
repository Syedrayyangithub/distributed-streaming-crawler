package com.crawler.distributed_crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class OllamaReadyService {

    private static final List<String> REQUIRED_MODELS = List.of("all-minilm", "llama3.2:1b");

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaReadyService(@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
                              ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean waitUntilReady(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (isReady()) {
                System.out.println("✅ Ollama is reachable and required models are available.");
                return true;
            }
            System.out.println("⏳ Waiting for Ollama models " + REQUIRED_MODELS + " at " + baseUrl + " ...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        System.err.println("❌ Timed out waiting for Ollama. Pull models, then call GET /api/crawl");
        return false;
    }

    public boolean isReady() {
        return missingModels().isEmpty();
    }

    public List<String> missingModels() {
        List<String> missing = new ArrayList<>(REQUIRED_MODELS);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return missing;
            }
            JsonNode models = objectMapper.readTree(response.body()).path("models");
            if (!models.isArray()) {
                return missing;
            }
            List<String> installed = new ArrayList<>();
            for (JsonNode model : models) {
                String name = model.path("name").asText("");
                if (!name.isBlank()) {
                    installed.add(name.toLowerCase());
                }
            }
            missing.removeIf(required -> installed.stream().anyMatch(name -> name.contains(required.toLowerCase())));
            return missing;
        } catch (Exception e) {
            return missing;
        }
    }
}
