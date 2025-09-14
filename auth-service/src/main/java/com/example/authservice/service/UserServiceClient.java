package com.example.authservice.service;

import com.example.authservice.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Service
public class UserServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(UserServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String userServiceBaseUrl;
    private final String internalApiKey;
    
    public UserServiceClient(RestTemplate restTemplate,
                           @Value("${app.user-service.base-url}") String userServiceBaseUrl,
                           @Value("${app.user-service.internal-api-key}") String internalApiKey) {
        this.restTemplate = restTemplate;
        this.userServiceBaseUrl = userServiceBaseUrl;
        this.internalApiKey = internalApiKey;
    }
    
    @Cacheable(value = "users", key = "#userId")
    public Optional<UserDto> getUserById(Long userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-API-Key", internalApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String url = userServiceBaseUrl + "/internal/users/" + userId;
            ResponseEntity<UserDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, UserDto.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            
            logger.warn("User not found with ID: {}", userId);
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error fetching user with ID: {}", userId, e);
            return Optional.empty();
        }
    }
    
    @Cacheable(value = "users", key = "#email")
    public Optional<UserDto> getUserByEmail(String email) {
        logger.info("UserServiceClient.getUserByEmail called with email: {}", email);
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
               // Use the public endpoint /api/users/email/{email} instead of internal endpoint
               // Don't manually encode the email as Spring's RestTemplate will handle it
               String url = userServiceBaseUrl + "/api/users/email/" + email;
            
            logger.info("Fetching user by email: {}", email);
            logger.info("Full URL: {}", url);
            
            ResponseEntity<UserDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, UserDto.class
            );
            
            logger.info("Response status: {}", response.getStatusCode());
            logger.info("Response body: {}", response.getBody());
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("User found with email: {}", email);
                return Optional.of(response.getBody());
            }
            
            logger.warn("User not found with email: {}", email);
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error fetching user with email: {}", email, e);
            return Optional.empty();
        }
    }
    
    @Cacheable(value = "users", key = "#username")
    public Optional<UserDto> getUserByUsername(String username) {
        logger.info("UserServiceClient.getUserByUsername called with username: {}", username);
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
               // Use the public endpoint /api/users/username/{username} instead of internal endpoint
               // Don't manually encode the username as Spring's RestTemplate will handle it
               String url = userServiceBaseUrl + "/api/users/username/" + username;
            
            logger.info("Fetching user by username: {}", username);
            logger.info("Full URL: {}", url);
            
            ResponseEntity<UserDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, UserDto.class
            );
            
            logger.info("Response status: {}", response.getStatusCode());
            logger.info("Response body: {}", response.getBody());
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("User found with username: {}", username);
                return Optional.of(response.getBody());
            }
            
            logger.warn("User not found with username: {}", username);
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error fetching user with username: {}", username, e);
            return Optional.empty();
        }
    }
    
    public UserDto createUser(UserDto userDto) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<UserDto> entity = new HttpEntity<>(userDto, headers);
            
            // Use the public endpoint /api/users instead of internal endpoint
            String url = userServiceBaseUrl + "/api/users";
            
            logger.info("Creating user: {} with email: {}", userDto.getUsername(), userDto.getEmail());
            logger.info("Full URL: {}", url);
            
            ResponseEntity<UserDto> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, UserDto.class
            );
            
            logger.info("Response status: {}", response.getStatusCode());
            logger.info("Response body: {}", response.getBody());
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("User created successfully: {}", userDto.getUsername());
                return response.getBody();
            }
            
            throw new RuntimeException("Failed to create user");
            
        } catch (Exception e) {
            logger.error("Error creating user: {}", userDto.getEmail(), e);
            throw new RuntimeException("Failed to create user", e);
        }
    }
    
    public ResponseEntity<Map> getHealth() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-API-Key", internalApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String url = userServiceBaseUrl + "/health";
            return restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class
            );
            
        } catch (Exception e) {
            logger.error("Error checking User Service health", e);
            throw new RuntimeException("Failed to check User Service health", e);
        }
    }
}