package com.example.userservice.service;

import com.example.userservice.entity.OutboxEvent;
import com.example.userservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class OutboxService {
    
    private static final Logger logger = LoggerFactory.getLogger(OutboxService.class);
    
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public OutboxService(OutboxEventRepository outboxEventRepository, 
                        KafkaTemplate<String, Object> kafkaTemplate,
                        ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    public void createOutboxEvent(String aggregateId, String aggregateType, String eventType, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxEvent outboxEvent = new OutboxEvent(aggregateId, aggregateType, eventType, payloadJson);
            outboxEventRepository.save(outboxEvent);
            logger.info("Created outbox event: {} for aggregate: {}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize payload for outbox event", e);
            throw new RuntimeException("Failed to create outbox event", e);
        }
    }
    
    @Scheduled(fixedDelay = 5000) // Run every 5 seconds
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEventsWithLimit(100);
        
        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
                markEventAsProcessed(event);
                logger.info("Published outbox event: {} for aggregate: {}", event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                markEventAsFailed(event);
                logger.error("Failed to publish outbox event: {} for aggregate: {}", 
                           event.getEventType(), event.getAggregateId(), e);
            }
        }
    }
    
    private void publishEvent(OutboxEvent event) {
        try {
            Object payload = objectMapper.readValue(event.getPayload(), Object.class);
            String topicName = getTopicName(event.getEventType());
            kafkaTemplate.send(topicName, event.getAggregateId(), payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize event payload", e);
        }
    }
    
    private String getTopicName(String eventType) {
        // Map event types to Kafka topics
        switch (eventType) {
            case "ProfileUpdated":
                return "profile-updated";
            default:
                return "user-events";
        }
    }
    
    private void markEventAsProcessed(OutboxEvent event) {
        event.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
        event.setProcessedAt(LocalDateTime.now());
        outboxEventRepository.save(event);
    }
    
    private void markEventAsFailed(OutboxEvent event) {
        event.setStatus(OutboxEvent.OutboxStatus.FAILED);
        outboxEventRepository.save(event);
    }
}