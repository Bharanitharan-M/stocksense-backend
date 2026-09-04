package com.stocksense.stocksense_backend.repositories;

import com.stocksense.stocksense_backend.models.Insight;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InsightRepository extends MongoRepository<Insight, String> {
    
    // Check if an insight already exists for a given source url
    boolean existsBySourceUrl(String sourceUrl);
    
    // Find latest insight for a ticker
    Insight findFirstByTickerOrderByCreatedAtDesc(String ticker);
    
    // Check if an insight already exists for a given headline
    boolean existsByHeadline(String headline);
    
    // Delete existing insights for a ticker to prevent duplicates
    void deleteByTicker(String ticker);
    
    // Fetch feed sorted dynamically by Pageable
    Page<Insight> findByStatus(Insight.Status status, Pageable pageable);

    // Fetch feed sorted by creation date
    Page<Insight> findByStatusOrderByCreatedAtDesc(Insight.Status status, Pageable pageable);
    
    // Fetch feed filtered by user's watchlist tickers
    Page<Insight> findByStatusAndTickerIn(Insight.Status status, List<String> tickers, Pageable pageable);

    // Count insights created after a certain date
    long countByCreatedAtAfter(java.time.Instant date);

    // Count insights for specific tickers created after a certain date
    long countByStatusAndTickerInAndCreatedAtAfter(Insight.Status status, List<String> tickers, java.time.Instant date);

    // Search feed by query in headline, ticker, or summary
    @org.springframework.data.mongodb.repository.Query("{ 'status': ?1, $or: [ { 'headline': { $regex: ?0, $options: 'i' } }, { 'ticker': { $regex: ?0, $options: 'i' } }, { 'summary5Sec': { $regex: ?0, $options: 'i' } } ] }")
    Page<Insight> searchInsights(String query, Insight.Status status, Pageable pageable);

    // Search feed by query in headline or summary, filtered by specific tickers
    @org.springframework.data.mongodb.repository.Query("{ 'status': ?1, 'ticker': { $in: ?2 }, $or: [ { 'headline': { $regex: ?0, $options: 'i' } }, { 'summary5Sec': { $regex: ?0, $options: 'i' } } ] }")
    Page<Insight> searchWatchlistInsights(String query, Insight.Status status, List<String> tickers, Pageable pageable);
    
    // Find multiple insights by their IDs
    List<Insight> findByIdIn(List<String> ids);
}
