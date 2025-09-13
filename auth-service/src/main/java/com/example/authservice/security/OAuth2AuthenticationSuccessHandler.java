package com.example.authservice.security;

import com.example.authservice.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);
    
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    
    @Value("${app.oauth2.authorizedRedirectUris:http://localhost:3000/auth/callback}")
    private String redirectUri;
    
    public OAuth2AuthenticationSuccessHandler(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        
        try {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            String registrationId = getRegistrationId(request);
            
            logger.info("OAuth2 authentication successful for provider: {}", registrationId);
            
            // Extract user information based on provider
            String email = extractEmail(oauth2User, registrationId);
            String name = extractName(oauth2User, registrationId);
            String providerId = extractProviderId(oauth2User, registrationId);
            
            if (email == null || providerId == null) {
                logger.error("Failed to extract required user information from OAuth2 provider: {}", registrationId);
                handleAuthenticationFailure(response, "Failed to extract user information");
                return;
            }
            
            // Get device info from request
            String deviceInfo = extractDeviceInfo(request);
            String clientId = request.getParameter("client_id");
            if (clientId == null) {
                clientId = "web-client";
            }
            
            // Perform social login
            Map<String, Object> profile = oauth2User.getAttributes();
            var authResponse = authService.socialLogin(
                    registrationId,
                    providerId,
                    email,
                    name,
                    profile,
                    clientId,
                    deviceInfo,
                    getClientIpAddress(request)
            );
            
            // Build redirect URL with tokens
            String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("access_token", authResponse.getAccessToken())
                    .queryParam("refresh_token", authResponse.getRefreshToken())
                    .queryParam("token_type", "Bearer")
                    .queryParam("expires_in", authResponse.getAccessTokenExpiresIn())
                    .queryParam("user_id", authResponse.getUserId())
                    .build().toUriString();
            
            logger.info("Redirecting user to: {}", redirectUri);
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
            
        } catch (Exception e) {
            logger.error("OAuth2 authentication success handling failed", e);
            handleAuthenticationFailure(response, "Authentication processing failed");
        }
    }
    
    private String getRegistrationId(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        // Extract registration ID from URI like /login/oauth2/code/google
        String[] parts = requestUri.split("/");
        if (parts.length >= 4) {
            return parts[parts.length - 1];
        }
        return "unknown";
    }
    
    private String extractEmail(OAuth2User oauth2User, String registrationId) {
        switch (registrationId.toLowerCase()) {
            case "google":
                return oauth2User.getAttribute("email");
            case "github":
                return oauth2User.getAttribute("email");
            case "facebook":
                return oauth2User.getAttribute("email");
            default:
                return oauth2User.getAttribute("email");
        }
    }
    
    private String extractName(OAuth2User oauth2User, String registrationId) {
        switch (registrationId.toLowerCase()) {
            case "google":
                return oauth2User.getAttribute("name");
            case "github":
                return oauth2User.getAttribute("name");
            case "facebook":
                return oauth2User.getAttribute("name");
            default:
                return oauth2User.getAttribute("name");
        }
    }
    
    private String extractProviderId(OAuth2User oauth2User, String registrationId) {
        switch (registrationId.toLowerCase()) {
            case "google":
                return oauth2User.getAttribute("sub");
            case "github":
                Object id = oauth2User.getAttribute("id");
                return id != null ? id.toString() : null;
            case "facebook":
                return oauth2User.getAttribute("id");
            default:
                Object defaultId = oauth2User.getAttribute("id");
                return defaultId != null ? defaultId.toString() : oauth2User.getAttribute("sub");
        }
    }
    
    private String extractDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String clientIp = getClientIpAddress(request);
        
        return String.format("OAuth2 Login - IP: %s, User-Agent: %s", 
                clientIp != null ? clientIp : "unknown", 
                userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 200)) : "unknown");
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
    
    private void handleAuthenticationFailure(HttpServletResponse response, String error) throws IOException {
        String errorUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", URLEncoder.encode(error, StandardCharsets.UTF_8))
                .build().toUriString();
        
        response.sendRedirect(errorUrl);
    }
}