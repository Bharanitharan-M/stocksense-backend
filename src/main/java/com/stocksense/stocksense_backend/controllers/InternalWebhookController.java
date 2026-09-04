package com.stocksense.stocksense_backend.controllers;

import com.stocksense.stocksense_backend.services.DiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/webhook")
@RequiredArgsConstructor
public class InternalWebhookController {

    private final DiscoveryService discoveryService;

    @PostMapping("/insight-result")
    public ResponseEntity<Void> handleInsightResult(@RequestBody Map<String, Object> payload) {
        log.info("Received async webhook from Python Engine");
        try {
            String query = (String) payload.get("query");
            List<String> userEmails = (List<String>) payload.get("userEmails");
            discoveryService.handleAsyncResult(query, userEmails, payload);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to handle webhook payload", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
