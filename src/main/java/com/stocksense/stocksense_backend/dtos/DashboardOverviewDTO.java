package com.stocksense.stocksense_backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardOverviewDTO {
    private long activeWatchlistAlerts;
    private long filingsProcessedToday;
    private int overallConfidenceScore;
    private String morningBriefing;
    private String briefingConfidence;
    
    // New Dashboard Widgets
    private java.util.List<com.stocksense.stocksense_backend.models.Insight> watchlistRadar;
    private java.util.Map<String, Long> sectorActivity;
    private java.util.Map<String, Long> trendingTickers;
}
