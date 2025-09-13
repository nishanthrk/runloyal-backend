package com.example.authservice.service;

import com.example.authservice.entity.RevokedAccessToken;
import com.example.authservice.dto.UserDto;
import com.example.authservice.repository.RevokedAccessTokenRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    
    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final String issuer;
    private final RevokedAccessTokenRepository revokedTokenRepository;
    
    public JwtService(@Value("${app.jwt.secret}") String secret,
                     @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration,
                     @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration,
                     @Value("${app.jwt.issuer}") String issuer,
                     RevokedAccessTokenRepository revokedTokenRepository) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.issuer = issuer;
        this.revokedTokenRepository = revokedTokenRepository;
    }
    
    public String generateAccessToken(UserDto user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("emailVerified", user.getEmailVerified());
        claims.put("enabled", user.getEnabled());
        
        return createToken(claims, user.getUsername(), accessTokenExpiration);
    }
    
    public String generateAccessToken(UserDto user, Map<String, Object> additionalClaims) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("emailVerified", user.getEmailVerified());
        claims.put("enabled", user.getEnabled());
        
        if (additionalClaims != null) {
            claims.putAll(additionalClaims);
        }
        
        return createToken(claims, user.getUsername(), accessTokenExpiration);
    }
    
    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .id(UUID.randomUUID().toString())
                .signWith(secretKey)
                .compact();
    }
    
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }
    
    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }
    
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            logger.error("Failed to parse JWT token: {}", e.getMessage());
            throw e;
        }
    }
    
    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }
    
    public boolean isTokenRevoked(String token) {
        try {
            String jti = extractJti(token);
            return jti != null && revokedTokenRepository.existsByJti(UUID.fromString(jti));
        } catch (Exception e) {
            logger.error("Error checking token revocation status: {}", e.getMessage());
            return true;
        }
    }
    
    public boolean isTokenValid(String token, UserDto user) {
        try {
            final String username = extractUsername(token);
            return username.equals(user.getUsername()) 
                    && !isTokenExpired(token) 
                    && !isTokenRevoked(token)
                    && user.getEnabled();
        } catch (JwtException e) {
            logger.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    public void revokeToken(String token) {
        try {
            String jti = extractJti(token);
            Long userId = extractUserId(token);
            Date expiration = extractExpiration(token);
            
            if (jti != null && userId != null && expiration != null) {
                LocalDateTime expiresAt = expiration.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
                
                RevokedAccessToken revokedToken = new RevokedAccessToken(
                        UUID.fromString(jti), 
                        userId, 
                        expiresAt
                );
                
                revokedTokenRepository.save(revokedToken);
                logger.info("Token with JTI {} has been revoked", jti);
            }
        } catch (Exception e) {
            logger.error("Failed to revoke token: {}", e.getMessage());
        }
    }
    
    public void cleanupExpiredRevokedTokens() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(1);
            revokedTokenRepository.deleteExpiredTokens(cutoffDate);
            logger.info("Cleaned up expired revoked tokens before {}", cutoffDate);
        } catch (Exception e) {
            logger.error("Failed to cleanup expired revoked tokens: {}", e.getMessage());
        }
    }
    
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
}