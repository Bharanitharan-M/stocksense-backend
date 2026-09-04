package com.stocksense.stocksense_backend.dtos;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class AIDiscoveryResponseDto {

    @JsonProperty("relevance_outcome")
    private String relevanceOutcome;

    @JsonProperty("headline")
    private String headline;

    @JsonProperty("summary_5_sec")
    private String summary5Sec;

    @JsonProperty("breakdown_30_sec")
    private String breakdown30Sec;

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("sector")
    private String sector;

    @JsonProperty("source_url")
    private String sourceUrl;
}
