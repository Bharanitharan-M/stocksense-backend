package com.stocksense.stocksense_backend.controllers;

import com.stocksense.stocksense_backend.dtos.AIDiscoveryResponseDto;
import com.stocksense.stocksense_backend.models.Insight;
import com.stocksense.stocksense_backend.models.Notification;
import com.stocksense.stocksense_backend.repositories.InsightRepository;
import com.stocksense.stocksense_backend.repositories.NotificationRepository;
import com.stocksense.stocksense_backend.services.AiEngineClientService;
import com.stocksense.stocksense_backend.services.DiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/search")
@CrossOrigin("*")
@RequiredArgsConstructor
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    private final InsightRepository insightRepository;
    private final AiEngineClientService aiEngineClientService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    /**
     * Standard Search: Queries the local database for existing insights.
     */
    @GetMapping
    public ResponseEntity<List<Insight>> searchInsights(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "all") String sector,
            @RequestParam(required = false, defaultValue = "all") String timeRange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sortBy) {
        
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        
        // Status filter
        query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("status").is(Insight.Status.PUBLISHED));
        
        // Text search filter
        if (q != null && !q.trim().isEmpty()) {
            org.springframework.data.mongodb.core.query.Criteria searchCriteria = new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                org.springframework.data.mongodb.core.query.Criteria.where("headline").regex(q.trim(), "i"),
                org.springframework.data.mongodb.core.query.Criteria.where("ticker").regex(q.trim(), "i"),
                org.springframework.data.mongodb.core.query.Criteria.where("summary5Sec").regex(q.trim(), "i")
            );
            query.addCriteria(searchCriteria);
        }
        
        // Sector filter
        if (sector != null && !sector.equalsIgnoreCase("all")) {
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("sector").regex(sector, "i"));
        }
        
        // Time Range filter
        if (timeRange != null && !timeRange.equalsIgnoreCase("all")) {
            Instant fromDate = Instant.now();
            if (timeRange.equalsIgnoreCase("24h")) {
                fromDate = fromDate.minus(24, java.time.temporal.ChronoUnit.HOURS);
            } else if (timeRange.equalsIgnoreCase("week")) {
                fromDate = fromDate.minus(7, java.time.temporal.ChronoUnit.DAYS);
            } else if (timeRange.equalsIgnoreCase("month")) {
                fromDate = fromDate.minus(30, java.time.temporal.ChronoUnit.DAYS);
            }
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("createdAt").gte(fromDate));
        }
        
        // Sorting
        if (sortBy.equalsIgnoreCase("relevance")) {
            query.with(Sort.by(Sort.Direction.DESC, "confidenceScore"));
        } else {
            query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        
        // Pagination
        query.with(PageRequest.of(page, size));
        
        List<Insight> results = mongoTemplate.find(query, Insight.class);
        return ResponseEntity.ok(results);
    }

    /**
     * AI Search Fallback: Triggers live Google Search Grounding to discover
     * new financial news on-demand, indexes it, and returns the result.
     */
    @PostMapping("/discover")
    public ResponseEntity<List<Insight>> discoverNews(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        try {
            AIDiscoveryResponseDto response = aiEngineClientService.discoverNewsOnDemand(q.trim());

            if ("RELEVANT".equalsIgnoreCase(response.getRelevanceOutcome()) && response.getHeadline() != null) {
                // Map the discovered news into a new Insight document
                Insight insight = new Insight();
                insight.setTransactionId("disc-" + UUID.randomUUID().toString().substring(0, 8));
                insight.setHeadline(response.getHeadline());
                insight.setSummary5Sec(response.getSummary5Sec() != null ? response.getSummary5Sec() : "No summary available.");
                insight.setBreakdown30Sec(response.getBreakdown30Sec() != null ? response.getBreakdown30Sec() : "No breakdown available.");
                insight.setTicker(response.getTicker() != null ? response.getTicker().toUpperCase() : "GENERAL");
                insight.setSector(response.getSector() != null ? response.getSector() : "Macro");
                insight.setStatus(Insight.Status.PUBLISHED);
                insight.setConfidenceScore(95); // High confidence default for Search Grounding
                insight.setCreatedAt(Instant.now());
                
                // Use dynamic source URL if available, otherwise fallback
                insight.setSourceUrl(response.getSourceUrl() != null ? response.getSourceUrl() : "https://economictimes.indiatimes.com");

                // Check for deduplication one last time before saving
                if (!insightRepository.existsByHeadline(insight.getHeadline())) {
                    insightRepository.save(insight);
                }
                
                return ResponseEntity.ok(List.of(insight));
            } else {
                return ResponseEntity.ok(List.of());
            }
        } catch (Exception e) {
            logger.error("Failed to execute AI Discovery", e);
            return ResponseEntity.status(500).build();
        }
    }

    private final DiscoveryService discoveryService;

    @PostMapping("/discover/async")
    public ResponseEntity<java.util.Map<String, String>> discoverNewsAsync(
            @RequestParam String q,
            @AuthenticationPrincipal String userEmail) {
            
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        discoveryService.discoverAndProcessAsync(q.trim(), userEmail);

        return ResponseEntity.accepted().body(Collections.singletonMap("message", "Analysis started in background."));
    }

    @GetMapping("/trending")
    public ResponseEntity<List<String>> getTrendingSignals() {
        PageRequest pageRequest = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Insight> recentInsights = insightRepository.findByStatusOrderByCreatedAtDesc(Insight.Status.PUBLISHED, pageRequest);
        
        List<String> trending = recentInsights.getContent().stream()
            .map(Insight::getTicker)
            .filter(ticker -> ticker != null && !ticker.equalsIgnoreCase("GENERAL"))
            .distinct()
            .limit(5)
            .map(ticker -> ticker + " News")
            .toList();

        // Fallback if we don't have enough data in the DB yet
        if (trending.isEmpty()) {
            trending = List.of("RELIANCE News", "HDFCBANK News", "TCS News", "INFY News", "TATAMOTORS News");
        }
        
        return ResponseEntity.ok(trending);
    }
    
    @GetMapping("/sectors")
    public ResponseEntity<List<String>> getAvailableSectors() {
        List<String> distinctSectors = mongoTemplate.findDistinct("sector", Insight.class, String.class);
        
        List<String> formattedSectors = distinctSectors.stream()
            .filter(s -> s != null && !s.trim().isEmpty() && !s.equalsIgnoreCase("Macro"))
            .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
            .distinct()
            .sorted()
            .toList();
            
        return ResponseEntity.ok(formattedSectors);
    }
}
