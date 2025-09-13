package com.example.addressservice.controller;

import com.example.addressservice.dto.AddressResponse;
import com.example.addressservice.service.AddressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/internal/addresses")
@CrossOrigin(origins = "*")
public class InternalAddressController {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalAddressController.class);
    
    private final AddressService addressService;
    
    public InternalAddressController(AddressService addressService) {
        this.addressService = addressService;
    }
    
    /**
     * Get primary address for a user (for inter-service communication)
     */
    @GetMapping("/users/{userId}/primary")
    public ResponseEntity<AddressResponse> getUserPrimaryAddress(@PathVariable Long userId) {
        try {
            logger.debug("Fetching primary address for user: {} (internal call)", userId);
            Optional<AddressResponse> address = addressService.getPrimaryAddress(userId);
            return address.map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error fetching primary address for user: {} (internal call)", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get all addresses for a user (for inter-service communication)
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<AddressResponse>> getUserAddresses(@PathVariable Long userId) {
        try {
            logger.debug("Fetching all addresses for user: {} (internal call)", userId);
            List<AddressResponse> addresses = addressService.getUserAddresses(userId);
            return ResponseEntity.ok(addresses);
        } catch (Exception e) {
            logger.error("Error fetching addresses for user: {} (internal call)", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Check if user has any addresses (for inter-service communication)
     */
    @GetMapping("/users/{userId}/exists")
    public ResponseEntity<Boolean> userHasAddresses(@PathVariable Long userId) {
        try {
            logger.debug("Checking if user has addresses: {} (internal call)", userId);
            long count = addressService.getAddressCount(userId);
            return ResponseEntity.ok(count > 0);
        } catch (Exception e) {
            logger.error("Error checking addresses for user: {} (internal call)", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}