package com.stocksense.stocksense_backend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sessions")
public class Session {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sessionId;       // UUID — what the client stores in the HttpOnly cookie

    private String userId;          // MongoDB user _id
    private String userEmail;       // For quick lookups

    private String refreshToken;    // Stored SERVER-SIDE only — never sent to the client

    private Instant createdAt;
    private Instant expiresAt;      // 7 days from creation

    private String userAgent;       // For security auditing (device info)
    private String ipAddress;
}
