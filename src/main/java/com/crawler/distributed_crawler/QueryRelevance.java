package com.crawler.distributed_crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class QueryRelevance {

    static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "by",
            "for", "with", "about", "why", "how", "what", "when", "who", "which", "is", "it",
            "this", "that", "from", "into", "good", "habit", "should", "would", "could",
            "not", "keep", "does", "did", "can", "are", "was", "were", "been", "being",
            "have", "has", "had", "your", "their", "them", "they", "you", "i", "my", "me",
            "we", "our", "just", "very", "more", "some", "than", "then", "also", "over"
    );

    private QueryRelevance() {
    }

    public static List<String> distinctiveTerms(String userPrompt) {
        List<String> terms = new ArrayList<>();
        if (userPrompt == null || userPrompt.isBlank()) {
            return terms;
        }
        for (String term : userPrompt.toLowerCase(Locale.ROOT).split("\\s+")) {
            String cleaned = term.replaceAll("[^a-z0-9]", "");
            if (cleaned.length() <= 3 || STOP_WORDS.contains(cleaned)) {
                continue;
            }
            if (!terms.contains(cleaned)) {
                terms.add(cleaned);
            }
        }
        return terms;
    }

    public static boolean titleContainsAnyTerm(String title, List<String> terms) {
        if (title == null || terms == null || terms.isEmpty()) {
            return false;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(term -> containsTerm(lower, term));
    }

    static boolean containsTerm(String haystack, String term) {
        if (haystack.contains(term)) {
            return true;
        }
        if (term.endsWith("s") && term.length() > 4) {
            return haystack.contains(term.substring(0, term.length() - 1));
        }
        return false;
    }

    public static boolean isNoisyTitle(String title, List<String> terms) {
        if (title == null) {
            return true;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("disambiguation")) {
            return true;
        }
        List<String> noise = List.of("song", "album", "film", "book", "novel", "board game", "tv series", "episode");
        for (String label : noise) {
            boolean titleLooksLikeMedia = lower.contains("(" + label + ")") || lower.endsWith(" " + label);
            boolean queryWantsMedia = terms.contains(label.replace(" ", ""));
            if (titleLooksLikeMedia && !queryWantsMedia) {
                return true;
            }
        }
        return false;
    }

    public static double score(WebPageDocument doc, List<String> terms, List<Double> queryVector) {
        if (doc == null || terms == null || terms.isEmpty()) {
            return -1;
        }
        String title = doc.getTitle() == null ? "" : doc.getTitle().toLowerCase(Locale.ROOT);
        String body = doc.getRawText() == null ? "" : doc.getRawText().toLowerCase(Locale.ROOT);
        String haystack = title + "\n" + body;

        long coveredTerms = terms.stream().filter(term -> containsTerm(haystack, term)).count();
        long titleHits = terms.stream().filter(term -> containsTerm(title, term)).count();
        int bodyOccurrences = 0;
        for (String term : terms) {
            bodyOccurrences += countOccurrences(body, term);
        }

        double cosine = Math.max(0, cosineSimilarity(queryVector, doc.getEmbedding()));
        if (coveredTerms == 0) {
            return -1;
        }
        if (coveredTerms < terms.size() && cosine < 0.42) {
            return -1;
        }
        if (titleHits == 0 && bodyOccurrences < 8) {
            return -1;
        }

        double coverage = (double) coveredTerms / terms.size();
        double lexical = Math.min(1.0, (titleHits * 0.5) + (bodyOccurrences / 10.0));
        return (coverage * 0.25) + (titleHits * 0.25) + (lexical * 0.15) + (Math.min(cosine, 1.0) * 0.35);
    }

    public static double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.size() != right.size() || left.isEmpty()) {
            return -1;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return -1;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    static int countOccurrences(String haystack, String needle) {
        if (haystack == null || needle == null || haystack.isEmpty() || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
