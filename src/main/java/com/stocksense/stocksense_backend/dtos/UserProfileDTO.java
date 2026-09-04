package com.stocksense.stocksense_backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileDTO {
    private String id;
    private String name;
    private String email;
    private String pictureUrl;
    private boolean pushNotificationsEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
    private java.util.List<String> savedInsights;
}
