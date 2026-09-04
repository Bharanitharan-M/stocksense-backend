package com.stocksense.stocksense_backend.controllers;

import com.stocksense.stocksense_backend.dtos.AIAnalysisResponseDto;
import com.stocksense.stocksense_backend.services.AiEngineClientService;
import com.stocksense.stocksense_backend.services.LiveNewsFeedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test")
public class TestIntegrationController {

    @Autowired
    private AiEngineClientService aiEngineClientService;
    
    @Autowired
    private LiveNewsFeedService liveNewsFeedService;

    @GetMapping("/run-ai")
    public ResponseEntity<AIAnalysisResponseDto> testAiIntegration() {
        String headline = "Tata Motors to split commercial and passenger vehicle businesses";
        String content = "Tata Motors board has approved a proposal to demerge its business into two separate listed companies. One entity will house the commercial vehicles business and its related investments, while the other will encompass the passenger vehicles businesses, including electric vehicles and Jaguar Land Rover (JLR). This move aims to provide enhanced focus and agility for both segments.";
        
        AIAnalysisResponseDto result = aiEngineClientService.processNewsDocument(headline, content);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/run-real-ai")
    public ResponseEntity<AIAnalysisResponseDto> testRealAiIntegration() {
        // 1. Fetch real breaking news from CNBC
        LiveNewsFeedService.NewsArticle article = liveNewsFeedService.fetchLatestBreakingNews();
        
        // 2. Send the real news to our Python AI Engine
        AIAnalysisResponseDto result = aiEngineClientService.processNewsDocument(article.headline, article.content);
        
        return ResponseEntity.ok(result);
    }
}
