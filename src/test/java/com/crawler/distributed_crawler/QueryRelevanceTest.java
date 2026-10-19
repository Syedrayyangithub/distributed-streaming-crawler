package com.crawler.distributed_crawler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryRelevanceTest {

    @Test
    void extractsDistinctiveTermsAndDropsStopWords() {
        List<String> laptopTerms = QueryRelevance.distinctiveTerms(
                "why should i not keep my laptop on my lap");
        assertEquals(List.of("laptop"), laptopTerms);

        List<String> sleepTerms = QueryRelevance.distinctiveTerms(
                "why is it a good habit to wake up early in the morning");
        assertEquals(List.of("wake", "early", "morning"), sleepTerms);
    }

    @Test
    void marksMediaAndDisambiguationTitlesAsNoise() {
        List<String> terms = List.of("early", "morning");
        assertTrue(QueryRelevance.isNoisyTitle("Early Morning (song) - Wikipedia", terms));
        assertTrue(QueryRelevance.isNoisyTitle("Photosynthesis (disambiguation) - Wikipedia", List.of("photosynthesis")));
        assertFalse(QueryRelevance.isNoisyTitle("Laptop - Wikipedia", List.of("laptop")));
        assertFalse(QueryRelevance.isNoisyTitle("Early Morning (song) - Wikipedia", List.of("song")));
    }

    @Test
    void rejectsOffTopicPagesAndKeepsOnTopicPages() {
        List<String> laptopTerms = QueryRelevance.distinctiveTerms("why should i not keep my laptop on my lap");
        WebPageDocument sleepPage = page(
                "Sleep - Wikipedia",
                "Sleep is a naturally recurring state of mind and body. Healthy sleep includes several stages of rest."
        );
        WebPageDocument laptopPage = page(
                "Laptop - Wikipedia",
                "A laptop is a portable personal computer. Laptops produce heat on the user's lap and can block ventilation."
        );

        assertTrue(QueryRelevance.score(sleepPage, laptopTerms, List.of()) < 0);
        assertTrue(QueryRelevance.score(laptopPage, laptopTerms, List.of()) > 0);
    }

    @Test
    void titleMustContainTheTopicWhenPresent() {
        assertTrue(QueryRelevance.titleContainsAnyTerm("Animal - Wikipedia", List.of("animals")));
        assertFalse(QueryRelevance.titleContainsAnyTerm("Circadian rhythm - Wikipedia", List.of("animals")));
    }

    @Test
    void cosineSimilarityIsOneForIdenticalVectors() {
        List<Double> vector = List.of(1.0, 0.0, 0.0);
        assertEquals(1.0, QueryRelevance.cosineSimilarity(vector, vector), 1e-9);
        assertEquals(-1.0, QueryRelevance.cosineSimilarity(vector, List.of(1.0, 0.0)));
    }

    private static WebPageDocument page(String title, String text) {
        WebPageDocument document = new WebPageDocument();
        document.setTitle(title);
        document.setRawText(text);
        document.setUrl("https://en.wikipedia.org/wiki/" + title.replace(" ", "_"));
        return document;
    }
}
