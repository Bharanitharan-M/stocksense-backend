package com.stocksense.stocksense_backend.controllers;

import com.stocksense.stocksense_backend.models.Insight;
import com.stocksense.stocksense_backend.repositories.InsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@CrossOrigin("*")
public class FeedController {

    @Autowired
    private com.stocksense.stocksense_backend.repositories.InsightRepository insightRepository;

    @Autowired
    private com.stocksense.stocksense_backend.repositories.UserRepository userRepository;

    @Autowired
    private com.stocksense.stocksense_backend.services.WatchlistService watchlistService;

    @GetMapping
    public ResponseEntity<List<Insight>> getMainFeed(
            @org.springframework.security.core.annotation.AuthenticationPrincipal String email,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "latest") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        org.springframework.data.domain.Sort sort = "confidence".equalsIgnoreCase(sortBy) ? 
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "confidenceScore") : 
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
            
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<Insight> insightPage;
        
        if (ticker != null && !ticker.trim().isEmpty()) {
            // Filter feed by a specific ticker (from query param)
            List<String> explicitTickers = List.of(ticker, ticker.replaceAll("\\s+", ""));
            
            if (query != null && !query.trim().isEmpty()) {
                insightPage = insightRepository.searchWatchlistInsights(
                    query.trim(), Insight.Status.PUBLISHED, explicitTickers, pageRequest
                );
            } else {
                insightPage = insightRepository.findByStatusAndTickerIn(
                    Insight.Status.PUBLISHED, explicitTickers, pageRequest
                );
            }
        } else {
            // Fetch user's entire watchlist
            List<String> userWatchlist = userRepository.findByEmail(email)
                    .map(com.stocksense.stocksense_backend.models.User::getWatchlistTickers)
                    .orElse(new java.util.ArrayList<>());

            if (userWatchlist != null && !userWatchlist.isEmpty()) {
                List<String> expandedWatchlist = new java.util.ArrayList<>();
                for (String t : userWatchlist) {
                    expandedWatchlist.add(t);
                    expandedWatchlist.add(t.replaceAll("\\s+", ""));
                }
                
                if (query != null && !query.trim().isEmpty()) {
                    insightPage = insightRepository.searchWatchlistInsights(
                        query.trim(), Insight.Status.PUBLISHED, expandedWatchlist, pageRequest
                    );
                } else {
                    insightPage = insightRepository.findByStatusAndTickerIn(
                        Insight.Status.PUBLISHED, expandedWatchlist, pageRequest
                    );
                }
            } else {
                // If no watchlist, return empty feed (Feed ONLY shows Watchlist)
                insightPage = Page.empty(pageRequest);
            }
        }
        
        return ResponseEntity.ok(insightPage.getContent());
    }

    @PostMapping("/scrape")
    public ResponseEntity<Map<String, String>> scrapeArticle(@RequestBody Map<String, String> payload) {
        String url = payload.get("url");
        if (url == null || url.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL is required"));
        }
        
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();
            
            // Remove common noise elements
            doc.select("script, style, noscript, nav, footer, header, aside, .subscribe, .ad, .advertisement, .related, .social, .paywall, .newsletter, iframe, .comments").remove();
            
            // Append periods to block elements so the TTS engine pauses between paragraphs
            doc.select("p, h1, h2, h3, h4, h5, h6, li").append(". ");
            doc.select("br").append(". ");
            
            String text = "";
            // Prioritize specific known content classes first (like ET's .artText and .blogSysn)
            if (doc.selectFirst(".artText") != null) {
                text = doc.select(".artText").text();
            } else if (doc.selectFirst(".blogSysn") != null) {
                text = doc.select(".blogSysn").text();
            } else if (doc.selectFirst(".pageContent") != null) {
                text = doc.select(".pageContent").text();
            } else if (doc.selectFirst(".article-body") != null) {
                text = doc.select(".article-body").text();
            } else if (doc.selectFirst(".story-content") != null) {
                text = doc.select(".story-content").text();
            } else if (doc.selectFirst("article") != null) {
                text = doc.select("article").text();
            } else if (doc.selectFirst("main") != null) {
                text = doc.select("main").text();
            } else {
                text = doc.body().text();
            }
            
            // Clean up multiple periods and common leftover junk phrases
            text = text.replaceAll("\\.\\s*\\.", ".").replaceAll("\\s+", " ");
            text = text.replaceAll("(?i)(subscribe now|read more|click here|follow us on|download our app|advertisement)", "");
            
            if (text.length() > 5000) {
                text = text.substring(0, 5000) + "...";
            }
            
            return ResponseEntity.ok(Map.of("text", text));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to extract article text: " + e.getMessage()));
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<List<Insight>> getInsightsBatch(@RequestBody Map<String, List<String>> payload) {
        List<String> ids = payload.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        
        // Limit to 50 insights max to prevent abuse
        if (ids.size() > 50) {
            ids = ids.subList(0, 50);
        }
        
        List<Insight> insights = insightRepository.findByIdIn(ids);
        return ResponseEntity.ok(insights);
    }
}
