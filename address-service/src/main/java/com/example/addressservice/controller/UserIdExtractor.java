package com.example.addressservice.controller;

import com.example.addressservice.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class UserIdExtractor {
    
    private static final Logger logger = LoggerFactory.getLogger(UserIdExtractor.class);
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    private final JwtUtil jwtUtil;
    
    @Autowired
    public UserIdExtractor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }
    
    public Long extractUserIdFromRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                logger.error("No request attributes found");
                return null;
            }
            
            HttpServletRequest request = attributes.getRequest();
            
            // First try to get from request attributes (set by JWT filter)
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr instanceof Long) {
                return (Long) userIdAttr;
            }
            
            // Fallback: extract from JWT token directly
            String jwt = getJwtFromRequest(request);
            if (StringUtils.hasText(jwt)) {
                return jwtUtil.extractUserId(jwt);
            }
            
            logger.warn("No user ID found in request");
            return null;
            
        } catch (Exception e) {
            logger.error("Error extracting user ID from request", e);
            return null;
        }
    }
    
    public String extractUsernameFromRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            
            HttpServletRequest request = attributes.getRequest();
            
            // First try to get from request attributes
            Object usernameAttr = request.getAttribute("username");
            if (usernameAttr instanceof String) {
                return (String) usernameAttr;
            }
            
            // Fallback: extract from JWT token directly
            String jwt = getJwtFromRequest(request);
            if (StringUtils.hasText(jwt)) {
                return jwtUtil.extractUsername(jwt);
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Error extracting username from request", e);
            return null;
        }
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        
        return null;
    }
}