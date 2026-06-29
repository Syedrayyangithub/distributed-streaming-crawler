package com.crawler.distributed_crawler;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Map;

@Document(collection = "crawled_pages")
public class WebPageDocument {

    @Id
    private String id;
    private String url;
    private String title;
    private String rawText;
    
    // Inverted-Index Tracking: Stores keyword distributions for frequency-based matches
    private Map<String, Integer> wordFrequencies;
    
    // Semantic Vector Tracking: Dense coordinates representing text meaning for local LLM searches
    private List<Double> embedding; 

    // Default Constructor required by Spring Data MongoDB for deserialization mappings
    public WebPageDocument() {
    }

    // Comprehensive Constructor used by our DataPipelineConsumer block
    public WebPageDocument(String url, String title, String rawText, Map<String, Integer> wordFrequencies, List<Double> embedding) {
        this.url = url;
        this.title = title;
        this.rawText = rawText;
        this.wordFrequencies = wordFrequencies;
        this.embedding = embedding;
    }

    // --- Getters and Setters ---
    public String getId() { 
        return id; 
    }
    
    public String getUrl() { 
        return url; 
    }
    
    public void setUrl(String url) { 
        this.url = url; 
    }
    
    public String getTitle() { 
        return title; 
    }
    
    public void setTitle(String title) { 
        this.title = title; 
    }
    
    public String getRawText() { 
        return rawText; 
    }
    
    public void setRawText(String rawText) { 
        this.rawText = rawText; 
    }
    
    public Map<String, Integer> getWordFrequencies() { 
        return wordFrequencies; 
    }
    
    public void setWordFrequencies(Map<String, Integer> wordFrequencies) { 
        this.wordFrequencies = wordFrequencies; 
    }
    
    public List<Double> getEmbedding() { 
        return embedding; 
    }
    
    public void setEmbedding(List<Double> embedding) { 
        this.embedding = embedding; 
    }
}