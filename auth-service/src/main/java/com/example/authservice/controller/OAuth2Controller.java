package com.example.authservice.controller;

import com.example.authservice.dto.AuthResponse;
import com.example.authservice.dto.UserDto;
import com.example.authservice.entity.OAuthProviderToken;
import com.example.authservice.repository.OAuthProviderTokenRepository;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.UserServiceClient;
import com.example.authservice.util.UsernameGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth/oauth2")
@Tag(name = "OAuth2 Authentication", description = "OAuth2 social login endpoints")
public class OAuth2Controller {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Controller.class);

    @Autowired
    private AuthService authService;
    
    @Autowired
    private OAuthProviderTokenRepository oauthProviderTokenRepository;
    
    @Autowired
    private UserServiceClient userServiceClient;

    /**
     * Initiate OAuth2 login for frontend applications
     * This endpoint returns the authorization URL instead of redirecting
     */
    @GetMapping("/authorize/{provider}")
    @Operation(summary = "Get OAuth2 authorization URL", 
               description = "Get the authorization URL for OAuth2 provider to redirect user to")
    public ResponseEntity<Map<String, Object>> getAuthorizationUrl(
            @PathVariable String provider,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String redirectUri,
            HttpServletRequest request) {
        
        try {
            // Validate provider
            if (!isValidProvider(provider)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid OAuth2 provider. Supported providers: google, github, facebook"));
            }

            // Build authorization URL
            String baseUrl = getBaseUrl(request);
            String authUrl = baseUrl + "/oauth2/authorization/" + provider;
            
            // Add state parameter for security (optional but recommended)
            String state = generateState();
            
            Map<String, Object> response = new HashMap<>();
            response.put("authorizationUrl", authUrl);
            response.put("provider", provider);
            response.put("state", state);
            response.put("redirectUri", redirectUri != null ? redirectUri : baseUrl + "/api/auth/oauth2/callback");
            
            logger.info("Generated OAuth2 authorization URL for provider: {}", provider);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error generating OAuth2 authorization URL for provider: {}", provider, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to generate authorization URL"));
        }
    }

    /**
     * Handle OAuth2 callback and return tokens as JSON
     * This is the endpoint that the OAuth2 provider will redirect to
     */
    @GetMapping("/callback")
    @Operation(summary = "Handle OAuth2 callback", 
               description = "Process OAuth2 callback and return authentication tokens")
    public ResponseEntity<?> handleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description,
            @RequestParam(required = false) String access_token,
            @RequestParam(required = false) String refresh_token,
            @RequestParam(required = false) String token_type,
            @RequestParam(required = false) String expires_in,
            @RequestParam(required = false) String user_id,
            HttpServletRequest request) {
        
        try {
            // Handle OAuth2 errors
            if (error != null) {
                logger.warn("OAuth2 error: {} - {}", error, error_description);
                return ResponseEntity.badRequest()
                    .body(Map.of("error", error, "error_description", error_description));
            }

            // Check if tokens are already in the URL (from OAuth2 success handler redirect)
            if (access_token != null && refresh_token != null) {
                logger.info("OAuth2 callback with tokens in URL - returning tokens directly");
                
                // Extract user info from JWT token
                try {
                    // Parse JWT token to get user information
                    String[] jwtParts = access_token.split("\\.");
                    if (jwtParts.length == 3) {
                        // Decode JWT payload (base64)
                        String payload = new String(java.util.Base64.getUrlDecoder().decode(jwtParts[1]));
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        Map<String, Object> claims = mapper.readValue(payload, Map.class);
                        logger.info("OAuth2 callback successful - returning tokens for user: {}", claims);
                        
                        // Extract user information from JWT claims
                        String provider = (String) claims.get("socialProvider");
                        String providerUserId = (String) claims.get("sub"); // Google's subject ID
                        String email = (String) claims.get("email");
                        String username = (String) claims.get("username");
                        Long userId = claims.get("userId") != null ? ((Number) claims.get("userId")).longValue() : null;
                        
                        // Get user details from User Service to ensure we have the latest data
                        UserDto userDto = null;
                        if (userId != null) {
                            try {
                                userDto = userServiceClient.getUserById(userId).orElse(null);
                                logger.info("Retrieved user from User Service: {}", userDto != null ? userDto.getUsername() : "not found");
                            } catch (Exception e) {
                                logger.warn("Failed to retrieve user from User Service: {}", e.getMessage());
                            }
                        }
                        
                        // Store OAuth2 provider tokens in database
                        if (provider != null && providerUserId != null && email != null && userId != null) {
                            storeOAuth2ProviderTokens(
                                userId,
                                provider,
                                providerUserId,
                                access_token,
                                refresh_token,
                                expires_in != null ? Long.parseLong(expires_in) : 3600
                            );
                            
                            logger.info("OAuth2 provider tokens stored for user: {} via provider: {}", email, provider);
                        }
                        
                        // Create proper AuthResponse
                        AuthResponse response = new AuthResponse(
                            access_token,
                            refresh_token,
                            expires_in != null ? Long.parseLong(expires_in) : 3600,
                            7 * 24 * 3600L, // 7 days for refresh token
                            userId,
                            userDto != null ? userDto.getUsername() : username,
                            userDto != null ? userDto.getEmail() : email,
                            true // OAuth2 emails are typically verified
                        );
                        
                        logger.info("OAuth2 callback successful - returning AuthResponse for user: {}", email);
                        return ResponseEntity.ok(response);
                    }
                } catch (Exception e) {
                    logger.error("Failed to parse JWT token from callback", e);
                    return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid token format"));
                }
            }
            
            // If no tokens in URL, return error
            logger.warn("No OAuth2 tokens found in callback URL");
            return ResponseEntity.badRequest()
                .body(Map.of("error", "OAuth2 callback failed - no tokens provided"));
            
        } catch (Exception e) {
            logger.error("OAuth2 callback processing failed", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "OAuth2 callback processing failed: " + e.getMessage()));
        }
    }

    private boolean isValidProvider(String provider) {
        return provider != null && 
               (provider.equalsIgnoreCase("google") || 
                provider.equalsIgnoreCase("github") || 
                provider.equalsIgnoreCase("facebook"));
    }

    private String generateState() {
        // Generate a random state parameter for CSRF protection
        return java.util.UUID.randomUUID().toString();
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        
        if ((scheme.equals("http") && serverPort != 80) || 
            (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        
        return url.toString();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Store OAuth2 provider tokens in the database
     */
    private void storeOAuth2ProviderTokens(Long userId, String provider, String providerUserId, 
                                         String accessToken, String refreshToken, long expiresInSeconds) {
        try {
            // Check if OAuth provider token already exists
            Optional<OAuthProviderToken> existingToken = oauthProviderTokenRepository
                .findByProviderAndProviderUserId(provider, providerUserId);
            
            OAuthProviderToken oauthToken;
            if (existingToken.isPresent()) {
                // Update existing token
                oauthToken = existingToken.get();
                oauthToken.setAccessToken(accessToken);
                oauthToken.setRefreshToken(refreshToken);
                oauthToken.setTokenExpiresAt(java.time.LocalDateTime.now().plusSeconds(expiresInSeconds));
                logger.info("Updated existing OAuth2 provider token for user: {} via provider: {}", userId, provider);
            } else {
                // Create new token
                oauthToken = new OAuthProviderToken();
                oauthToken.setUserId(userId);
                oauthToken.setProvider(provider);
                oauthToken.setProviderUserId(providerUserId);
                oauthToken.setAccessToken(accessToken);
                oauthToken.setRefreshToken(refreshToken);
                oauthToken.setTokenExpiresAt(java.time.LocalDateTime.now().plusSeconds(expiresInSeconds));
                logger.info("Created new OAuth2 provider token for user: {} via provider: {}", userId, provider);
            }
            
            oauthProviderTokenRepository.save(oauthToken);
            
        } catch (Exception e) {
            logger.error("Failed to store OAuth2 provider tokens for user: {} via provider: {}", userId, provider, e);
            // Don't throw exception as this shouldn't break the login flow
        }
    }
}
