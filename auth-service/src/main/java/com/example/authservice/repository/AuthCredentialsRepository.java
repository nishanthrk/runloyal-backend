package com.example.authservice.repository;

import com.example.authservice.entity.AuthCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthCredentialsRepository extends JpaRepository<AuthCredentials, Long> {
    
    Optional<AuthCredentials> findByUserId(Long userId);
    
    boolean existsByUserId(Long userId);
    
    @Query("SELECT ac FROM AuthCredentials ac WHERE ac.userId = :userId AND ac.enabled = true")
    Optional<AuthCredentials> findActiveByUserId(@Param("userId") Long userId);
    
    void deleteByUserId(Long userId);
}