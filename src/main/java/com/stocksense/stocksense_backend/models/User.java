package com.stocksense.stocksense_backend.models;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String googleId;
    private String email;
    private String name;
    private String avatarUrl;
    private String pictureUrl;
    private boolean pushNotificationsEnabled = true;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
    
    // Watchlist references (capped at 5 by business logic)
    @Builder.Default
    private List<String> watchlistTickers = new ArrayList<>();

    // Bookmarked insight IDs
    @Builder.Default
    private List<String> savedInsights = new ArrayList<>();
}
