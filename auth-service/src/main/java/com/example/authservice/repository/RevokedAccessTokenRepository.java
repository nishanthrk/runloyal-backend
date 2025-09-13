package com.example.authservice.repository;

import com.example.authservice.entity.RevokedAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, UUID> {
    
    boolean existsByJti(UUID jti);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM RevokedAccessToken rat WHERE rat.expiresAt < :cutoffDate")
    void deleteExpiredTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Query("SELECT COUNT(rat) FROM RevokedAccessToken rat WHERE rat.userId = :userId")
    long countByUserId(@Param("userId") Long userId);
}