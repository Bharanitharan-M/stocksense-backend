package com.stocksense.stocksense_backend.controllers;

import com.stocksense.stocksense_backend.dtos.AuthResponse;
import com.stocksense.stocksense_backend.services.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Google OAuth2 login.
     * Creates a server-side session and returns accessToken + sessionId.
     * The sessionId is stored in an HttpOnly cookie by the Next.js proxy.
     */
    @PostMapping("/google")
    public ResponseEntity<?> authenticateGoogle(
            @RequestBody TokenRequest request,
            HttpServletRequest httpRequest) {
        try {
            String userAgent = httpRequest.getHeader("User-Agent");
            String ipAddress = httpRequest.getRemoteAddr();
            AuthResponse authResponse = authService.authenticateWithGoogle(
                    request.getCredential(), userAgent, ipAddress);
            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(401).body(
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "Authentication failed"));
        }
    }

    /**
     * Refresh the access token using a sessionId.
     * The Next.js proxy reads the sessionId from the HttpOnly cookie and sends it here.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody SessionRefreshRequest request) {
        try {
            String newAccessToken = authService.refreshAccessToken(request.getSessionId());
            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Session invalid or expired"));
        }
    }

    /**
     * Logout — invalidates the server-side session.
     * The Next.js proxy reads sessionId from cookie, sends it here, then clears the cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody SessionRefreshRequest request) {
        try {
            if (request.getSessionId() != null) {
                authService.invalidateSession(request.getSessionId());
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            // Even if session not found, return success — client should clear cookie anyway
            return ResponseEntity.ok(Map.of("success", true));
        }
    }

    /**
     * Get current authenticated user profile.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            String token = authHeader.substring(7);
            com.stocksense.stocksense_backend.security.JwtService jwtService = authService.getJwtService();
            String email = jwtService.extractUsername(token);
            if (email != null && jwtService.isTokenValid(token, email)) {
                return authService.getUserByEmail(email)
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.status(401).build());
            }
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }
}

@Data
class TokenRequest {
    private String credential;
}

@Data
class SessionRefreshRequest {
    private String sessionId;
}
