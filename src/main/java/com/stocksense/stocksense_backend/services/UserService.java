package com.stocksense.stocksense_backend.services;

import com.stocksense.stocksense_backend.dtos.UserProfileDTO;
import com.stocksense.stocksense_backend.models.User;
import com.stocksense.stocksense_backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserProfileDTO getUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return UserProfileDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .pictureUrl(user.getPictureUrl())
                .pushNotificationsEnabled(user.isPushNotificationsEnabled())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .savedInsights(user.getSavedInsights())
                .build();
    }

    public UserProfileDTO updatePushNotifications(String email, boolean enabled) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPushNotificationsEnabled(enabled);
        User savedUser = userRepository.save(user);

        return UserProfileDTO.builder()
                .id(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .pictureUrl(savedUser.getPictureUrl())
                .pushNotificationsEnabled(savedUser.isPushNotificationsEnabled())
                .createdAt(savedUser.getCreatedAt())
                .lastLogin(savedUser.getLastLogin())
                .savedInsights(savedUser.getSavedInsights())
                .build();
    }

    public UserProfileDTO addBookmark(String email, String insightId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getSavedInsights().contains(insightId)) {
            user.getSavedInsights().add(insightId);
            user = userRepository.save(user);
        }
        return getUserProfile(user.getEmail());
    }

    public UserProfileDTO removeBookmark(String email, String insightId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getSavedInsights().contains(insightId)) {
            user.getSavedInsights().remove(insightId);
            user = userRepository.save(user);
        }
        return getUserProfile(user.getEmail());
    }
}
