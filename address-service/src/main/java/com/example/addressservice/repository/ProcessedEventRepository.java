package com.example.addressservice.repository;

import com.example.addressservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    
    /**
     * Check if an event has already been processed
     */
    boolean existsByEventId(UUID eventId);
    
    /**
     * Find processed events by type
     */
    List<ProcessedEvent> findByEventType(String eventType);
    
    /**
     * Find processed events within a time range
     */
    List<ProcessedEvent> findByProcessedAtBetween(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Count processed events by type
     */
    long countByEventType(String eventType);
    
    /**
     * Delete old processed events (for cleanup)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ProcessedEvent pe WHERE pe.processedAt < :cutoffDate")
    int deleteAllByProcessedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
}