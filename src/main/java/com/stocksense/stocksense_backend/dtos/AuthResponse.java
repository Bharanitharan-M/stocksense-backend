package com.stocksense.stocksense_backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String sessionId;

    // User profile included directly so the frontend doesn't need to decode any token
    private String userId;
    private String email;
    private String name;
    private String pictureUrl;
    private java.util.List<String> savedInsights;
}
