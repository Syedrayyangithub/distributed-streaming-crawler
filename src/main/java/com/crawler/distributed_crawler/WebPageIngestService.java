package com.crawler.distributed_crawler;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WebPageIngestService {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "by",
            "for", "with", "about", "against", "between", "into", "through", "during",
            "before", "after", "above", "below", "from", "up", "down", "is", "are",
            "was", "were", "be", "been", "being", "have", "has", "had", "this", "that"
    ));

    private final WebPageRepository webPageRepository;
    private final EmbeddingModel embeddingModel;

    public WebPageIngestService(WebPageRepository webPageRepository, EmbeddingModel embeddingModel) {
        this.webPageRepository = webPageRepository;
        this.embeddingModel = embeddingModel;
    }

    public WebPageDocument ingest(String url, String title, String rawText) {
        return ingest(url, title, rawText, true);
    }

    public WebPageDocument ingest(String url, String title, String rawText, boolean computeEmbedding) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        String[] tokens = rawText.toLowerCase()
                .replaceAll("[^a-zA-Z\\s]", "")
                .split("\\s+");

        Map<String, Integer> wordFrequencies = new HashMap<>();
        for (String word : tokens) {
            if (word.length() > 2 && !STOP_WORDS.contains(word)) {
                wordFrequencies.put(word, wordFrequencies.getOrDefault(word, 0) + 1);
            }
        }

        WebPageDocument document = webPageRepository.findByUrl(url).orElseGet(WebPageDocument::new);
        List<Double> textVector;
        if (computeEmbedding) {
            textVector = embedSafely(rawText);
        } else if (document.getEmbedding() != null && !document.getEmbedding().isEmpty()) {
            textVector = document.getEmbedding();
        } else {
            textVector = new ArrayList<>();
        }
        document.setUrl(url);
        document.setTitle(title);
        document.setRawText(rawText);
        document.setWordFrequencies(wordFrequencies);
        document.setEmbedding(textVector);
        WebPageDocument saved = webPageRepository.save(document);
        System.out.println("💾 Persisted page: " + title + " (embedding dims: " + textVector.size() + ")");
        return saved;
    }

    public WebPageDocument ingest(CrawlResult rawData) {
        return ingest(rawData.getUrl(), rawData.getTitle(), rawData.getRawText());
    }

    private List<Double> embedSafely(String rawText) {
        try {
            String embeddingInput = rawText.length() > 1500 ? rawText.substring(0, 1500) : rawText;
            float[] embeddingResponse = embeddingModel.embed(embeddingInput);
            List<Double> textVector = new ArrayList<>();
            if (embeddingResponse != null) {
                for (float val : embeddingResponse) {
                    textVector.add((double) val);
                }
            }
            return textVector;
        } catch (Exception e) {
            System.err.println("⚠️ Embedding failed, saving text-only document: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
