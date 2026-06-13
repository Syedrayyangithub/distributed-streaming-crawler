package com.crawler.distributed_crawler;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class DataPipelineConsumer {

    // A basic set of English stop words to filter out noisy text data
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "by", 
        "for", "with", "about", "against", "between", "into", "through", "during", 
        "before", "after", "above", "below", "from", "up", "down", "is", "are", 
        "was", "were", "be", "been", "being", "have", "has", "had", "this", "that"
    ));

    @EventListener
    public void processIncomingScrapedData(CrawlerEvent event) {
        CrawlResult rawData = event.getCrawlResult();
        
        System.out.println("\n📡 [Pipeline Consumer] Intercepted stream payload for: " + rawData.getTitle());
        
        // 1. Run Tokenization & Word Count Analytics
        String rawText = rawData.getRawText();
        if (rawText == null || rawText.isBlank()) {
            return;
        }

        // Split text by spaces and strip away non-alphabetic characters
        String[] tokens = rawText.toLowerCase()
                .replaceAll("[^a-zA-Z\\s]", "")
                .split("\\s+");

        int totalWords = tokens.length;
        Map<String, Integer> wordFrequencies = new HashMap<>();

        for (String word : tokens) {
            if (word.length() > 2 && !STOP_WORDS.contains(word)) {
                wordFrequencies.put(word, wordFrequencies.getOrDefault(word, 0) + 1);
            }
        }

        // 2. Extract Top 5 Most Frequent Keywords using Java Streams
        Map<String, Integer> topKeywords = wordFrequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, 
                        Map.Entry::getValue, 
                        (e1, e2) -> e1, 
                        LinkedHashMap::new
                ));

        // 3. Package into our structured analytical record object
        PageAnalysis analysis = new PageAnalysis(rawData.getUrl(), rawData.getTitle(), totalWords, topKeywords);

        // 4. Print out the structured data payload summary
        displayAnalysisReport(analysis);
    }

    private void displayAnalysisReport(PageAnalysis analysis) {
        System.out.println("📊 --- CONTENT ANALYSIS REPORT ---");
        System.out.println("🔗 URL:          " + analysis.getUrl());
        System.out.println("🏷️ TITLE:        " + analysis.getTitle());
        System.out.println("🔤 TOTAL WORDS: " + analysis.getTotalWordCount());
        System.out.println("🔥 TOP KEYWORDS INDICES:");
        analysis.getTopKeywords().forEach((word, frequency) -> 
            System.out.println("   ▪️ " + word + " -> appeared " + frequency + " times")
        );
        System.out.println("---------------------------------\n");
    }
}