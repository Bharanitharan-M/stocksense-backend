package com.stocksense.stocksense_backend.services;

import com.stocksense.stocksense_backend.dtos.AuthResponse;
import com.stocksense.stocksense_backend.models.Session;
import com.stocksense.stocksense_backend.models.User;
import com.stocksense.stocksense_backend.repositories.SessionRepository;
import com.stocksense.stocksense_backend.repositories.UserRepository;
import com.stocksense.stocksense_backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final JwtService jwtService;

    public JwtService getJwtService() {
        return jwtService;
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Authenticate via Google OAuth2.
     * Upserts the user, creates a new server-side session, and returns
     * the accessToken + sessionId (NOT the refreshToken — that stays in the DB).
     */
    public AuthResponse authenticateWithGoogle(String credential, String userAgent, String ipAddress) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(credential);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v3/userinfo",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> payload = response.getBody();
                String email = (String) payload.get("email");
                String name = (String) payload.get("name");
                String pictureUrl = (String) payload.get("picture");

                // Upsert user
                Optional<User> userOptional = userRepository.findByEmail(email);
                User user;
                if (userOptional.isPresent()) {
                    user = userOptional.get();
                    user.setLastLogin(LocalDateTime.now());
                    userRepository.save(user);
                } else {
                    user = User.builder()
                            .email(email)
                            .name(name)
                            .pictureUrl(pictureUrl)
                            .createdAt(LocalDateTime.now())
                            .lastLogin(LocalDateTime.now())
                            .build();
                    userRepository.save(user);
                }

                // Generate tokens
                String accessToken = jwtService.generateToken(user.getEmail());
                String refreshToken = jwtService.generateRefreshToken(user.getEmail());

                // Create server-side session — refreshToken NEVER leaves the server
                String sessionId = UUID.randomUUID().toString();
                Session session = Session.builder()
                        .sessionId(sessionId)
                        .userId(user.getId())
                        .userEmail(user.getEmail())
                        .refreshToken(refreshToken)
                        .createdAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60 * 60 * 24 * 7)) // 7 days
                        .userAgent(userAgent)
                        .ipAddress(ipAddress)
                        .build();
                sessionRepository.save(session);

                return AuthResponse.builder()
                        .accessToken(accessToken)
                        .sessionId(sessionId)
                        .userId(user.getId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .pictureUrl(user.getPictureUrl())
                        .savedInsights(user.getSavedInsights())
                        .build();
            } else {
                throw new Exception("Invalid Google Access token.");
            }
        } catch (Exception e) {
            throw new Exception("Error validating Google token: " + e.getMessage());
        }
    }

    /**
     * Refresh the access token using a sessionId.
     * Looks up the session in MongoDB, validates the stored refreshToken,
     * rotates it, and returns a new accessToken.
     */
    public String refreshAccessToken(String sessionId) throws Exception {
        Optional<Session> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new Exception("Session not found");
        }

        Session session = sessionOpt.get();

        // Check session expiry
        if (session.getExpiresAt().isBefore(Instant.now())) {
            sessionRepository.deleteBySessionId(sessionId);
            throw new Exception("Session expired");
        }

        // Validate the stored refresh token
        String storedRefreshToken = session.getRefreshToken();
        String email = jwtService.extractUsername(storedRefreshToken);
        if (email == null || !jwtService.isTokenValid(storedRefreshToken, email)) {
            sessionRepository.deleteBySessionId(sessionId);
            throw new Exception("Invalid session token");
        }

        // Rotate refresh token (new refresh token each time for security)
        String newRefreshToken = jwtService.generateRefreshToken(email);
        session.setRefreshToken(newRefreshToken);
        session.setExpiresAt(Instant.now().plusSeconds(60 * 60 * 24 * 7)); // Extend 7 more days
        sessionRepository.save(session);

        // Return a fresh access token
        return jwtService.generateToken(email);
    }

    /**
     * Invalidate a single session (single-device logout).
     */
    public void invalidateSession(String sessionId) {
        sessionRepository.deleteBySessionId(sessionId);
    }

    /**
     * Invalidate ALL sessions for a user (logout from all devices).
     */
    public void invalidateAllSessions(String userEmail) {
        sessionRepository.deleteByUserEmail(userEmail);
    }
}
