package com.example.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class UserProfile {
    
    @JsonProperty("userId")
    private Long userId;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("emailVerified")
    private Boolean emailVerified;
    
    @JsonProperty("enabled")
    private Boolean enabled;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
    
    @JsonProperty("socialAccounts")
    private List<SocialAccountInfo> socialAccounts;
    
    // Constructors
    public UserProfile() {}
    
    public UserProfile(Long userId, String username, String email, 
                      Boolean emailVerified, Boolean enabled,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.emailVerified = emailVerified;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters and Setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public Boolean getEmailVerified() {
        return emailVerified;
    }
    
    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public List<SocialAccountInfo> getSocialAccounts() {
        return socialAccounts;
    }
    
    public void setSocialAccounts(List<SocialAccountInfo> socialAccounts) {
        this.socialAccounts = socialAccounts;
    }
    
    // Inner class for social account information
    public static class SocialAccountInfo {
        private String provider;
        
        @JsonProperty("linked_at")
        private LocalDateTime linkedAt;
        
        private Map<String, Object> profile;
        
        // Constructors
        public SocialAccountInfo() {}
        
        public SocialAccountInfo(String provider, LocalDateTime linkedAt, Map<String, Object> profile) {
            this.provider = provider;
            this.linkedAt = linkedAt;
            this.profile = profile;
        }
        
        // Getters and Setters
        public String getProvider() {
            return provider;
        }
        
        public void setProvider(String provider) {
            this.provider = provider;
        }
        
        public LocalDateTime getLinkedAt() {
            return linkedAt;
        }
        
        public void setLinkedAt(LocalDateTime linkedAt) {
            this.linkedAt = linkedAt;
        }
        
        public Map<String, Object> getProfile() {
            return profile;
        }
        
        public void setProfile(Map<String, Object> profile) {
            this.profile = profile;
        }
    }
}