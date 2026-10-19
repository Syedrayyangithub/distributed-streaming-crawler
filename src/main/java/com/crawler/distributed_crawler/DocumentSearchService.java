package com.crawler.distributed_crawler;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentSearchService {

    private static final int MAX_DOCS_SCANNED = 200;
    private static final int EMBED_CACHE_SIZE = 64;

    private final MongoTemplate mongoTemplate;
    private final EmbeddingModel embeddingModel;
    private final WikipediaLiveFetcher wikipediaLiveFetcher;
    private final Map<String, List<Double>> queryEmbedCache = new LinkedHashMap<>(EMBED_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Double>> eldest) {
            return size() > EMBED_CACHE_SIZE;
        }
    };

    public DocumentSearchService(MongoTemplate mongoTemplate,
                                 EmbeddingModel embeddingModel,
                                 WikipediaLiveFetcher wikipediaLiveFetcher) {
        this.mongoTemplate = mongoTemplate;
        this.embeddingModel = embeddingModel;
        this.wikipediaLiveFetcher = wikipediaLiveFetcher;
    }

    public List<WebPageDocument> findOrFetchRelevantDocuments(String userPrompt, int limit) {
        List<Double> queryVector = embedQuery(userPrompt);
        List<String> terms = QueryRelevance.distinctiveTerms(userPrompt);
        List<WebPageDocument> localHits = preferTitleMatches(
                findRelevantDocuments(userPrompt, limit, queryVector), terms, limit);
        if (hasTitleMatch(localHits, terms)) {
            return localHits;
        }

        String fetchQuery = terms.isEmpty() ? userPrompt : String.join(" ", terms);
        System.out.println("🔎 No title-level local pages for [" + userPrompt + "]. Fetching Wikipedia for: " + fetchQuery);
        int saved = wikipediaLiveFetcher.fetchAndIndex(fetchQuery, Math.min(4, Math.max(limit, 3)));
        System.out.println("📥 Live fetch indexed " + saved + " page(s).");
        List<WebPageDocument> fetched = preferTitleMatches(
                findRelevantDocuments(userPrompt, limit, queryVector), terms, limit);
        if (!fetched.isEmpty()) {
            return fetched;
        }
        return localHits;
    }

    public List<WebPageDocument> findRelevantDocuments(String userPrompt, int limit) {
        return findRelevantDocuments(userPrompt, limit, embedQuery(userPrompt));
    }

    private List<WebPageDocument> findRelevantDocuments(String userPrompt, int limit, List<Double> queryVector) {
        List<String> terms = QueryRelevance.distinctiveTerms(userPrompt);
        Query query = new Query().limit(MAX_DOCS_SCANNED);
        query.fields().include("title").include("url").include("rawText").include("embedding");
        List<WebPageDocument> allDocs = mongoTemplate.find(query, WebPageDocument.class);

        record RankedDoc(WebPageDocument document, double score) {}
        return allDocs.stream()
                .filter(doc -> !QueryRelevance.isNoisyTitle(doc.getTitle(), terms))
                .map(doc -> new RankedDoc(doc, QueryRelevance.score(doc, terms, queryVector)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingDouble(RankedDoc::score).reversed())
                .map(RankedDoc::document)
                .limit(limit)
                .toList();
    }

    private List<WebPageDocument> preferTitleMatches(List<WebPageDocument> docs, List<String> terms, int limit) {
        List<WebPageDocument> titled = docs.stream()
                .filter(doc -> QueryRelevance.titleContainsAnyTerm(doc.getTitle(), terms))
                .limit(limit)
                .toList();
        return titled.isEmpty() ? docs : titled;
    }

    private boolean hasTitleMatch(List<WebPageDocument> docs, List<String> terms) {
        return docs.stream().anyMatch(doc -> QueryRelevance.titleContainsAnyTerm(doc.getTitle(), terms));
    }

    public long indexedDocumentCount() {
        return mongoTemplate.count(new Query(), WebPageDocument.class);
    }

    private List<Double> embedQuery(String userPrompt) {
        synchronized (queryEmbedCache) {
            List<Double> cached = queryEmbedCache.get(userPrompt);
            if (cached != null) {
                return cached;
            }
        }
        List<Double> queryVector = List.of();
        try {
            float[] embedding = embeddingModel.embed(userPrompt);
            List<Double> computed = new ArrayList<>();
            if (embedding != null) {
                for (float value : embedding) {
                    computed.add((double) value);
                }
            }
            queryVector = List.copyOf(computed);
        } catch (Exception e) {
            System.out.println("⚠️ Query embedding unavailable, using keyword search: " + e.getMessage());
        }
        synchronized (queryEmbedCache) {
            queryEmbedCache.put(userPrompt, queryVector);
        }
        return queryVector;
    }
}
