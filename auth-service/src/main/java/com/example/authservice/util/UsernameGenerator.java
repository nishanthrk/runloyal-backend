package com.example.authservice.util;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class UsernameGenerator {
    
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final int MAX_USERNAME_LENGTH = 30;
    private static final int MIN_USERNAME_LENGTH = 3;
    
    /**
     * Generate a unique username from OAuth2 provider data
     * Uses the provider's unique ID as the primary strategy for uniqueness
     */
    public String generateUsername(String email, String fullName, String provider, String providerId) {
        String baseUsername;
        
        // Primary strategy: Use provider's unique ID (guaranteed unique)
        if (providerId != null && !providerId.trim().isEmpty()) {
            baseUsername = provider.toLowerCase() + "_" + providerId;
        } else if (email != null && email.contains("@")) {
            // Fallback: Extract username from email
            baseUsername = email.substring(0, email.indexOf("@"));
        } else if (fullName != null && !fullName.trim().isEmpty()) {
            // Fallback: Generate from full name
            baseUsername = generateFromFullName(fullName);
        } else {
            // Last resort: Use provider + timestamp
            baseUsername = provider.toLowerCase() + "_" + System.currentTimeMillis();
        }
        
        // Clean and validate username
        baseUsername = cleanUsername(baseUsername);
        
        // Ensure minimum length
        if (baseUsername.length() < MIN_USERNAME_LENGTH) {
            baseUsername = baseUsername + "_" + provider.toLowerCase();
        }
        
        // Ensure maximum length
        if (baseUsername.length() > MAX_USERNAME_LENGTH) {
            baseUsername = baseUsername.substring(0, MAX_USERNAME_LENGTH);
        }
        
        return baseUsername;
    }
    
    /**
     * Generate username from full name
     */
    private String generateFromFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "user";
        }
        
        String[] nameParts = fullName.trim().split("\\s+");
        StringBuilder username = new StringBuilder();
        
        if (nameParts.length >= 2) {
            // Use first name + last name initial
            username.append(nameParts[0].toLowerCase());
            username.append(nameParts[nameParts.length - 1].charAt(0)).append(".");
        } else {
            // Use only first name
            username.append(nameParts[0].toLowerCase());
        }
        
        return username.toString();
    }
    
    /**
     * Clean username to make it URL-safe and valid
     */
    private String cleanUsername(String username) {
        if (username == null) {
            return "user";
        }
        
        // Convert to lowercase
        username = username.toLowerCase();
        
        // Replace spaces and special characters with underscores
        // Keep dots and hyphens for provider IDs like "google_123456789"
        username = username.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Remove multiple consecutive underscores
        username = username.replaceAll("_{2,}", "_");
        
        // Remove leading/trailing underscores and dots
        username = username.replaceAll("^[._]+|[._]+$", "");
        
        // Ensure it starts with a letter or number
        if (username.isEmpty() || !Character.isLetterOrDigit(username.charAt(0))) {
            username = "user_" + username;
        }
        
        return username;
    }
    
    /**
     * Generate a unique username with suffix if needed
     */
    public String generateUniqueUsername(String baseUsername, int suffix) {
        if (suffix <= 0) {
            return baseUsername;
        }
        
        String suffixStr = String.valueOf(suffix);
        int maxSuffixLength = MAX_USERNAME_LENGTH - baseUsername.length() - 1; // -1 for underscore
        
        if (suffixStr.length() > maxSuffixLength) {
            // Truncate base username to accommodate suffix
            int newBaseLength = MAX_USERNAME_LENGTH - suffixStr.length() - 1;
            baseUsername = baseUsername.substring(0, newBaseLength);
        }
        
        return baseUsername + "_" + suffixStr;
    }
    
    /**
     * Validate if username meets requirements
     */
    public boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        
        username = username.trim();
        
        // Check length
        if (username.length() < MIN_USERNAME_LENGTH || username.length() > MAX_USERNAME_LENGTH) {
            return false;
        }
        
        // Check pattern
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return false;
        }
        
        // Check if starts with letter or number
        if (!Character.isLetterOrDigit(username.charAt(0))) {
            return false;
        }
        
        return true;
    }
}
