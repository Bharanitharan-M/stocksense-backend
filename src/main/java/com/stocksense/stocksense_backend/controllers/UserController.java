package com.stocksense.stocksense_backend.controllers;

import com.stocksense.stocksense_backend.dtos.UserProfileDTO;
import com.stocksense.stocksense_backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin("*")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getMyProfile(@AuthenticationPrincipal String email) {
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.getUserProfile(email));
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<?> updatePreferences(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, Boolean> updates) {
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        
        try {
            if (updates.containsKey("pushNotificationsEnabled")) {
                UserProfileDTO updatedProfile = userService.updatePushNotifications(email, updates.get("pushNotificationsEnabled"));
                return ResponseEntity.ok(updatedProfile);
            }
            return ResponseEntity.badRequest().body(Map.of("error", "No valid preferences to update."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/me/bookmarks/{insightId}")
    public ResponseEntity<?> addBookmark(
            @AuthenticationPrincipal String email,
            @PathVariable String insightId) {
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(userService.addBookmark(email, insightId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/me/bookmarks/{insightId}")
    public ResponseEntity<?> removeBookmark(
            @AuthenticationPrincipal String email,
            @PathVariable String insightId) {
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(userService.removeBookmark(email, insightId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
