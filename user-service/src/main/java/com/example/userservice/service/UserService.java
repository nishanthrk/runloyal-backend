package com.example.userservice.service;

import com.example.userservice.dto.ProfileUpdateRequest;
import com.example.userservice.entity.User;
import com.example.userservice.event.ProfileUpdatedEvent;
import com.example.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    private final UserRepository userRepository;
    private final OutboxService outboxService;
    
    public UserService(UserRepository userRepository, OutboxService outboxService) {
        this.userRepository = userRepository;
        this.outboxService = outboxService;
    }
    
    public User createUser(User user) {
        // Check if username already exists
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists: " + user.getUsername());
        }
        
        // Check if email already exists
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists: " + user.getEmail());
        }
        
        User savedUser = userRepository.save(user);
        logger.info("Created new user: {}", savedUser.getUsername());
        return savedUser;
    }
    
    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }
    
    @Transactional(readOnly = true)
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    @Transactional(readOnly = true)
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    @Transactional(readOnly = true)
    public Optional<User> getUserByUsernameOrEmail(String identifier) {
        return userRepository.findByUsernameOrEmail(identifier);
    }
    
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public List<User> searchUsers(String keyword) {
        return userRepository.searchUsers(keyword);
    }
    
    public User updateUser(Long id, User userDetails) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        
        // Update fields if provided
        if (userDetails.getUsername() != null && !userDetails.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(userDetails.getUsername())) {
                throw new RuntimeException("Username already exists: " + userDetails.getUsername());
            }
            user.setUsername(userDetails.getUsername());
        }
        
        if (userDetails.getEmail() != null && !userDetails.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(userDetails.getEmail())) {
                throw new RuntimeException("Email already exists: " + userDetails.getEmail());
            }
            user.setEmail(userDetails.getEmail());
        }
        
        if (userDetails.getFirstName() != null) {
            user.setFirstName(userDetails.getFirstName());
        }
        
        if (userDetails.getLastName() != null) {
            user.setLastName(userDetails.getLastName());
        }
        
        if (userDetails.getPhoneNumber() != null) {
            user.setPhoneNumber(userDetails.getPhoneNumber());
        }
        
        if (userDetails.getDateOfBirth() != null) {
            user.setDateOfBirth(userDetails.getDateOfBirth());
        }
        
        User updatedUser = userRepository.save(user);
        logger.info("Updated user: {}", updatedUser.getUsername());
        return updatedUser;
    }
    
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        
        userRepository.delete(user);
        logger.info("Deleted user: {}", user.getUsername());
    }
    
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
    
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
    
    public User updateUserProfile(Long id, ProfileUpdateRequest profileRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        
        // Update user fields if provided
        if (profileRequest.getUsername() != null && !profileRequest.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(profileRequest.getUsername())) {
                throw new RuntimeException("Username already exists: " + profileRequest.getUsername());
            }
            user.setUsername(profileRequest.getUsername());
        }
        
        if (profileRequest.getEmail() != null && !profileRequest.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(profileRequest.getEmail())) {
                throw new RuntimeException("Email already exists: " + profileRequest.getEmail());
            }
            user.setEmail(profileRequest.getEmail());
        }
        
        if (profileRequest.getFirstName() != null) {
            user.setFirstName(profileRequest.getFirstName());
        }
        
        if (profileRequest.getLastName() != null) {
            user.setLastName(profileRequest.getLastName());
        }
        
        if (profileRequest.getPhoneNumber() != null) {
            user.setPhoneNumber(profileRequest.getPhoneNumber());
        }
        
        if (profileRequest.getDateOfBirth() != null) {
            user.setDateOfBirth(profileRequest.getDateOfBirth());
        }
        
        // Save user first
        User updatedUser = userRepository.save(user);
        
        // Create ProfileUpdated event with address information
        ProfileUpdatedEvent.AddressInfo addressInfo = null;
        if (profileRequest.getAddress() != null) {
            ProfileUpdateRequest.AddressDto addressDto = profileRequest.getAddress();
            addressInfo = new ProfileUpdatedEvent.AddressInfo(
                addressDto.getStreet(),
                addressDto.getCity(),
                addressDto.getState(),
                addressDto.getZipCode(),
                addressDto.getCountry()
            );
        }
        
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
            updatedUser.getId(),
            updatedUser.getUsername(),
            updatedUser.getEmail(),
            updatedUser.getFirstName(),
            updatedUser.getLastName(),
            updatedUser.getPhoneNumber(),
            addressInfo
        );
        
        // Create outbox event
        outboxService.createOutboxEvent(
            updatedUser.getId().toString(),
            "User",
            "ProfileUpdated",
            event
        );
        
        logger.info("Updated user profile and created outbox event: {}", updatedUser.getUsername());
        return updatedUser;
    }
}