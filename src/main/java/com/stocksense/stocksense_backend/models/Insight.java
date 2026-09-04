package com.stocksense.stocksense_backend.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document(collection = "insights")
public class Insight {
    @Id
    private String id;
    
    private String transactionId;
    private String ticker; 
    private String sector;
    private String headline;
    
    // The 3-tier structure matching the AI Output
    private String summary5Sec;
    private String breakdown30Sec;
    
    // Source Document Link
    private String sourceUrl;
    private java.util.List<String> relatedUrls = new java.util.ArrayList<>();
    
    // State machine logic
    private Status status; // PUBLISHED, QUARANTINE, RAW_HEADLINE_ONLY
    private int confidenceScore;
    
    private Instant createdAt = Instant.now();
    
    public enum Status {
        PENDING_PUBLISH,
        PUBLISHED,
        QUARANTINE,
        RAW_HEADLINE_ONLY
    }
}
