package com.stocksense.stocksense_backend.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document(collection = "notifications")
public class Notification {
    @Id
    private String id;
    
    private String userId; // Email or user ID
    private String title;
    private String message;
    private String link; // Optional link to redirect when clicked
    
    private boolean isRead = false;
    private Instant createdAt = Instant.now();
}
