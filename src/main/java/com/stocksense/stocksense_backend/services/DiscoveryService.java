package com.stocksense.stocksense_backend.services;

import com.stocksense.stocksense_backend.dtos.AIDiscoveryResponseDto;
import com.stocksense.stocksense_backend.models.Insight;
import com.stocksense.stocksense_backend.models.Notification;
import com.stocksense.stocksense_backend.repositories.InsightRepository;
import com.stocksense.stocksense_backend.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final AiEngineClientService aiEngineClientService;
    private final InsightRepository insightRepository;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final LiveNewsFeedService liveNewsFeedService;
    private final MongoTemplate mongoTemplate;

    @Async("taskExecutor")
    public void discoverAndProcessAsync(String query, String userEmail) {
        discoverAndProcessAsyncBatched(query, List.of(userEmail));
    }

    @Async("taskExecutor")
    public void discoverAndProcessAsyncBatched(String query, List<String> userEmails) {
        try {
            // Cheap Check: Fetch Google News RSS for the query
            List<LiveNewsFeedService.NewsArticle> articles = liveNewsFeedService.fetchGoogleNewsForTicker(query);
            
            if (articles.isEmpty()) {
                log.info("Cheap Check: No articles found in RSS for {}", query);
                sendNotifications(userEmails, query, "No recent news found for '" + query + "'.", "/search?q=" + query, "Analysis Complete");
                return;
            }
            
            // Check if top article's URL already exists
            String topUrl = articles.get(0).sourceUrl;
            if (insightRepository.existsBySourceUrl(topUrl)) {
                log.info("Cheap Check: Article {} already exists in DB for {}. Skipping AI processing.", topUrl, query);
                return;
            }
            
            // It's a new article, trigger ASYNC processing in Python!
            // Fire and forget.
            aiEngineClientService.triggerDiscoverAsync(query.trim(), userEmails, topUrl);
            
            log.info("Successfully handed off task to Python Engine for query: {}", query);
            
        } catch (Exception e) {
            log.error("Background AI Discovery failed for " + query, e);
            sendNotifications(userEmails, query, "An error occurred while analyzing '" + query + "'.", "/search?q=" + query, "Analysis Failed");
        }
    }
    
    public void handleAsyncResult(String query, List<String> userEmails, Map<String, Object> payload) {
        try {
            String relevance = (String) payload.get("relevance_outcome");
            String headline = (String) payload.get("headline");
            String topUrl = (String) payload.get("top_url");
            String sourceUrl = payload.containsKey("source_url") && payload.get("source_url") != null ? (String) payload.get("source_url") : topUrl;
            
            String link = "/search?q=" + query;
            
            if ("RELEVANT".equalsIgnoreCase(relevance) && headline != null) {
                // ATOMIC $push update to prevent Race Conditions (Lost Updates)
                if (insightRepository.existsByHeadline(headline)) {
                    Query search = new Query(Criteria.where("headline").is(headline));
                    Update update = new Update().push("relatedUrls", sourceUrl);
                    
                    mongoTemplate.updateFirst(search, update, Insight.class);
                    log.info("Atomically appended new source URL to existing insight for {}", query);
                    return; 
                }
                
                Insight insight = new Insight();
                insight.setTransactionId("disc-" + UUID.randomUUID().toString().substring(0, 8));
                insight.setHeadline(headline);
                insight.setSummary5Sec((String) payload.get("summary_5_sec"));
                insight.setBreakdown30Sec((String) payload.get("breakdown_30_sec"));
                
                String extractedTicker = (String) payload.get("ticker");
                String finalTicker = extractedTicker != null ? extractedTicker.toUpperCase() : "GENERAL";
                
                // Subsidiary-to-Parent Mapping:
                // If the AI Engine extracted a different parent ticker (e.g. TTWO) for a subsidiary query (e.g. ROCK STAR GAMES),
                // we map the insight to the user's exact query so it successfully links to their Watchlist UI!
                if (query != null && !query.trim().isEmpty() && extractedTicker != null && !query.equalsIgnoreCase(extractedTicker) && !extractedTicker.equals("GENERAL")) {
                    finalTicker = query.toUpperCase();
                }
                insight.setTicker(finalTicker);
                
                String sector = (String) payload.get("sector");
                insight.setSector(sector != null ? sector : "Macro");
                
                insight.setStatus(Insight.Status.PUBLISHED);
                insight.setConfidenceScore(95);
                insight.setCreatedAt(Instant.now());
                insight.setSourceUrl(sourceUrl);
                
                insightRepository.save(insight);
                
                try {
                    messagingTemplate.convertAndSend("/topic/feed", insight);
                } catch (Exception ex) {
                    log.error("Failed to broadcast to /topic/feed", ex);
                }
                
                String notificationMessage = "AI Analysis complete! Found new insights for '" + query + "'.";
                sendNotifications(userEmails, query, notificationMessage, link, "Analysis Complete");
            } else {
                sendNotifications(userEmails, query, "No relevant news found for '" + query + "'.", link, "Analysis Complete");
            }
        } catch (Exception e) {
            log.error("Error processing webhook result for {}", query, e);
        }
    }
    
    private void sendNotifications(List<String> userEmails, String query, String message, String link, String title) {
        if (userEmails == null || userEmails.isEmpty()) return;
        for (String email : userEmails) {
            Notification notification = new Notification();
            notification.setUserId(email);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setLink(link);
            notificationRepository.save(notification);
            messagingTemplate.convertAndSend("/topic/notifications/" + email, notification);
        }
    }
}
