package com.example.addressservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "failed_events")
public class FailedEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "topic", nullable = false)
    private String topic;
    
    @Column(name = "partition", nullable = false)
    private Integer partition;
    
    @Column(name = "\"offset\"", nullable = false)
    private Long offset;
    
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    private String errorStackTrace;
    
    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;
    
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;
    
    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private FailedEventStatus status = FailedEventStatus.PENDING;
    
    // Constructors
    public FailedEvent() {}
    
    public FailedEvent(String eventId, String eventType, String topic, Integer partition, 
                      Long offset, String message, String errorMessage, String errorStackTrace, 
                      LocalDateTime failedAt, Integer retryCount) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.message = message;
        this.errorMessage = errorMessage;
        this.errorStackTrace = errorStackTrace;
        this.failedAt = failedAt;
        this.retryCount = retryCount;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public String getTopic() {
        return topic;
    }
    
    public void setTopic(String topic) {
        this.topic = topic;
    }
    
    public Integer getPartition() {
        return partition;
    }
    
    public void setPartition(Integer partition) {
        this.partition = partition;
    }
    
    public Long getOffset() {
        return offset;
    }
    
    public void setOffset(Long offset) {
        this.offset = offset;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getErrorStackTrace() {
        return errorStackTrace;
    }
    
    public void setErrorStackTrace(String errorStackTrace) {
        this.errorStackTrace = errorStackTrace;
    }
    
    public LocalDateTime getFailedAt() {
        return failedAt;
    }
    
    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }
    
    public Integer getRetryCount() {
        return retryCount;
    }
    
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
    
    public LocalDateTime getLastRetryAt() {
        return lastRetryAt;
    }
    
    public void setLastRetryAt(LocalDateTime lastRetryAt) {
        this.lastRetryAt = lastRetryAt;
    }
    
    public FailedEventStatus getStatus() {
        return status;
    }
    
    public void setStatus(FailedEventStatus status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return "FailedEvent{" +
                "id=" + id +
                ", eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", topic='" + topic + '\'' +
                ", partition=" + partition +
                ", offset=" + offset +
                ", failedAt=" + failedAt +
                ", retryCount=" + retryCount +
                ", status=" + status +
                '}';
    }
    
    public enum FailedEventStatus {
        PENDING,
        RETRYING,
        RESOLVED,
        PERMANENTLY_FAILED
    }
}
