package com.example.authservice.service;

import com.example.authservice.entity.RefreshToken;
import com.example.authservice.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    
    private final UserServiceClient userServiceClient;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    
    public AuthService(
            UserServiceClient userServiceClient,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userServiceClient = userServiceClient;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }
    
    @Transactional
    public AuthResponse register(String username, String email, String password, 
                                String clientId, String deviceInfo, String ipAddress) {
        try {
            // Create user via User Service
            UserDto userDto = new UserDto();
            userDto.setUsername(username);
            userDto.setEmail(email);
            UserDto createdUser = userServiceClient.createUser(userDto);
            
            // Store auth credentials in Auth Service
            // TODO: Implement password hashing and storage in auth_credentials table
            
            String accessToken = jwtService.generateAccessToken(createdUser);
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                createdUser.getId(), clientId, deviceInfo, ipAddress
            );
            
            logger.info("User registered successfully: {}", username);
            
            return new AuthResponse(
                accessToken,
                refreshToken.getId().toString(),
                jwtService.getAccessTokenExpiration(),
                refreshTokenService.getRefreshTokenExpiration(),
                createdUser.getId(),
                createdUser.getUsername(),
                createdUser.getEmail(),
                createdUser.getEmailVerified()
            );
        } catch (Exception e) {
            logger.error("Registration failed for username: {}", username, e);
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }
    
    @Transactional
    public AuthResponse login(String identifier, String password, 
                             String clientId, String deviceInfo, String ipAddress) {
        try {
            // Find user by username or email
            Optional<UserDto> userOpt = userServiceClient.getUserByUsername(identifier);
            if (userOpt.isEmpty()) {
                userOpt = userServiceClient.getUserByEmail(identifier);
            }
            
            if (userOpt.isEmpty()) {
                logger.warn("Login attempt with invalid identifier: {}", identifier);
                throw new RuntimeException("Invalid credentials");
            }
            
            UserDto user = userOpt.get();
            
            // TODO: Validate password against auth_credentials table
            // For now, we'll skip password validation as it needs to be implemented
            
            String accessToken = jwtService.generateAccessToken(user);
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                user.getId(), clientId, deviceInfo, ipAddress
            );
            
            logger.info("User logged in successfully: {}", user.getUsername());
            
            return new AuthResponse(
                accessToken,
                refreshToken.getId().toString(),
                jwtService.getAccessTokenExpiration(),
                refreshTokenService.getRefreshTokenExpiration(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getEmailVerified()
            );
        } catch (Exception e) {
            logger.error("Login failed for identifier: {}", identifier, e);
            throw new RuntimeException("Login failed: " + e.getMessage());
        }
    }
    
    @Transactional
    public AuthResponse socialLogin(String provider, String providerUserId, 
                                   String email, String username, 
                                   Map<String, Object> profile,
                                   String clientId, String deviceInfo, String ipAddress) {
        try {
            // TODO: Implement social login via User Service
            // UserDto user = userServiceClient.createOrUpdateSocialUser(
            //     provider, providerUserId, email, username, profile
            // );
            throw new RuntimeException("Social login not implemented yet");
            
            // TODO: Implement the rest of social login
            // Map<String, Object> additionalClaims = new HashMap<>();
            // additionalClaims.put("socialProvider", provider);
            // 
            // String accessToken = jwtService.generateAccessToken(user, additionalClaims);
            // RefreshToken refreshToken = refreshTokenService.createRefreshToken(
            //     user.getId(), clientId, deviceInfo, ipAddress
            // );
            // 
            // logger.info("Social login successful for user: {} via {}", user.getUsername(), provider);
            // 
            // return new AuthResponse(
            //     accessToken,
            //     refreshToken.getId().toString(),
            //     jwtService.getAccessTokenExpiration(),
            //     refreshTokenService.getRefreshTokenExpiration(),
            //     user.getId(),
            //     user.getUsername(),
            //     user.getEmail(),
            //     user.getEmailVerified()
            // );
        } catch (Exception e) {
            logger.error("Social login failed for provider: {} and user: {}", provider, providerUserId, e);
            throw new RuntimeException("Social login failed: " + e.getMessage());
        }
    }
    
    @Transactional
    public AuthResponse refreshToken(String refreshTokenId, String deviceInfo, String ipAddress) {
        try {
            UUID tokenId = UUID.fromString(refreshTokenId);
            Optional<RefreshToken> tokenOpt = refreshTokenService.validateRefreshToken(tokenId);
            
            if (tokenOpt.isEmpty()) {
                logger.warn("Invalid refresh token used: {}", refreshTokenId);
                throw new RuntimeException("Invalid refresh token");
            }
            
            RefreshToken oldToken = tokenOpt.get();
            Long userId = oldToken.getUserId();
            
            // Get user information from User Service
            Optional<UserDto> userOpt = userServiceClient.getUserById(userId);
            if (userOpt.isEmpty()) {
                logger.warn("User not found for refresh token: {}", userId);
                throw new RuntimeException("User not found");
            }
            UserDto user = userOpt.get();
            
            // Generate new tokens
            String accessToken = jwtService.generateAccessToken(user);
            RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(
                oldToken, deviceInfo, ipAddress
            );
            
            logger.info("Tokens refreshed successfully for user: {}", user.getUsername());
            
            return new AuthResponse(
                accessToken,
                newRefreshToken.getId().toString(),
                jwtService.getAccessTokenExpiration(),
                refreshTokenService.getRefreshTokenExpiration(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getEmailVerified()
            );
        } catch (Exception e) {
            logger.error("Token refresh failed for token: {}", refreshTokenId, e);
            throw new RuntimeException("Token refresh failed: " + e.getMessage());
        }
    }
    
    @Transactional
    public void logout(String accessToken, String refreshTokenId) {
        try {
            // Revoke access token
            if (accessToken != null) {
                jwtService.revokeToken(accessToken);
            }
            
            // Revoke refresh token
            if (refreshTokenId != null) {
                refreshTokenService.revokeToken(UUID.fromString(refreshTokenId));
            }
            
            logger.info("User logged out successfully");
        } catch (Exception e) {
            logger.error("Logout failed: {}", e.getMessage());
            throw new RuntimeException("Logout failed: " + e.getMessage());
        }
    }
    
    @Transactional
    public void logoutAll(Long userId) {
        try {
            refreshTokenService.revokeAllTokensForUser(userId);
            logger.info("All sessions logged out for user: {}", userId);
        } catch (Exception e) {
            logger.error("Logout all failed for user: {}", userId, e);
            throw new RuntimeException("Logout all failed: " + e.getMessage());
        }
    }
    
    public boolean validateToken(String token) {
        try {
            String username = jwtService.extractUsername(token);
            Optional<UserDto> userOpt = userServiceClient.getUserByUsername(username);
            
            return userOpt.isPresent() && jwtService.isTokenValid(token, userOpt.get());
        } catch (Exception e) {
            logger.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    public Optional<UserDto> getUserFromToken(String token) {
        try {
            String username = jwtService.extractUsername(token);
            return userServiceClient.getUserByUsername(username);
        } catch (Exception e) {
            logger.error("Failed to extract user from token: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    // Inner class for authentication response
    public static class AuthResponse {
        private final String accessToken;
        private final String refreshToken;
        private final long accessTokenExpiresIn;
        private final long refreshTokenExpiresIn;
        private final Long userId;
        private final String username;
        private final String email;
        private final Boolean emailVerified;
        
        public AuthResponse(String accessToken, String refreshToken, 
                           long accessTokenExpiresIn, long refreshTokenExpiresIn,
                           Long userId, String username, String email, Boolean emailVerified) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.accessTokenExpiresIn = accessTokenExpiresIn;
            this.refreshTokenExpiresIn = refreshTokenExpiresIn;
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.emailVerified = emailVerified;
        }
        
        // Getters
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public long getAccessTokenExpiresIn() { return accessTokenExpiresIn; }
        public long getRefreshTokenExpiresIn() { return refreshTokenExpiresIn; }
        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public Boolean getEmailVerified() { return emailVerified; }
    }
}