package com.example.authservice.service;

import com.example.authservice.entity.RefreshToken;
import com.example.authservice.entity.OAuthProviderToken;
import com.example.authservice.entity.AuthCredentials;
import com.example.authservice.repository.OAuthProviderTokenRepository;
import com.example.authservice.repository.AuthCredentialsRepository;
import com.example.authservice.dto.UserDto;
import com.example.authservice.util.UsernameGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final OAuthProviderTokenRepository oauthProviderTokenRepository;
    private final AuthCredentialsRepository authCredentialsRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;
    
    public AuthService(
            UserServiceClient userServiceClient,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            OAuthProviderTokenRepository oauthProviderTokenRepository,
            AuthCredentialsRepository authCredentialsRepository,
            PasswordEncoder passwordEncoder,
            UsernameGenerator usernameGenerator) {
        this.userServiceClient = userServiceClient;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.oauthProviderTokenRepository = oauthProviderTokenRepository;
        this.authCredentialsRepository = authCredentialsRepository;
        this.passwordEncoder = passwordEncoder;
        this.usernameGenerator = usernameGenerator;
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
            String passwordHash = passwordEncoder.encode(password);
            AuthCredentials authCredentials = new AuthCredentials(createdUser.getId(), passwordHash);
            authCredentialsRepository.save(authCredentials);
            
            logger.info("Auth credentials stored for user: {}", username);
            
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
            logger.info("Attempting login with identifier: {}", identifier);
            Optional<UserDto> userOpt;
            
            // Check if identifier looks like an email (contains @ symbol)
            if (identifier.contains("@")) {
                logger.info("Identifier appears to be an email, trying email lookup for: {}", identifier);
                userOpt = userServiceClient.getUserByEmail(identifier);
                logger.info("Email lookup result: {}", userOpt.isPresent() ? "found" : "not found");
                
                // If email lookup fails, also try username lookup as fallback
                if (userOpt.isEmpty()) {
                    logger.info("Email lookup failed, trying username lookup as fallback for: {}", identifier);
                    userOpt = userServiceClient.getUserByUsername(identifier);
                    logger.info("Username lookup result: {}", userOpt.isPresent() ? "found" : "not found");
                }
            } else {
                logger.info("Identifier appears to be a username, trying username lookup for: {}", identifier);
                userOpt = userServiceClient.getUserByUsername(identifier);
                logger.info("Username lookup result: {}", userOpt.isPresent() ? "found" : "not found");
                
                // If username lookup fails, also try email lookup as fallback
                if (userOpt.isEmpty()) {
                    logger.info("Username lookup failed, trying email lookup as fallback for: {}", identifier);
                    userOpt = userServiceClient.getUserByEmail(identifier);
                    logger.info("Email lookup result: {}", userOpt.isPresent() ? "found" : "not found");
                }
            }
            
            if (userOpt.isEmpty()) {
                logger.warn("Login attempt with invalid identifier: {}", identifier);
                throw new RuntimeException("Invalid credentials");
            }
            
            UserDto user = userOpt.get();
            
            // Validate password against auth_credentials table
            Optional<AuthCredentials> authCredentialsOpt = authCredentialsRepository.findActiveByUserId(user.getId());
            if (authCredentialsOpt.isEmpty()) {
                logger.warn("No auth credentials found for user: {}", user.getUsername());
                throw new RuntimeException("Invalid credentials");
            }
            
            AuthCredentials authCredentials = authCredentialsOpt.get();
            if (!passwordEncoder.matches(password, authCredentials.getPasswordHash())) {
                logger.warn("Invalid password for user: {}", user.getUsername());
                throw new RuntimeException("Invalid credentials");
            }
            
            if (!authCredentials.getEnabled()) {
                logger.warn("Account disabled for user: {}", user.getUsername());
                throw new RuntimeException("Account disabled");
            }
            
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
            // Check if OAuth provider token already exists
            Optional<OAuthProviderToken> existingToken = oauthProviderTokenRepository
                .findByProviderAndProviderUserId(provider, providerUserId);
            
            UserDto user;
            
            if (existingToken.isPresent()) {
                // User already exists with this OAuth provider
                Long userId = existingToken.get().getUserId();
                Optional<UserDto> userOpt = userServiceClient.getUserById(userId);
                if (userOpt.isEmpty()) {
                    throw new RuntimeException("User not found in User Service");
                }
                user = userOpt.get();
                logger.info("Existing social login user found: {} via {}", user.getUsername(), provider);
            } else {
                // Check if user exists by email
                Optional<UserDto> existingUserOpt = userServiceClient.getUserByEmail(email);
                
                if (existingUserOpt.isPresent()) {
                    // User exists, link this OAuth provider
                    user = existingUserOpt.get();
                    logger.info("Linking OAuth provider {} to existing user: {}", provider, user.getUsername());
                } else {
                    // Create new user with unique username
                    String uniqueUsername = generateUniqueUsername(email, username, provider, providerUserId);
                    
                    UserDto newUserDto = new UserDto();
                    newUserDto.setUsername(uniqueUsername);
                    newUserDto.setEmail(email);
                    newUserDto.setEmailVerified(true); // OAuth emails are typically verified
                    
                    // Extract additional profile info if available
                    if (profile.containsKey("given_name")) {
                        newUserDto.setFirstName((String) profile.get("given_name"));
                    }
                    if (profile.containsKey("family_name")) {
                        newUserDto.setLastName((String) profile.get("family_name"));
                    }
                    
                    user = userServiceClient.createUser(newUserDto);
                    logger.info("Created new user via social login: {} (generated from: {}) via {}", 
                               user.getUsername(), username, provider);
                }
                
                // Store OAuth provider token
                OAuthProviderToken oauthToken = new OAuthProviderToken();
                oauthToken.setUserId(user.getId());
                oauthToken.setProvider(provider);
                oauthToken.setProviderUserId(providerUserId);
                // Note: We don't store access tokens from OAuth2User as they're not available
                oauthProviderTokenRepository.save(oauthToken);
            }
            
            // Generate JWT tokens
            Map<String, Object> additionalClaims = new HashMap<>();
            additionalClaims.put("socialProvider", provider);
            
            String accessToken = jwtService.generateAccessToken(user, additionalClaims);
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                user.getId(), clientId, deviceInfo, ipAddress
            );
            
            logger.info("Social login successful for user: {} via {}", user.getUsername(), provider);
            
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
            // Revoke all refresh tokens in database
            refreshTokenService.revokeAllTokensForUser(userId);
            
            // Blacklist all access tokens for user in Redis
            jwtService.revokeAllUserTokens(userId);
            
            logger.info("All sessions logged out for user: {} (Redis + Database)", userId);
        } catch (Exception e) {
            logger.error("Logout all failed for user: {}", userId, e);
            throw new RuntimeException("Logout all failed: " + e.getMessage());
        }
    }
    
    public boolean validateToken(String token) {
        try {
            String username = jwtService.extractUsername(token);
            Optional<UserDto> userOpt = userServiceClient.getUserByUsername(username);
            
            if (userOpt.isEmpty()) {
                return false;
            }
            
            UserDto user = userOpt.get();
            
            // Check if auth credentials are enabled
            Optional<AuthCredentials> authCredentialsOpt = authCredentialsRepository.findActiveByUserId(user.getId());
            if (authCredentialsOpt.isEmpty() || !authCredentialsOpt.get().getEnabled()) {
                return false;
            }
            
            return jwtService.isTokenValid(token, user);
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
    
    /**
     * Generate a unique username for OAuth2 users
     */
    private String generateUniqueUsername(String email, String fullName, String provider, String providerId) {
        String baseUsername = usernameGenerator.generateUsername(email, fullName, provider, providerId);
        
        // Check if username is already taken
        int suffix = 1;
        String candidateUsername = baseUsername;
        
        while (true) {
            try {
                Optional<UserDto> existingUser = userServiceClient.getUserByUsername(candidateUsername);
                if (existingUser.isEmpty()) {
                    // Username is available
                    logger.info("Generated unique username: {} (base: {})", candidateUsername, baseUsername);
                    return candidateUsername;
                }
                
                // Username is taken, try with suffix
                candidateUsername = usernameGenerator.generateUniqueUsername(baseUsername, suffix);
                suffix++;
                
                // Prevent infinite loop
                if (suffix > 1000) {
                    logger.warn("Could not generate unique username after 1000 attempts, using UUID fallback");
                    return provider.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
                }
                
            } catch (Exception e) {
                logger.error("Error checking username availability: {}", candidateUsername, e);
                // Fallback to UUID-based username
                return provider.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
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