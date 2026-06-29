package com.crawler.distributed_crawler;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class DataPipelineConsumer {

    private final WebPageRepository webPageRepository; // Injecting Mongo Repository
    private final EmbeddingModel embeddingModel;       // Injecting the Ollama Embedding Engine

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "by", 
        "for", "with", "about", "against", "between", "into", "through", "during", 
        "before", "after", "above", "below", "from", "up", "down", "is", "are", 
        "was", "were", "be", "been", "being", "have", "has", "had", "this", "that"
    ));

    // Updated Constructor to support native Spring AI embedding injections
    public DataPipelineConsumer(WebPageRepository webPageRepository, EmbeddingModel embeddingModel) {
        this.webPageRepository = webPageRepository;
        this.embeddingModel = embeddingModel;
    }

    @EventListener
    public void processIncomingScrapedData(CrawlerEvent event) {
        CrawlResult rawData = event.getCrawlResult();
        String url = rawData.getUrl();
        
        System.out.println("\n📡 [Pipeline Consumer] Intercepted stream payload for: " + rawData.getTitle());
        
        String rawText = rawData.getRawText();
        if (rawText == null || rawText.isBlank()) {
            return;
        }

        // 1. Core Frequency Tokenization Logic (Retained for Hybrid Lookup Strategy)
        String[] tokens = rawText.toLowerCase()
                .replaceAll("[^a-zA-Z\\s]", "")
                .split("\\s+");

        Map<String, Integer> wordFrequencies = new HashMap<>();
        for (String word : tokens) {
            if (word.length() > 2 && !STOP_WORDS.contains(word)) {
                wordFrequencies.put(word, wordFrequencies.getOrDefault(word, 0) + 1);
            }
        }

        try {
            System.out.println("🧠 [Ollama Inference] Computing high-dimensional vector embeddings...");
            
            // Guardrail constraint: Embeddings perform best on clean, condensed textual boundaries
            String embeddingInput = rawText.length() > 1500 ? rawText.substring(0, 1500) : rawText;

            // 2. Compute the semantic vector using our local all-minilm engine
         // 2. Compute the semantic vector using our local all-minilm engine
            float[] embeddingResponse = embeddingModel.embed(embeddingInput);
            
            // Convert primitive float[] array cleanly into standard List<Double>
            List<Double> textVector = new ArrayList<>();
            if (embeddingResponse != null) {
                for (float val : embeddingResponse) {
                    textVector.add((double) val);
                }
            }

            // 3. PERSISTENCE UPGRADE: Package text indices, maps, and vector coordinates together
            WebPageDocument document = new WebPageDocument(
                url, 
                rawData.getTitle(), 
                rawText, 
                wordFrequencies, 
                textVector // Passing our new vector array downstream
            );
            
            webPageRepository.save(document);
            System.out.println("💾 [MongoDB Storage] Successfully persisted Hybrid Vector Document: " + rawData.getTitle());

        } catch (Exception e) {
            System.err.println("❌ Critical failure during vector processing sequence: " + e.getMessage());
        }
    }
}