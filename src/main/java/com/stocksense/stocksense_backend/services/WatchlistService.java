package com.stocksense.stocksense_backend.services;

import com.stocksense.stocksense_backend.models.User;
import com.stocksense.stocksense_backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final UserRepository userRepository;

    public List<String> getWatchlist(String email) {
        return userRepository.findByEmail(email)
                .map(User::getWatchlistTickers)
                .orElse(new ArrayList<>());
    }

    public List<String> addTickerToWatchlist(String email, String ticker) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> watchlist = user.getWatchlistTickers();
        if (watchlist == null) {
            watchlist = new ArrayList<>();
        }

        String normalizedTicker = ticker.trim().toUpperCase();

        if (watchlist.contains(normalizedTicker)) {
            return watchlist; // Already exists
        }

        if (watchlist.size() >= 5) {
            throw new RuntimeException("Maximum watchlist size of 5 reached.");
        }

        watchlist.add(normalizedTicker);
        user.setWatchlistTickers(watchlist);
        userRepository.save(user);

        return watchlist;
    }

    public List<String> removeTickerFromWatchlist(String email, String ticker) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> watchlist = user.getWatchlistTickers();
        if (watchlist != null) {
            watchlist.removeIf(t -> t.equalsIgnoreCase(ticker.trim()));
            user.setWatchlistTickers(watchlist);
            userRepository.save(user);
        }

        return watchlist != null ? watchlist : new ArrayList<>();
    }
}
