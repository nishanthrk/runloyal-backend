package com.example.authservice.repository;

import com.example.authservice.entity.OAuthProviderToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OAuthProviderTokenRepository extends JpaRepository<OAuthProviderToken, Long> {
    
    Optional<OAuthProviderToken> findByProviderAndProviderUserId(String provider, String providerUserId);
    
    List<OAuthProviderToken> findByUserId(Long userId);
    
    Optional<OAuthProviderToken> findByUserIdAndProvider(Long userId, String provider);
    
    @Query("SELECT opt FROM OAuthProviderToken opt WHERE opt.tokenExpiresAt < :now")
    List<OAuthProviderToken> findExpiredTokens(@Param("now") LocalDateTime now);
    
    void deleteByUserId(Long userId);
    
    void deleteByUserIdAndProvider(Long userId, String provider);
}