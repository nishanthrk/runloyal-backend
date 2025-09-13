package com.example.userservice.controller;

import com.example.userservice.client.AddressServiceClient;
import com.example.userservice.dto.AddressDto;
import com.example.userservice.dto.UserDto;
import com.example.userservice.entity.User;
import com.example.userservice.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/internal/users")
@CrossOrigin(origins = "*")
public class InternalUserController {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalUserController.class);
    
    private final UserService userService;
    private final AddressServiceClient addressServiceClient;
    
    public InternalUserController(UserService userService, AddressServiceClient addressServiceClient) {
        this.userService = userService;
        this.addressServiceClient = addressServiceClient;
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        try {
            Optional<User> user = userService.getUserById(id);
            if (user.isPresent()) {
                UserDto userDto = convertToDto(user.get());
                return ResponseEntity.ok(userDto);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error fetching user by ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/by-email")
    public ResponseEntity<UserDto> getUserByEmail(@RequestParam String email) {
        try {
            Optional<User> user = userService.getUserByEmail(email);
            if (user.isPresent()) {
                UserDto userDto = convertToDto(user.get());
                return ResponseEntity.ok(userDto);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error fetching user by email: {}", email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/by-username")
    public ResponseEntity<UserDto> getUserByUsername(@RequestParam String username) {
        try {
            Optional<User> user = userService.getUserByUsername(username);
            if (user.isPresent()) {
                UserDto userDto = convertToDto(user.get());
                return ResponseEntity.ok(userDto);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error fetching user by username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody UserDto userDto) {
        try {
            User user = convertToEntity(userDto);
            User createdUser = userService.createUser(user);
            UserDto createdUserDto = convertToDto(createdUser);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUserDto);
        } catch (Exception e) {
            logger.error("Error creating user: {}", userDto.getEmail(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @PostMapping("/by-provider")
    public ResponseEntity<UserDto> getUserByProvider(@RequestBody ProviderLookupRequest request) {
        try {
            // This would need to be implemented based on social identity lookup
            // For now, return not found
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error fetching user by provider: {}", request.getProvider(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/link-social")
    public ResponseEntity<UserDto> linkSocialAccount(@RequestBody SocialLinkRequest request) {
        try {
            // This would need to be implemented based on social identity linking
            // For now, return not implemented
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (Exception e) {
            logger.error("Error linking social account: {}", request.getProvider(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setDateOfBirth(user.getDateOfBirth());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        
        // Fetch and include primary address from Address Service
        try {
            Optional<AddressDto> primaryAddress = addressServiceClient.getUserPrimaryAddress(user.getId());
            primaryAddress.ifPresent(dto::setAddress);
        } catch (Exception e) {
            logger.warn("Failed to fetch address for user: {} - {}", user.getId(), e.getMessage());
            // Continue without address if service is unavailable
        }
        
        return dto;
    }
    
    private User convertToEntity(UserDto dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setDateOfBirth(dto.getDateOfBirth());
        return user;
    }
    
    // Request DTOs
    public static class ProviderLookupRequest {
        private String provider;
        private String providerUserId;
        
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getProviderUserId() { return providerUserId; }
        public void setProviderUserId(String providerUserId) { this.providerUserId = providerUserId; }
    }
    
    public static class SocialLinkRequest {
        private Long userId;
        private String provider;
        private String providerUserId;
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getProviderUserId() { return providerUserId; }
        public void setProviderUserId(String providerUserId) { this.providerUserId = providerUserId; }
    }
}

