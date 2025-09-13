package com.example.authservice.service;

import com.example.authservice.entity.RefreshToken;
import com.example.authservice.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
    
    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
    
    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpiration;
    private final int maxTokensPerUser;
    
    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${app.refresh-token.max-per-user:5}") int maxTokensPerUser) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.maxTokensPerUser = maxTokensPerUser;
    }
    
    @Transactional
    public RefreshToken createRefreshToken(Long userId, String clientId, String deviceInfo, String ipAddress) {
        // Clean up old tokens if user has too many
        cleanupOldTokensForUser(userId, clientId);
        
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000);
        
        RefreshToken refreshToken = new RefreshToken(userId, clientId, deviceInfo, ipAddress, expiresAt);
        
        return refreshTokenRepository.save(refreshToken);
    }
    
    public Optional<RefreshToken> findByTokenId(UUID tokenId) {
        return refreshTokenRepository.findByIdAndRevokedFalse(tokenId);
    }
    
    public Optional<RefreshToken> validateRefreshToken(UUID tokenId) {
        Optional<RefreshToken> tokenOpt = findByTokenId(tokenId);
        
        if (tokenOpt.isEmpty()) {
            logger.warn("Refresh token not found or already revoked: {}", tokenId);
            return Optional.empty();
        }
        
        RefreshToken token = tokenOpt.get();
        
        if (token.isExpired()) {
            logger.warn("Refresh token expired: {}", tokenId);
            revokeToken(token);
            return Optional.empty();
        }
        
        // TODO: Check if user is enabled via User Service
        // For now, we'll skip this check since we don't have direct access to user status
        // if (!userServiceClient.getUserById(token.getUserId()).get().getEnabled()) {
        //     logger.warn("User account disabled for refresh token: {}", tokenId);
        //     revokeAllTokensForUser(token.getUserId());
        //     return Optional.empty();
        // }
        
        // Update last used timestamp
        token.markAsUsed();
        refreshTokenRepository.save(token);
        
        return Optional.of(token);
    }
    
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldToken, String deviceInfo, String ipAddress) {
        // Create new token
        RefreshToken newToken = createRefreshToken(
            oldToken.getUserId(), 
            oldToken.getClientId(), 
            deviceInfo, 
            ipAddress
        );
        
        // Mark old token as replaced
        oldToken.setReplacedBy(newToken.getId());
        oldToken.revoke();
        refreshTokenRepository.save(oldToken);
        
        logger.info("Refresh token rotated for user: {}", oldToken.getUserId());
        
        return newToken;
    }
    
    @Transactional
    public void revokeToken(RefreshToken token) {
        token.revoke();
        refreshTokenRepository.save(token);
        logger.info("Refresh token revoked: {}", token.getId());
    }
    
    @Transactional
    public void revokeToken(UUID tokenId) {
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findById(tokenId);
        if (tokenOpt.isPresent()) {
            revokeToken(tokenOpt.get());
        }
    }
    
    @Transactional
    public void revokeAllTokensForUser(Long userId) {
        refreshTokenRepository.revokeAllTokensByUserId(userId);
        logger.info("All refresh tokens revoked for user: {}", userId);
    }
    
    @Transactional
    public void revokeAllTokensForUserAndClient(Long userId, String clientId) {
        refreshTokenRepository.revokeTokensByUserAndClient(userId, clientId);
        logger.info("All refresh tokens revoked for user: {} and client: {}", userId, clientId);
    }
    
    public List<RefreshToken> getActiveTokensForUser(Long userId) {
        return refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
    }
    
    public List<RefreshToken> getActiveTokensForUserAndClient(Long userId, String clientId) {
        return refreshTokenRepository.findActiveTokensByUserAndClient(userId, clientId);
    }
    
    private void cleanupOldTokensForUser(Long userId, String clientId) {
        List<RefreshToken> activeTokens = getActiveTokensForUserAndClient(userId, clientId);
        
        if (activeTokens.size() >= maxTokensPerUser) {
            // Sort by creation date and revoke oldest tokens
            activeTokens.sort((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()));
            
            int tokensToRevoke = activeTokens.size() - maxTokensPerUser + 1;
            for (int i = 0; i < tokensToRevoke; i++) {
                revokeToken(activeTokens.get(i));
            }
            
            logger.info("Cleaned up {} old refresh tokens for user: {} and client: {}", 
                       tokensToRevoke, userId, clientId);
        }
    }
    
    @Scheduled(fixedRate = 3600000) // Run every hour
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7); // Keep expired tokens for 7 days for audit
            refreshTokenRepository.deleteExpiredTokens(cutoffDate);
            logger.info("Cleaned up expired refresh tokens before {}", cutoffDate);
        } catch (Exception e) {
            logger.error("Failed to cleanup expired refresh tokens: {}", e.getMessage());
        }
    }
    
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
    
    public int getMaxTokensPerUser() {
        return maxTokensPerUser;
    }
}