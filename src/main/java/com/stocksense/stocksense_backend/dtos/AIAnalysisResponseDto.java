package com.stocksense.stocksense_backend.dtos;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class AIAnalysisResponseDto {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("relevance_outcome")
    private String relevanceOutcome;

    @JsonProperty("relevance_metrics")
    private RelevanceMetricsDto relevanceMetrics;

    @JsonProperty("ai_analysis")
    private GenerationOutputDto aiAnalysis;

    @JsonProperty("metrics")
    private ValidationMetricsDto metrics;

    @Data
    public static class RelevanceMetricsDto {
        @JsonProperty("is_relevant")
        private boolean isRelevant;
        
        @JsonProperty("legal_pass")
        private boolean legalPass;
        
        @JsonProperty("algorithmic_pass")
        private boolean algorithmicPass;
        
        @JsonProperty("relevance_outcome")
        private String relevanceOutcome;
    }

    @Data
    public static class GenerationOutputDto {
        @JsonProperty("summary_5_sec")
        private String summary5Sec;
        
        @JsonProperty("breakdown_30_sec")
        private String breakdown30Sec;
        
        @JsonProperty("ticker")
        private String ticker;
        
        @JsonProperty("sector")
        private String sector;
    }

    @Data
    public static class ValidationMetricsDto {
        @JsonProperty("confidence_score")
        private int confidenceScore;
        
        @JsonProperty("safety_status")
        private String safetyStatus;
        
        @JsonProperty("status")
        private String status;
    }
}
