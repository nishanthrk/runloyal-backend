package com.example.addressservice.service;

import com.example.addressservice.entity.ProcessedEvent;
import com.example.addressservice.event.ProfileUpdatedEvent;
import com.example.addressservice.event.UserEvent;
import com.example.addressservice.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class KafkaConsumerService {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    
    private final ProcessedEventRepository processedEventRepository;
    private final AddressService addressService;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public KafkaConsumerService(ProcessedEventRepository processedEventRepository,
                               AddressService addressService,
                               ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.addressService = addressService;
        this.objectMapper = objectMapper;
    }
    
    @KafkaListener(topics = "user-events", groupId = "address-service-group")
    @Transactional
    public void handleUserEvent(String message) {
        try {
            logger.info("Received user event: {}", message);
            
            UserEvent userEvent = objectMapper.readValue(message, UserEvent.class);
            
            // Check if event has already been processed (idempotency)
            String eventId = userEvent.getId().toString();
            if (processedEventRepository.existsByEventId(eventId)) {
                logger.info("Event already processed, skipping: {}", eventId);
                return;
            }
            
            // Process the event based on type
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
            
            // Mark event as processed
            ProcessedEvent processedEvent = new ProcessedEvent();
            processedEvent.setEventId(eventId);
            processedEvent.setEventType(userEvent.getType());
            processedEvent.setProcessedAt(LocalDateTime.now());
            processedEventRepository.save(processedEvent);
            
            logger.info("Successfully processed event: {} of type: {}", eventId, userEvent.getType());
            
        } catch (Exception e) {
            logger.error("Error processing user event: {}", message, e);
            // In a production environment, you might want to send this to a dead letter queue
            // or implement retry logic
        }
    }
    
    @KafkaListener(topics = "profile-updated", groupId = "address-service-group")
    @Transactional
    public void handleProfileUpdatedEvent(ProfileUpdatedEvent profileEvent) {
        try {
            logger.info("Received profile updated event: {}", profileEvent);
            
            // Generate unique event ID for idempotency (using userId + timestamp)
            String eventId = "profile_updated_" + profileEvent.getUserId() + "_" + profileEvent.getTimestamp().toString();
            
            // Check if event has already been processed (idempotency)
            if (processedEventRepository.existsByEventId(eventId)) {
                logger.info("Event {} already processed, skipping", eventId);
                return;
            }
            
            // Process the profile updated event
            handleProfileUpdated(profileEvent);
            
            // Mark event as processed
            ProcessedEvent processedEvent = new ProcessedEvent();
            processedEvent.setEventId(eventId);
            processedEvent.setEventType("PROFILE_UPDATED");
            processedEvent.setProcessedAt(LocalDateTime.now());
            processedEventRepository.save(processedEvent);
            
            logger.info("Successfully processed profile updated event: {}", eventId);
            
        } catch (Exception e) {
            logger.error("Error processing profile updated event: {}", profileEvent, e);
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
            
            logger.info("Event processing stats - Total: {}, Created: {}, Updated: {}, Deleted: {}",
                       totalProcessed, userCreatedCount, userUpdatedCount, userDeletedCount);
                       
        } catch (Exception e) {
            logger.error("Error retrieving processing stats", e);
        }
    }
}