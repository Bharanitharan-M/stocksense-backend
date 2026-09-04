package com.stocksense.stocksense_backend.services;

import com.stocksense.stocksense_backend.dtos.DashboardOverviewDTO;
import com.stocksense.stocksense_backend.models.Insight;
import com.stocksense.stocksense_backend.models.User;
import com.stocksense.stocksense_backend.repositories.InsightRepository;
import com.stocksense.stocksense_backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InsightRepository insightRepository;
    private final UserRepository userRepository;

    public DashboardOverviewDTO getDashboardOverview(String userEmail) {
        // 1. Filings Processed Today (last 24 hours)
        Instant twentyFourHoursAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        long filingsProcessedToday = insightRepository.countByCreatedAtAfter(twentyFourHoursAgo);

        // 2. Active Watchlist Alerts
        List<String> userWatchlist = userRepository.findByEmail(userEmail)
                .map(User::getWatchlistTickers)
                .orElse(List.of());
        
        long activeWatchlistAlerts = 0;
        if (!userWatchlist.isEmpty()) {
            activeWatchlistAlerts = insightRepository.countByStatusAndTickerInAndCreatedAtAfter(
                    Insight.Status.PUBLISHED, userWatchlist, twentyFourHoursAgo);
        }

        // 3. Overall Confidence Score (Calculate average of latest 20 insights)
        Page<Insight> latestInsights = insightRepository.findByStatusOrderByCreatedAtDesc(Insight.Status.PUBLISHED, PageRequest.of(0, 20));
        int totalConfidence = 0;
        int count = 0;
        for (Insight insight : latestInsights) {
            totalConfidence += insight.getConfidenceScore();
            count++;
        }
        int overallConfidenceScore = count > 0 ? (totalConfidence / count) : 0;

        // 4. Briefing text & confidence
        String briefingConfidence = overallConfidenceScore > 85 ? "High" : (overallConfidenceScore > 60 ? "Medium" : "Low");
        
        // Dynamic Morning Briefing text based on latest headlines
        String morningBriefing = "No significant intelligence gathered yet.";
        if (count > 0) {
            List<Insight> content = latestInsights.getContent();
            morningBriefing = String.format("Recent market activity is highlighted by %s. Our systems are monitoring these developments with %s confidence. Active capital inflows and strategic partnerships remain key areas of focus today.", 
                content.get(0).getTicker(), briefingConfidence.toLowerCase());
        }

        // 5. Watchlist Radar (Latest 3 insights for tracked tickers)
        List<Insight> watchlistRadar = List.of();
        if (!userWatchlist.isEmpty()) {
            watchlistRadar = insightRepository.findByStatusAndTickerIn(
                    Insight.Status.PUBLISHED, userWatchlist, PageRequest.of(0, 3, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))).getContent();
        }

        // 6. Market Activity by Sector (Aggregation from latest 50 insights)
        Page<Insight> latest50 = insightRepository.findByStatusOrderByCreatedAtDesc(Insight.Status.PUBLISHED, PageRequest.of(0, 50));
        Map<String, Long> sectorActivity = latest50.getContent().stream()
                .filter(i -> i.getSector() != null && !i.getSector().isEmpty())
                .collect(Collectors.groupingBy(Insight::getSector, Collectors.counting()));

        // 7. Trending Tickers (Aggregation from latest 50 insights)
        Map<String, Long> trendingTickers = latest50.getContent().stream()
                .filter(i -> i.getTicker() != null && !i.getTicker().isEmpty() && !i.getTicker().equals("GENERAL"))
                .collect(Collectors.groupingBy(Insight::getTicker, Collectors.counting()));

        return DashboardOverviewDTO.builder()
                .activeWatchlistAlerts(activeWatchlistAlerts)
                .filingsProcessedToday(filingsProcessedToday)
                .overallConfidenceScore(overallConfidenceScore)
                .morningBriefing(morningBriefing)
                .briefingConfidence(briefingConfidence)
                .watchlistRadar(watchlistRadar)
                .sectorActivity(sectorActivity)
                .trendingTickers(trendingTickers)
                .build();
    }
}
