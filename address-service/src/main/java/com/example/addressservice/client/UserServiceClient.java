package com.example.addressservice.client;

import com.example.addressservice.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Component
public class UserServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(UserServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String userServiceBaseUrl;
    
    public UserServiceClient(RestTemplate restTemplate, 
                           @Value("${app.user-service.base-url:http://localhost:8082}") String userServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.userServiceBaseUrl = userServiceBaseUrl;
    }
    
    /**
     * Get user by ID from User Service
     */
    public Optional<UserDto> getUserById(Long userId) {
        try {
            String url = userServiceBaseUrl + "/api/users/" + userId;
            logger.debug("Fetching user by ID: {}", userId);
            
            ResponseEntity<UserDto> response = restTemplate.getForEntity(url, UserDto.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                logger.debug("Successfully fetched user: {}", response.getBody().getUsername());
                return Optional.of(response.getBody());
            } else {
                logger.warn("User not found with ID: {}", userId);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            logger.error("Error fetching user by ID: {}", userId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Check if user exists
     */
    public boolean userExists(Long userId) {
        return getUserById(userId).isPresent();
    }
    
    /**
     * Get User Service health status
     */
    public ResponseEntity<Map> getHealth() {
        try {
            String url = userServiceBaseUrl + "/actuator/health";
            logger.debug("Checking User Service health at: {}", url);
            return restTemplate.getForEntity(url, Map.class);
        } catch (Exception e) {
            logger.error("Error checking User Service health", e);
            throw new RuntimeException("Failed to check User Service health", e);
        }
    }
}
