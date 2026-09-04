package com.stocksense.stocksense_backend.services;

import com.stocksense.stocksense_backend.models.User;
import com.stocksense.stocksense_backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistScheduler {

    private final UserRepository userRepository;
    private final DiscoveryService discoveryService;

    // Run every 2 minutes for testing purposes
    // In production, this would be @Scheduled(cron = "0 0 */6 * * *") for every 6 hours
    @Scheduled(fixedRate = 120000)
    public void runAutomatedDiscovery() {
        log.info("Starting automated Watchlist AI discovery sweep...");
        
        List<User> allUsers = userRepository.findAll();
        
        // Map tickers to the list of user emails who are tracking them
        java.util.Map<String, java.util.List<String>> tickerToUsersMap = new java.util.HashMap<>();
        
        for (User user : allUsers) {
            List<String> watchlisted = user.getWatchlistTickers();
            if (watchlisted == null || watchlisted.isEmpty()) {
                continue;
            }
            for (String ticker : watchlisted) {
                tickerToUsersMap.computeIfAbsent(ticker, k -> new java.util.ArrayList<>()).add(user.getEmail());
            }
        }
        
        log.info("Unique tickers to process: {}", tickerToUsersMap.keySet());
        
        for (java.util.Map.Entry<String, java.util.List<String>> entry : tickerToUsersMap.entrySet()) {
            String ticker = entry.getKey();
            java.util.List<String> userEmails = entry.getValue();
            discoveryService.discoverAndProcessAsyncBatched(ticker, userEmails);
        }
        
        log.info("Automated Watchlist AI discovery sweep initiated.");
    }
}
