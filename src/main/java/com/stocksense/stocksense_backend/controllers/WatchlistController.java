package com.stocksense.stocksense_backend.controllers;

import com.stocksense.stocksense_backend.services.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
@CrossOrigin("*")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final com.stocksense.stocksense_backend.services.AiEngineClientService aiClient;

    @Autowired
    private com.stocksense.stocksense_backend.repositories.InsightRepository insightRepository;

    @GetMapping
    public ResponseEntity<List<String>> getWatchlist(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(watchlistService.getWatchlist(email));
    }

    @GetMapping("/stats")
    public ResponseEntity<List<Map<String, Object>>> getWatchlistStats(@org.springframework.security.core.annotation.AuthenticationPrincipal String email) {
        List<String> tickers = watchlistService.getWatchlist(email);
        List<Map<String, Object>> stats = new java.util.ArrayList<>();
        for (String ticker : tickers) {
            List<String> searchTickers = List.of(ticker, ticker.replaceAll("\\s+", ""));
            
            // Total insights
            long count = insightRepository.countByStatusAndTickerInAndCreatedAtAfter(
                com.stocksense.stocksense_backend.models.Insight.Status.PUBLISHED,
                searchTickers,
                java.time.Instant.ofEpochMilli(0)
            );
            
            // Trending calculation (Insights in the last 24 hours)
            long recentCount = insightRepository.countByStatusAndTickerInAndCreatedAtAfter(
                com.stocksense.stocksense_backend.models.Insight.Status.PUBLISHED,
                searchTickers,
                java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS)
            );
            boolean isTrending = recentCount > 2; // Threshold for "Trending" badge
            
            org.springframework.data.domain.Page<com.stocksense.stocksense_backend.models.Insight> latest = insightRepository.findByStatusAndTickerIn(
                com.stocksense.stocksense_backend.models.Insight.Status.PUBLISHED,
                searchTickers,
                org.springframework.data.domain.PageRequest.of(0, 1, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
            );
            
            String latestHeadline = "";
            String sector = "Market";
            if (latest.hasContent()) {
                latestHeadline = latest.getContent().get(0).getHeadline();
                if (latest.getContent().get(0).getSector() != null) {
                    sector = latest.getContent().get(0).getSector();
                }
            }
            
            stats.add(Map.of(
                "ticker", ticker, 
                "count", count, 
                "latestHeadline", latestHeadline,
                "isTrending", isTrending,
                "sector", sector
            ));
        }
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/{ticker}")
    public ResponseEntity<?> addTickerToWatchlist(@AuthenticationPrincipal String email, @PathVariable String ticker) {
        try {
            List<String> watchlist = watchlistService.addTickerToWatchlist(email, ticker);
            // Trigger an immediate asynchronous discovery for the new ticker!
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    aiClient.triggerDiscoverAsync(ticker, List.of(email), "");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return ResponseEntity.ok(watchlist);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{ticker}")
    public ResponseEntity<List<String>> removeTickerFromWatchlist(@AuthenticationPrincipal String email, @PathVariable String ticker) {
        List<String> watchlist = watchlistService.removeTickerFromWatchlist(email, ticker);
        return ResponseEntity.ok(watchlist);
    }
}
