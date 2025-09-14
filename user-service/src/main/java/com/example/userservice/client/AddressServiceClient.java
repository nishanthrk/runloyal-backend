package com.example.userservice.client;

import com.example.userservice.dto.AddressDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AddressServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AddressServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String addressServiceUrl;
    
    public AddressServiceClient(RestTemplate restTemplate, 
                               @Value("${address.service.url:http://localhost:8083}") String addressServiceUrl) {
        this.restTemplate = restTemplate;
        this.addressServiceUrl = addressServiceUrl;
    }
    
    /**
     * Get primary address for a user
     */
    public Optional<AddressDto> getUserPrimaryAddress(Long userId) {
        try {
            logger.debug("Fetching primary address for user: {}", userId);
            String url = addressServiceUrl + "/internal/addresses/users/" + userId + "/primary";
            AddressDto address = restTemplate.getForObject(url, AddressDto.class);
            return Optional.ofNullable(address);
        } catch (RestClientException e) {
            logger.warn("Failed to fetch primary address for user: {} - {}", userId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error fetching primary address for user: {}", userId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Get all addresses for a user
     */
    public List<AddressDto> getUserAddresses(Long userId) {
        try {
            logger.debug("Fetching all addresses for user: {}", userId);
            String url = addressServiceUrl + "/internal/addresses/users/" + userId;
            AddressDto[] addresses = restTemplate.getForObject(url, AddressDto[].class);
            return addresses != null ? Arrays.asList(addresses) : List.of();
        } catch (RestClientException e) {
            logger.warn("Failed to fetch addresses for user: {} - {}", userId, e.getMessage());
            return List.of();
        } catch (Exception e) {
            logger.error("Error fetching addresses for user: {}", userId, e);
            return List.of();
        }
    }
    
    /**
     * Check if user has any addresses
     */
    public boolean userHasAddresses(Long userId) {
        try {
            logger.debug("Checking if user has addresses: {}", userId);
            String url = addressServiceUrl + "/internal/addresses/users/" + userId + "/exists";
            Boolean hasAddresses = restTemplate.getForObject(url, Boolean.class);
            return hasAddresses != null && hasAddresses;
        } catch (RestClientException e) {
            logger.warn("Failed to check addresses for user: {} - {}", userId, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error checking addresses for user: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Get Address Service health status
     */
    public ResponseEntity<Map> getHealth() {
        try {
            String url = addressServiceUrl + "/health";
            return restTemplate.getForEntity(url, Map.class);
        } catch (Exception e) {
            logger.error("Error checking Address Service health", e);
            throw new RuntimeException("Failed to check Address Service health", e);
        }
    }
}