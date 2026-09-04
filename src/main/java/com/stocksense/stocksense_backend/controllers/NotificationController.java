package com.stocksense.stocksense_backend.controllers;

import com.stocksense.stocksense_backend.models.Notification;
import com.stocksense.stocksense_backend.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@CrossOrigin("*")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public ResponseEntity<List<Notification>> getUserNotifications(@AuthenticationPrincipal String email) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(email);
        return ResponseEntity.ok(notifications);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable String id, @AuthenticationPrincipal String email) {
        notificationRepository.findById(id).ifPresent(notification -> {
            if (notification.getUserId().equals(email)) {
                notificationRepository.delete(notification);
            }
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable String id) {
        notificationRepository.findById(id).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal String email) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(email);
        for (Notification notification : unread) {
            notification.setRead(true);
        }
        if (!unread.isEmpty()) {
            notificationRepository.saveAll(unread);
        }
        return ResponseEntity.ok().build();
    }
}
