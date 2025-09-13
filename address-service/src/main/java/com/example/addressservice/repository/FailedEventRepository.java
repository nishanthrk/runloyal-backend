package com.example.addressservice.repository;

import com.example.addressservice.entity.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {
    
    /**
     * Find failed event by event ID
     */
    Optional<FailedEvent> findByEventId(String eventId);
    
    /**
     * Find failed events by status
     */
    List<FailedEvent> findByStatus(FailedEvent.FailedEventStatus status);
    
    /**
     * Find failed events by event type
     */
    List<FailedEvent> findByEventType(String eventType);
    
    /**
     * Find failed events by topic
     */
    List<FailedEvent> findByTopic(String topic);
    
    /**
     * Find top N failed events ordered by failed date (most recent first)
     */
    List<FailedEvent> findTop10ByOrderByFailedAtDesc();
    
    /**
     * Find failed events older than specified date
     */
    List<FailedEvent> findByFailedAtBefore(LocalDateTime date);
    
    /**
     * Find failed events with retry count less than specified value
     */
    List<FailedEvent> findByRetryCountLessThan(Integer maxRetryCount);
    
    /**
     * Count failed events by status
     */
    long countByStatus(FailedEvent.FailedEventStatus status);
    
    /**
     * Count failed events by event type
     */
    long countByEventType(String eventType);
    
    /**
     * Count failed events by topic
     */
    long countByTopic(String topic);
    
    /**
     * Find failed events for retry (pending status and retry count < max)
     */
    @Query("SELECT fe FROM FailedEvent fe WHERE fe.status = 'PENDING' AND fe.retryCount < :maxRetryCount ORDER BY fe.failedAt ASC")
    List<FailedEvent> findPendingEventsForRetry(@Param("maxRetryCount") Integer maxRetryCount);
    
    /**
     * Find failed events by date range
     */
    @Query("SELECT fe FROM FailedEvent fe WHERE fe.failedAt BETWEEN :startDate AND :endDate ORDER BY fe.failedAt DESC")
    List<FailedEvent> findByFailedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find failed events with specific error message pattern
     */
    @Query("SELECT fe FROM FailedEvent fe WHERE fe.errorMessage LIKE %:errorPattern% ORDER BY fe.failedAt DESC")
    List<FailedEvent> findByErrorMessageContaining(@Param("errorPattern") String errorPattern);
    
    /**
     * Delete failed events older than specified date
     */
    void deleteByFailedAtBefore(LocalDateTime date);
    
    /**
     * Delete failed events by status
     */
    void deleteByStatus(FailedEvent.FailedEventStatus status);
}
