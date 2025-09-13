package com.example.addressservice.controller;

import com.example.addressservice.entity.FailedEvent;
import com.example.addressservice.service.KafkaConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/failed-events")
public class FailedEventController {
    
    private final KafkaConsumerService kafkaConsumerService;
    
    @Autowired
    public FailedEventController(KafkaConsumerService kafkaConsumerService) {
        this.kafkaConsumerService = kafkaConsumerService;
    }
    
    /**
     * Get all failed events
     */
    @GetMapping
    public ResponseEntity<List<FailedEvent>> getFailedEvents() {
        List<FailedEvent> failedEvents = kafkaConsumerService.getFailedEvents(10);
        return ResponseEntity.ok(failedEvents);
    }
    
    /**
     * Retry a specific failed event
     */
    @PostMapping("/{eventId}/retry")
    public ResponseEntity<Map<String, Object>> retryFailedEvent(@PathVariable String eventId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = kafkaConsumerService.retryFailedEvent(eventId);
            
            if (success) {
                response.put("success", true);
                response.put("message", "Event retried successfully");
                response.put("eventId", eventId);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Failed to retry event");
                response.put("eventId", eventId);
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error retrying event: " + e.getMessage());
            response.put("eventId", eventId);
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Get processing statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getProcessingStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            kafkaConsumerService.logProcessingStats();
            stats.put("message", "Statistics logged to application logs");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            stats.put("error", "Failed to retrieve statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(stats);
        }
    }
}
