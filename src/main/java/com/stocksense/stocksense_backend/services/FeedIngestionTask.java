package com.stocksense.stocksense_backend.services;

import com.stocksense.stocksense_backend.dtos.AIAnalysisResponseDto;
import com.stocksense.stocksense_backend.models.Insight;
import com.stocksense.stocksense_backend.repositories.InsightRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class FeedIngestionTask {

    private static final Logger logger = LoggerFactory.getLogger(FeedIngestionTask.class);

    @Autowired
    private LiveNewsFeedService liveNewsFeedService;

    @Autowired
    private AiEngineClientService aiEngineClientService;

    @Autowired
    private InsightRepository insightRepository;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    // Run immediately on startup, then every 5 minutes (300,000 ms)
    @Scheduled(initialDelay = 5000, fixedRate = 300000)
    public void ingestLatestNews() {
        logger.info("=== Starting Scheduled Feed Ingestion ===");

        try {
            // 1. Fetch aggregated articles from Indian financial feeds
            List<LiveNewsFeedService.NewsArticle> articles = liveNewsFeedService.fetchIndianBreakingNews();
            logger.info("Retrieved {} articles from Indian feeds for processing", articles.size());

            int processedCount = 0;
            // Increased processing limit per cycle
            int maxItemsPerCycle = 15; 

            for (LiveNewsFeedService.NewsArticle article : articles) {
                if (processedCount >= maxItemsPerCycle) {
                    logger.info("Processed maximum limit of {} new articles this cycle. Stopping ingestion loop.", maxItemsPerCycle);
                    break;
                }

                // 2. Exact URL Deduplication check (Cheap Check)
                if (insightRepository.existsBySourceUrl(article.sourceUrl)) {
                    continue; // We already have this exact URL, skip entirely!
                }

                // 3. Similar Event Consolidation (Cheap Check)
                // If a different URL has the exact same headline, just append the URL and skip AI!
                if (insightRepository.existsByHeadline(article.headline)) {
                    Insight existing = insightRepository.findFirstByTickerOrderByCreatedAtDesc("GENERAL"); // Dummy fallback
                    // We need a proper query to find by headline. Since we don't have findFirstByHeadline, 
                    // we can just skip for now to save tokens, but ideally we'd append.
                    // For now, if headline exists but URL is different, we skip AI to save tokens.
                    continue;
                }

                logger.info("New breaking article found! Processing via AI Engine: '{}'", article.headline);

                try {
                    // 4. Send to Python AI Engine
                    AIAnalysisResponseDto aiResponse = aiEngineClientService.processNewsDocument(article.headline, article.content);

                    // 5. Map to Insight entity
                    Insight insight = new Insight();
                    insight.setHeadline(article.headline);
                    insight.setTransactionId(aiResponse.getTransactionId());
                    insight.setSourceUrl(article.sourceUrl);
                    
                    if (aiResponse.getAiAnalysis() != null) {
                        insight.setSummary5Sec(aiResponse.getAiAnalysis().getSummary5Sec());
                        insight.setBreakdown30Sec(aiResponse.getAiAnalysis().getBreakdown30Sec());
                        insight.setTicker(aiResponse.getAiAnalysis().getTicker() != null ? aiResponse.getAiAnalysis().getTicker().toUpperCase() : "GENERAL");
                        insight.setSector(aiResponse.getAiAnalysis().getSector() != null ? aiResponse.getAiAnalysis().getSector() : "Macro");
                    } else {
                        insight.setTicker("GENERAL");
                        insight.setSector("Macro");
                    }

                    if (aiResponse.getMetrics() != null) {
                        insight.setConfidenceScore(aiResponse.getMetrics().getConfidenceScore());
                    }

                    // Status handling based on Relevance
                    if ("RELEVANT".equals(aiResponse.getRelevanceOutcome())) {
                        insight.setStatus(Insight.Status.PUBLISHED);
                    } else {
                        // If not relevant, quarantine it
                        insight.setStatus(Insight.Status.QUARANTINE);
                    }
                    
                    insight.setCreatedAt(Instant.now());

                    // Deduplication logic: If we already have a recent insight for this specific company, just group it!
                    if (insight.getTicker() != null && !insight.getTicker().equals("GENERAL") && !insight.getTicker().equals("MACRO")) {
                        Insight existing = insightRepository.findFirstByTickerOrderByCreatedAtDesc(insight.getTicker());
                        if (existing != null && existing.getCreatedAt().isAfter(Instant.now().minus(12, java.time.temporal.ChronoUnit.HOURS))) {
                            logger.info("Deduplication triggered! Grouping redundant news for ticker {}", insight.getTicker());
                            
                            // Instead of importing MongoTemplate, we can just save via the repository
                            if (existing.getRelatedUrls() == null) {
                                existing.setRelatedUrls(new java.util.ArrayList<>());
                            }
                            if (insight.getSourceUrl() != null && !existing.getRelatedUrls().contains(insight.getSourceUrl())) {
                                existing.getRelatedUrls().add(insight.getSourceUrl());
                            }
                            insightRepository.save(existing);
                            continue; // Skip saving the new duplicate insight
                        }
                    }

                    // 5. Save to MongoDB
                    insightRepository.save(insight);
                    logger.info("Successfully persisted new Insight to MongoDB! [ID: {}, Ticker: {}, Sector: {}]", 
                                insight.getId(), insight.getTicker(), insight.getSector());

                    try {
                        messagingTemplate.convertAndSend("/topic/feed", insight);
                    } catch (Exception ex) {
                        logger.error("Failed to broadcast to /topic/feed", ex);
                    }

                    processedCount++;

                } catch (Exception e) {
                    logger.error("Failed to process article: '{}'. Error: {}", article.headline, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error during scheduled feed ingestion: {}", e.getMessage(), e);
        }
        
        logger.info("=== Scheduled Feed Ingestion Complete ===");
    }
}
