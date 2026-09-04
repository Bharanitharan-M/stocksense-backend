package com.stocksense.stocksense_backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LiveNewsFeedService {

    private static final Logger logger = LoggerFactory.getLogger(LiveNewsFeedService.class);
    
    // Indian stock market RSS feeds
    private static final List<String> INDIAN_RSS_FEEDS = List.of(
        "https://economictimes.indiatimes.com/markets/rssfeeds/2146842.cms",           // ET Markets
        "https://www.moneycontrol.com/rss/latestnews.xml",                             // Moneycontrol Latest
        "https://www.livemint.com/rss/markets",                                        // LiveMint Markets
        "https://www.business-standard.com/rss/markets-106.rss"                        // Business Standard
    );

    private final RestClient restClient;

    public LiveNewsFeedService() {
        this.restClient = RestClient.create();
    }

    /**
     * Fetch Google News RSS for a specific ticker to perform the 'Cheap Check'.
     */
    public List<NewsArticle> fetchGoogleNewsForTicker(String ticker) {
        String query = java.net.URLEncoder.encode(ticker + " stock news", java.nio.charset.StandardCharsets.UTF_8);
        String feedUrl = "https://news.google.com/rss/search?q=" + query + "&hl=en-IN&gl=IN&ceid=IN:en";
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            String xmlResponse = restClient.get()
                    .uri(feedUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .body(String.class);

            if (xmlResponse != null && !xmlResponse.isEmpty()) {
                Pattern itemPattern = Pattern.compile("(?s)<item>(.*?)</item>");
                Matcher itemMatcher = itemPattern.matcher(xmlResponse);
                
                int count = 0;
                while (itemMatcher.find() && count < 6) { // Only check top 6
                    String itemXml = itemMatcher.group(1);
                    
                    Pattern titlePattern = Pattern.compile("(?s)<title>(.*?)</title>");
                    Matcher titleMatcher = titlePattern.matcher(itemXml);
                    String title = titleMatcher.find() ? cleanXmlString(titleMatcher.group(1)) : "Unknown Title";
                    
                    Pattern linkPattern = Pattern.compile("(?s)<link>(.*?)</link>");
                    Matcher linkMatcher = linkPattern.matcher(itemXml);
                    String link = linkMatcher.find() ? cleanXmlString(linkMatcher.group(1)) : "";

                    if (!title.equals("Unknown Title")) {
                        articles.add(new NewsArticle(title, "", link));
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to fetch Google News for {}: {}", ticker, e.getMessage());
        }
        return articles;
    }

    /**
     * Legacy method preserved for compatibility with test controllers.
     * Returns the first article from the aggregated Indian feeds.
     */
    public NewsArticle fetchLatestBreakingNews() {
        List<NewsArticle> articles = fetchIndianBreakingNews();
        if (!articles.isEmpty()) {
            return articles.get(0);
        }
        throw new RuntimeException("No news items found in Indian feeds");
    }

    /**
     * Fetches and aggregates latest news from multiple Indian financial RSS feeds.
     */
    public List<NewsArticle> fetchIndianBreakingNews() {
        logger.info("Fetching live Indian Financial RSS Feeds...");
        List<NewsArticle> aggregatedArticles = new ArrayList<>();

        for (String feedUrl : INDIAN_RSS_FEEDS) {
            try {
                logger.info("Fetching feed: {}", feedUrl);
                String xmlResponse = restClient.get()
                        .uri(feedUrl)
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                        .retrieve()
                        .body(String.class);

                if (xmlResponse == null || xmlResponse.isEmpty()) {
                    logger.warn("Empty response from feed: {}", feedUrl);
                    continue;
                }

                // Extract items
                Pattern itemPattern = Pattern.compile("(?s)<item>(.*?)</item>");
                Matcher itemMatcher = itemPattern.matcher(xmlResponse);
                
                int count = 0;
                while (itemMatcher.find()) {
                    String itemXml = itemMatcher.group(1);
                    
                    // Extract Title
                    Pattern titlePattern = Pattern.compile("(?s)<title>(.*?)</title>");
                    Matcher titleMatcher = titlePattern.matcher(itemXml);
                    String title = titleMatcher.find() ? cleanXmlString(titleMatcher.group(1)) : "Unknown Title";
                    
                    // Extract Description
                    Pattern descPattern = Pattern.compile("(?s)<description>(.*?)</description>");
                    Matcher descMatcher = descPattern.matcher(itemXml);
                    String description = descMatcher.find() ? cleanXmlString(descMatcher.group(1)) : "No description available";

                    // Extract Link
                    Pattern linkPattern = Pattern.compile("(?s)<link>(.*?)</link>");
                    Matcher linkMatcher = linkPattern.matcher(itemXml);
                    String link = linkMatcher.find() ? cleanXmlString(linkMatcher.group(1)) : "";

                    if (!title.equals("Unknown Title")) {
                        aggregatedArticles.add(new NewsArticle(title, description, link));
                        count++;
                    }
                }
                logger.info("Successfully fetched {} articles from feed: {}", count, feedUrl);

            } catch (Exception e) {
                logger.error("Failed to fetch feed: {}. Error: {}", feedUrl, e.getMessage());
            }
        }

        logger.info("Total aggregated articles from Indian feeds: {}", aggregatedArticles.size());
        return aggregatedArticles;
    }

    /**
     * Clean HTML tags, CDATA, and unescape common XML entities from RSS strings.
     */
    private String cleanXmlString(String input) {
        if (input == null) return "";
        
        // Remove CDATA wrappers
        if (input.startsWith("<![CDATA[") && input.endsWith("]]>")) {
            input = input.substring(9, input.length() - 3);
        } else {
            input = input.replace("<![CDATA[", "").replace("]]>", "");
        }
        
        // Unescape common HTML/XML entities
        input = input.replace("&amp;", "&")
                     .replace("&lt;", "<")
                     .replace("&gt;", ">")
                     .replace("&quot;", "\"")
                     .replace("&apos;", "'")
                     .replace("&#39;", "'")
                     .replace("&#96;", "`")
                     .replace("&ndash;", "-")
                     .replace("&mdash;", "-")
                     .replace("&#x27;", "'");
                     
        // Remove any HTML tags to keep it plain text
        input = input.replaceAll("<[^>]*>", "");
        
        return input.trim();
    }

    public static class NewsArticle {
        public final String headline;
        public final String content;
        public final String sourceUrl;

        public NewsArticle(String headline, String content, String sourceUrl) {
            this.headline = headline;
            this.content = content;
            this.sourceUrl = sourceUrl;
        }
    }
}
