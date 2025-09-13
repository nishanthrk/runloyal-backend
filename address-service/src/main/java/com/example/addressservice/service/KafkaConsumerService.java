package com.example.addressservice.service;

import com.example.addressservice.entity.ProcessedEvent;
import com.example.addressservice.entity.FailedEvent;
import com.example.addressservice.event.ProfileUpdatedEvent;
import com.example.addressservice.event.UserEvent;
import com.example.addressservice.repository.ProcessedEventRepository;
import com.example.addressservice.repository.FailedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class KafkaConsumerService {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    
    private final ProcessedEventRepository processedEventRepository;
    private final FailedEventRepository failedEventRepository;
    private final AddressService addressService;
    private final ObjectMapper objectMapper;
    
    // Retry configuration constants
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final double RETRY_MULTIPLIER = 2.0;
    
    @Autowired
    public KafkaConsumerService(ProcessedEventRepository processedEventRepository,
                               FailedEventRepository failedEventRepository,
                               AddressService addressService,
                               ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.failedEventRepository = failedEventRepository;
        this.addressService = addressService;
        this.objectMapper = objectMapper;
    }
    
    @KafkaListener(topics = "user-events", groupId = "address-service-group")
    @Transactional
    @Retryable(
        value = {Exception.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(
            delay = INITIAL_RETRY_DELAY_MS,
            multiplier = RETRY_MULTIPLIER,
            maxDelay = 10000
        )
    )
    public void handleUserEvent(String message, 
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset) {
        try {
            logger.info("Received user event: {} from topic: {}, partition: {}, offset: {}", 
                       message, topic, partition, offset);
            
            UserEvent userEvent;
            try {
                userEvent = objectMapper.readValue(message, UserEvent.class);
            } catch (JsonProcessingException e) {
                logger.error("Failed to deserialize user event message: {}", message, e);
                sendToDeadLetterQueue(message, topic, partition, offset, e, "USER_EVENT");
                return;
            }
            
            // Check if event has already been processed (idempotency)
            String eventId = userEvent.getId().toString();
            if (processedEventRepository.existsByEventId(eventId)) {
                logger.info("Event already processed, skipping: {}", eventId);
                return;
            }
            
            // Process the event based on type
            processUserEventWithRetry(userEvent);
            
            // Mark event as processed
            ProcessedEvent processedEvent = new ProcessedEvent();
            processedEvent.setEventId(eventId);
            processedEvent.setEventType(userEvent.getType());
            processedEvent.setProcessedAt(LocalDateTime.now());
            processedEventRepository.save(processedEvent);
            
            logger.info("Successfully processed event: {} of type: {}", eventId, userEvent.getType());
            
        } catch (Exception e) {
            logger.error("Error processing user event: {} from topic: {}, partition: {}, offset: {}", 
                        message, topic, partition, offset, e);
            
            // Check if this is a retryable error
            if (isRetryableError(e)) {
                logger.warn("Retryable error encountered, will retry: {}", e.getMessage());
                throw e; // Re-throw to trigger retry mechanism
            } else {
                logger.error("Non-retryable error encountered, sending to DLQ: {}", e.getMessage());
                sendToDeadLetterQueue(message, topic, partition, offset, e, "USER_EVENT");
            }
        }
    }
    
    @KafkaListener(topics = "profile-updated", groupId = "address-service-group")
    @Transactional
    @Retryable(
        value = {Exception.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(
            delay = INITIAL_RETRY_DELAY_MS,
            multiplier = RETRY_MULTIPLIER,
            maxDelay = 10000
        )
    )
    public void handleProfileUpdatedEvent(ProfileUpdatedEvent profileEvent,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                        @Header(KafkaHeaders.OFFSET) long offset) {
        try {
            logger.info("Received profile updated event: {} from topic: {}, partition: {}, offset: {}", 
                       profileEvent, topic, partition, offset);
            
            // Generate unique event ID for idempotency (using userId + timestamp)
            String eventId = "profile_updated_" + profileEvent.getUserId() + "_" + profileEvent.getTimestamp().toString();
            
            // Check if event has already been processed (idempotency)
            if (processedEventRepository.existsByEventId(eventId)) {
                logger.info("Event {} already processed, skipping", eventId);
                return;
            }
            
            // Process the profile updated event with retry logic
            processProfileUpdatedWithRetry(profileEvent);
            
            // Mark event as processed
            ProcessedEvent processedEvent = new ProcessedEvent();
            processedEvent.setEventId(eventId);
            processedEvent.setEventType("PROFILE_UPDATED");
            processedEvent.setProcessedAt(LocalDateTime.now());
            processedEventRepository.save(processedEvent);
            
            logger.info("Successfully processed profile updated event: {}", eventId);
            
        } catch (Exception e) {
            logger.error("Error processing profile updated event: {} from topic: {}, partition: {}, offset: {}", 
                        profileEvent, topic, partition, offset, e);
            
            // Check if this is a retryable error
            if (isRetryableError(e)) {
                logger.warn("Retryable error encountered, will retry: {}", e.getMessage());
                throw e; // Re-throw to trigger retry mechanism
            } else {
                logger.error("Non-retryable error encountered, sending to DLQ: {}", e.getMessage());
                sendToDeadLetterQueue(profileEvent.toString(), topic, partition, offset, e, "PROFILE_UPDATED");
            }
        }
    }
    
    private void handleUserCreated(UserEvent userEvent) {
        logger.info("Handling USER_CREATED event for user: {}", userEvent.getAggregateId());
        
        // For user creation, we don't need to do anything specific
        // The address service will handle address creation when the user creates addresses
        // This is mainly for logging and potential future use cases
        
        if (userEvent.getPayload() != null) {
            logger.info("User created: {} with email: {}", 
                       userEvent.getPayload().getName(), 
                       userEvent.getPayload().getEmail());
        }
    }
    
    private void handleUserUpdated(UserEvent userEvent) {
        logger.info("Handling USER_UPDATED event for user: {}", userEvent.getAggregateId());
        
        // For user updates, we might want to log the changes
        // In the future, we could update address-related information if needed
        
        if (userEvent.getPayload() != null) {
            logger.info("User updated: {} with email: {}", 
                       userEvent.getPayload().getName(), 
                       userEvent.getPayload().getEmail());
        }
    }
    
    private void handleUserDeleted(UserEvent userEvent) {
        logger.info("Handling USER_DELETED event for user: {}", userEvent.getAggregateId());
        
        try {
            Long userId = Long.parseLong(userEvent.getAggregateId());
            
            // Delete all addresses for the deleted user
            long addressCount = addressService.getAddressCount(userId);
            if (addressCount > 0) {
                addressService.deleteAllUserAddresses(userId);
                logger.info("Deleted {} addresses for deleted user: {}", addressCount, userId);
            } else {
                logger.info("No addresses found for deleted user: {}", userId);
            }
            
        } catch (NumberFormatException e) {
            logger.error("Invalid user ID format in USER_DELETED event: {}", userEvent.getAggregateId(), e);
        } catch (Exception e) {
            logger.error("Error deleting addresses for user: {}", userEvent.getAggregateId(), e);
            throw e; // Re-throw to ensure transaction rollback
        }
    }
    
    private void handleProfileUpdated(ProfileUpdatedEvent profileEvent) {
        try {
            logger.info("Processing profile updated event for user: {}", profileEvent.getUserId());
            
            // Use the address service to upsert the address information
            addressService.upsertAddressFromProfile(profileEvent);
            
            logger.info("Successfully processed profile updated event for user: {}", profileEvent.getUserId());
            
        } catch (Exception e) {
            logger.error("Error handling profile updated event for user: {}", profileEvent.getUserId(), e);
            throw e; // Re-throw to trigger Kafka retry mechanism
        }
    }
    
    /**
     * Clean up old processed events (can be called by a scheduled job)
     */
    @Transactional
    public void cleanupOldProcessedEvents(int daysToKeep) {
        logger.info("Cleaning up processed events older than {} days", daysToKeep);
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        long deletedCount = processedEventRepository.deleteAllByProcessedAtBefore(cutoffDate);
        
        logger.info("Cleaned up {} old processed events", deletedCount);
    }
    
    /**
     * Get processing statistics
     */
    public void logProcessingStats() {
        try {
            long totalProcessed = processedEventRepository.count();
            long userCreatedCount = processedEventRepository.countByEventType("USER_CREATED");
            long userUpdatedCount = processedEventRepository.countByEventType("USER_UPDATED");
            long userDeletedCount = processedEventRepository.countByEventType("USER_DELETED");
            long failedCount = failedEventRepository.count();
            
            logger.info("Event processing stats - Total: {}, Created: {}, Updated: {}, Deleted: {}, Failed: {}",
                       totalProcessed, userCreatedCount, userUpdatedCount, userDeletedCount, failedCount);
                       
        } catch (Exception e) {
            logger.error("Error retrieving processing stats", e);
        }
    }
    
    /**
     * Process user event with retry logic
     */
    @Retryable(
        value = {Exception.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(
            delay = INITIAL_RETRY_DELAY_MS,
            multiplier = RETRY_MULTIPLIER,
            maxDelay = 10000
        )
    )
    private void processUserEventWithRetry(UserEvent userEvent) {
        switch (userEvent.getType()) {
            case "USER_CREATED":
                handleUserCreated(userEvent);
                break;
            case "USER_UPDATED":
                handleUserUpdated(userEvent);
                break;
            case "USER_DELETED":
                handleUserDeleted(userEvent);
                break;
            default:
                logger.warn("Unknown event type: {}", userEvent.getType());
                break;
        }
    }
    
    /**
     * Process profile updated event with retry logic
     */
    @Retryable(
        value = {Exception.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(
            delay = INITIAL_RETRY_DELAY_MS,
            multiplier = RETRY_MULTIPLIER,
            maxDelay = 10000
        )
    )
    private void processProfileUpdatedWithRetry(ProfileUpdatedEvent profileEvent) {
        handleProfileUpdated(profileEvent);
    }
    
    /**
     * Check if an error is retryable
     */
    private boolean isRetryableError(Exception e) {
        // Database connection issues, network timeouts, temporary service unavailability
        if (e instanceof org.springframework.dao.DataAccessException ||
            e instanceof java.net.ConnectException ||
            e instanceof java.net.SocketTimeoutException ||
            e instanceof org.springframework.web.client.ResourceAccessException) {
            return true;
        }
        
        // Check for specific error messages that indicate temporary issues
        String errorMessage = e.getMessage().toLowerCase();
        return errorMessage.contains("connection") ||
               errorMessage.contains("timeout") ||
               errorMessage.contains("temporary") ||
               errorMessage.contains("unavailable") ||
               errorMessage.contains("retry");
    }
    
    /**
     * Send failed event to dead letter queue
     */
    @Transactional
    private void sendToDeadLetterQueue(String message, String topic, int partition, 
                                     long offset, Exception error, String eventType) {
        try {
            FailedEvent failedEvent = new FailedEvent();
            failedEvent.setEventId(generateEventId(topic, partition, offset));
            failedEvent.setEventType(eventType);
            failedEvent.setTopic(topic);
            failedEvent.setPartition(partition);
            failedEvent.setOffset(offset);
            failedEvent.setMessage(message);
            failedEvent.setErrorMessage(error.getMessage());
            failedEvent.setErrorStackTrace(getStackTrace(error));
            failedEvent.setFailedAt(LocalDateTime.now());
            failedEvent.setRetryCount(MAX_RETRY_ATTEMPTS);
            
            failedEventRepository.save(failedEvent);
            
            logger.error("Event sent to dead letter queue - EventId: {}, Topic: {}, Partition: {}, Offset: {}", 
                        failedEvent.getEventId(), topic, partition, offset);
            
        } catch (Exception e) {
            logger.error("Failed to save event to dead letter queue", e);
        }
    }
    
    /**
     * Generate unique event ID for failed events
     */
    private String generateEventId(String topic, int partition, long offset) {
        return String.format("%s_%d_%d_%d", topic, partition, offset, System.currentTimeMillis());
    }
    
    /**
     * Get stack trace as string
     */
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Recovery method for user events after all retries exhausted
     */
    @Recover
    public void recoverUserEvent(Exception e, String message, String topic, int partition, long offset) {
        logger.error("All retry attempts exhausted for user event from topic: {}, partition: {}, offset: {}", 
                    topic, partition, offset, e);
        sendToDeadLetterQueue(message, topic, partition, offset, e, "USER_EVENT");
    }
    
    /**
     * Recovery method for profile updated events after all retries exhausted
     */
    @Recover
    public void recoverProfileUpdatedEvent(Exception e, ProfileUpdatedEvent profileEvent, 
                                         String topic, int partition, long offset) {
        logger.error("All retry attempts exhausted for profile updated event from topic: {}, partition: {}, offset: {}", 
                    topic, partition, offset, e);
        sendToDeadLetterQueue(profileEvent.toString(), topic, partition, offset, e, "PROFILE_UPDATED");
    }
    
    /**
     * Get failed events for manual reprocessing
     */
    public java.util.List<FailedEvent> getFailedEvents(int limit) {
        return failedEventRepository.findTop10ByOrderByFailedAtDesc();
    }
    
    /**
     * Retry a failed event manually
     */
    @Transactional
    public boolean retryFailedEvent(String eventId) {
        try {
            Optional<FailedEvent> failedEventOpt = failedEventRepository.findByEventId(eventId);
            if (failedEventOpt.isEmpty()) {
                logger.warn("Failed event not found: {}", eventId);
                return false;
            }
            
            FailedEvent failedEvent = failedEventOpt.get();
            
            logger.info("Manually retrying failed event: {}", eventId);
            
            // Process the event based on type
            if ("USER_EVENT".equals(failedEvent.getEventType())) {
                try {
                    UserEvent userEvent = objectMapper.readValue(failedEvent.getMessage(), UserEvent.class);
                    processUserEventWithRetry(userEvent);
                } catch (JsonProcessingException e) {
                    logger.error("Failed to deserialize USER_EVENT message: {}", failedEvent.getMessage(), e);
                    return false;
                }
            } else if ("PROFILE_UPDATED".equals(failedEvent.getEventType())) {
                try {
                    ProfileUpdatedEvent profileEvent = objectMapper.readValue(failedEvent.getMessage(), ProfileUpdatedEvent.class);
                    processProfileUpdatedWithRetry(profileEvent);
                } catch (JsonProcessingException e) {
                    logger.error("Failed to deserialize PROFILE_UPDATED message: {}", failedEvent.getMessage(), e);
                    return false;
                }
            }
            
            // Remove from failed events table
            failedEventRepository.delete(failedEvent);
            
            logger.info("Successfully retried failed event: {}", eventId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error retrying failed event: {}", eventId, e);
            return false;
        }
    }
}