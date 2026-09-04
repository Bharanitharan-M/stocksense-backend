package com.stocksense.stocksense_backend.services;

import com.stocksense.stocksense_backend.dtos.AIAnalysisResponseDto;
import com.stocksense.stocksense_backend.dtos.AIDiscoveryResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.UUID;

@Service
public class AiEngineClientService {

    private static final Logger logger = LoggerFactory.getLogger(AiEngineClientService.class);
    
    private final RestClient restClient;

    public AiEngineClientService(@Value("${ai.engine.base-url}") String engineBaseUrl) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(600000);
        
        this.restClient = RestClient.builder()
                .baseUrl(engineBaseUrl)
                .requestFactory(factory)
                .build();
        logger.info("Initialized AI Engine Client pointing to: {} with 120s timeout", engineBaseUrl);
    }

    public AIAnalysisResponseDto processNewsDocument(String headline, String content) {
        String transactionId = "txn-" + UUID.randomUUID().toString().substring(0, 8);
        logger.info("Sending document [{}] to AI Engine...", transactionId);
        
        Map<String, String> payload = Map.of(
            "transaction_id", transactionId,
            "headline", headline,
            "content", content
        );

        try {
            AIAnalysisResponseDto response = restClient.post()
                    .uri("/process")
                    .body(payload)
                    .retrieve()
                    .body(AIAnalysisResponseDto.class);
                    
            logger.info("Received AI Response for [{}]: Relevance={}, Status={}", 
                        transactionId, 
                        response.getRelevanceOutcome(), 
                        response.getMetrics() != null ? response.getMetrics().getStatus() : "N/A");
            return response;
        } catch (Exception e) {
            logger.error("Failed to connect to Python AI Engine: {}", e.getMessage());
            throw new RuntimeException("AI Engine Unavailable", e);
        }
    }

    public AIDiscoveryResponseDto discoverNewsOnDemand(String query) {
        logger.info("Triggering AI search discovery for query: '{}'...", query);
        Map<String, String> payload = Map.of("query", query);

        try {
            AIDiscoveryResponseDto response = restClient.post()
                    .uri("/discover")
                    .body(payload)
                    .retrieve()
                    .body(AIDiscoveryResponseDto.class);
                    
            logger.info("Received AI Discovery response. Relevance={}", response.getRelevanceOutcome());
            return response;
        } catch (Exception e) {
            logger.error("Failed to connect to Python AI Engine discover endpoint: {}", e.getMessage());
            throw new RuntimeException("AI Engine Discovery Unavailable", e);
        }
    }

    public void triggerDiscoverAsync(String query, java.util.List<String> userEmails, String topUrl) {
        logger.info("Triggering Async AI search discovery for query: '{}'...", query);
        Map<String, Object> payload = Map.of(
            "query", query,
            "userEmails", userEmails,
            "top_url", topUrl
        );

        try {
            restClient.post()
                    .uri("/discover-async")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            logger.info("Async discovery request accepted by AI Engine.");
        } catch (Exception e) {
            logger.error("Failed to trigger async AI Engine discover endpoint: {}", e.getMessage());
            throw new RuntimeException("AI Engine Async Discovery Unavailable", e);
        }
    }
}
