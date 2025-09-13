package com.example.authservice.repository;

import com.example.authservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    
    Optional<RefreshToken> findByIdAndRevokedFalse(UUID id);
    
    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);
    
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.userId = :userId AND rt.clientId = :clientId AND rt.revoked = false")
    List<RefreshToken> findActiveTokensByUserAndClient(@Param("userId") Long userId, @Param("clientId") String clientId);
    
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId")
    void revokeAllTokensByUserId(@Param("userId") Long userId);
    
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId AND rt.clientId = :clientId")
    void revokeTokensByUserAndClient(@Param("userId") Long userId, @Param("clientId") String clientId);
    
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.lastUsedAt = :lastUsedAt WHERE rt.id = :tokenId")
    void updateLastUsedAt(@Param("tokenId") UUID tokenId, @Param("lastUsedAt") LocalDateTime lastUsedAt);
    
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.expiresAt < :now OR rt.revoked = true")
    List<RefreshToken> findExpiredOrRevokedTokens(@Param("now") LocalDateTime now);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoffDate")
    void deleteExpiredTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
}