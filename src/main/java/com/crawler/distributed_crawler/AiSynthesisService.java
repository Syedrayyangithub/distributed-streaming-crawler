package com.crawler.distributed_crawler;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class AiSynthesisService {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final MongoTemplate mongoTemplate;

    // Injecting our core components: Ollama Chat client, Embedding engine, and MongoTemplate
    public AiSynthesisService(ChatClient.Builder chatClientBuilder, EmbeddingModel embeddingModel, MongoTemplate mongoTemplate) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.mongoTemplate = mongoTemplate;
    }

    public String synthesizeAnswer(String keyword, String fullUserPrompt) {
        
        // 1. Convert user prompt into search coordinates using local all-minilm engine
        float[] queryEmbeddingResponse = embeddingModel.embed(fullUserPrompt);
        
        // Convert to standard Double array format for database aggregation mapping
        List<Double> queryVector = new ArrayList<>();
        if (queryEmbeddingResponse != null) {
            for (float val : queryEmbeddingResponse) {
                queryVector.add((double) val);
            }
        }

        // 2. Query MongoDB using an Aggregation Pipeline stage to fetch matching contexts
        List<WebPageDocument> sourceDocs;
        try {
            Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.stage("{ $vectorSearch: { " +
                    "index: 'vector_index', " +
                    "path: 'embedding', " +
                    "queryVector: " + queryVector.toString() + ", " +
                    "numCandidates: 10, " +
                    "limit: 2 } }")
            );
            sourceDocs = mongoTemplate.aggregate(aggregation, "crawled_pages", WebPageDocument.class).getMappedResults();
        } catch (Exception e) {
            // Broad Fallback: Fall back to checking if the document title or URL shares keywords with the user prompt
            System.out.println("⚠️ MongoDB Vector Index not found. Running broad title criteria fallback...");
            
            org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
            String[] queryTerms = fullUserPrompt.toLowerCase().split("\\s+");
            
            List<Criteria> criteriaList = new ArrayList<>();
            for (String term : queryTerms) {
                if (term.length() > 3) { // Skip tiny structural words like "is", "it", "why"
                    criteriaList.add(Criteria.where("title").regex(term, "i"));
                    criteriaList.add(Criteria.where("url").regex(term, "i"));
                }
            }
            
            if (!criteriaList.isEmpty()) {
                Criteria dynamicCriteria = new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
                query.addCriteria(dynamicCriteria);
            }
            
            query.limit(2);
            sourceDocs = mongoTemplate.find(query, WebPageDocument.class);
            
            // Absolute Emergency Guardrail: If still empty, grab the latest documents to guarantee context for RAG references!
            if (sourceDocs.isEmpty()) {
                System.out.println("⚠️ No criteria match. Pulling latest database records to populate references...");
                org.springframework.data.mongodb.core.query.Query emergencyQuery = new org.springframework.data.mongodb.core.query.Query();
                emergencyQuery.limit(2);
                sourceDocs = mongoTemplate.find(emergencyQuery, WebPageDocument.class);
            }
        }

        if (sourceDocs.isEmpty()) {
            return "No web documents found matching the topic target context. Unable to synthesize an AI response.";
        }

        // 3. Format the text context snippet safely to keep CPU temperatures low
        String contextPayload = sourceDocs.stream()
                .map(doc -> {
                    String shortText = doc.getRawText();
                    if (shortText != null && shortText.length() > 800) {
                        shortText = shortText.substring(0, 800) + "... [truncated]";
                    }
                    return "Source Title: " + doc.getTitle() + "\nURL: " + doc.getUrl() + "\nContent: " + shortText;
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = """
            You are an advanced AI Search Assistant operating on a crawled web data repository.
            Your task is to synthesize a direct, comprehensive, and factual answer to the user's query
            based ONLY on the verified web page source text contexts provided below.
            
            Always prioritize accuracy. At the end of your response, list the URLs you used as references.
            
            ---
            CRAWLED SOURCE CONTEXT DATA:
            %s
            ---
            """.formatted(contextPayload);

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(fullUserPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            return "AI Processing Error: " + e.getMessage();
        }
    }
}