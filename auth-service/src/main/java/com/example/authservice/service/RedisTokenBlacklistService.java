package com.example.authservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RedisTokenBlacklistService {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisTokenBlacklistService.class);
    
    private final RedisTemplate<String, String> redisTemplate;
    private final String keyPrefix;
    private final long defaultTtl;
    
    public RedisTokenBlacklistService(RedisTemplate<String, String> redisTemplate,
                                    @Value("${app.redis.tokenBlacklist.keyPrefix}") String keyPrefix,
                                    @Value("${app.redis.tokenBlacklist.defaultTtl}") long defaultTtl) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.defaultTtl = defaultTtl;
    }
    
    /**
     * Blacklist a token with default TTL
     */
    public void blacklistToken(String tokenId) {
        blacklistToken(tokenId, defaultTtl);
    }
    
    /**
     * Blacklist a token with custom TTL
     */
    public void blacklistToken(String tokenId, long ttlMillis) {
        try {
            String key = keyPrefix + tokenId;
            redisTemplate.opsForValue().set(key, "blacklisted", Duration.ofMillis(ttlMillis));
            logger.debug("Token blacklisted: {} with TTL: {} ms", tokenId, ttlMillis);
        } catch (Exception e) {
            logger.error("Failed to blacklist token: {}", tokenId, e);
            throw new RuntimeException("Failed to blacklist token", e);
        }
    }
    
    /**
     * Check if a token is blacklisted
     */
    public boolean isTokenBlacklisted(String tokenId) {
        try {
            String key = keyPrefix + tokenId;
            Boolean exists = redisTemplate.hasKey(key);
            boolean blacklisted = exists != null && exists;
            logger.debug("Token blacklist check: {} - {}", tokenId, blacklisted ? "BLACKLISTED" : "VALID");
            return blacklisted;
        } catch (Exception e) {
            logger.error("Failed to check token blacklist status: {}", tokenId, e);
            // In case of Redis failure, assume token is not blacklisted to avoid blocking valid requests
            return false;
        }
    }
    
    /**
     * Remove a token from blacklist (if needed for testing or admin purposes)
     */
    public void removeFromBlacklist(String tokenId) {
        try {
            String key = keyPrefix + tokenId;
            redisTemplate.delete(key);
            logger.debug("Token removed from blacklist: {}", tokenId);
        } catch (Exception e) {
            logger.error("Failed to remove token from blacklist: {}", tokenId, e);
        }
    }
    
    /**
     * Blacklist all tokens for a specific user (useful for logout all devices)
     */
    public void blacklistAllUserTokens(Long userId, long ttlMillis) {
        try {
            String userKey = keyPrefix + "user:" + userId;
            redisTemplate.opsForValue().set(userKey, "all_tokens_blacklisted", Duration.ofMillis(ttlMillis));
            logger.debug("All tokens blacklisted for user: {} with TTL: {} ms", userId, ttlMillis);
        } catch (Exception e) {
            logger.error("Failed to blacklist all tokens for user: {}", userId, e);
            throw new RuntimeException("Failed to blacklist user tokens", e);
        }
    }
    
    /**
     * Check if all tokens for a user are blacklisted
     */
    public boolean areAllUserTokensBlacklisted(Long userId) {
        try {
            String userKey = keyPrefix + "user:" + userId;
            Boolean exists = redisTemplate.hasKey(userKey);
            boolean blacklisted = exists != null && exists;
            logger.debug("User tokens blacklist check: {} - {}", userId, blacklisted ? "ALL_BLACKLISTED" : "VALID");
            return blacklisted;
        } catch (Exception e) {
            logger.error("Failed to check user tokens blacklist status: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Get remaining TTL for a blacklisted token
     */
    public long getTokenBlacklistTtl(String tokenId) {
        try {
            String key = keyPrefix + tokenId;
            Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            return ttl != null ? ttl : -1;
        } catch (Exception e) {
            logger.error("Failed to get TTL for blacklisted token: {}", tokenId, e);
            return -1;
        }
    }
}