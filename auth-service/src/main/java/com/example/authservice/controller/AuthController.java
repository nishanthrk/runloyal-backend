package com.example.authservice.controller;

import com.example.authservice.dto.*;
import com.example.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication and user management endpoints")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final AuthService authService;
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    
    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Create a new user account with username, email, and password")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input or user already exists"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        try {
            String clientId = request.getClientId() != null ? request.getClientId() : "web";
            String deviceInfo = request.getDeviceInfo() != null ? request.getDeviceInfo() : 
                              httpRequest.getHeader("User-Agent");
            String ipAddress = getClientIpAddress(httpRequest);
            
            AuthService.AuthResponse authResponse = authService.register(
                request.getUsername(),
                request.getEmail(),
                request.getPassword(),
                clientId,
                deviceInfo,
                ipAddress
            );
            
            AuthResponse response = new AuthResponse(
                authResponse.getAccessToken(),
                authResponse.getRefreshToken(),
                authResponse.getAccessTokenExpiresIn(),
                authResponse.getRefreshTokenExpiresIn(),
                authResponse.getUserId(),
                authResponse.getUsername(),
                authResponse.getEmail(),
                authResponse.getEmailVerified()
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user with username/email and password")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            String clientId = request.getClientId() != null ? request.getClientId() : "web";
            String deviceInfo = request.getDeviceInfo() != null ? request.getDeviceInfo() : 
                              httpRequest.getHeader("User-Agent");
            String ipAddress = getClientIpAddress(httpRequest);
            
            AuthService.AuthResponse authResponse = authService.login(
                request.getIdentifier(),
                request.getPassword(),
                clientId,
                deviceInfo,
                ipAddress
            );
            
            AuthResponse response = new AuthResponse(
                authResponse.getAccessToken(),
                authResponse.getRefreshToken(),
                authResponse.getAccessTokenExpiresIn(),
                authResponse.getRefreshTokenExpiresIn(),
                authResponse.getUserId(),
                authResponse.getUsername(),
                authResponse.getEmail(),
                authResponse.getEmailVerified()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Get a new access token using refresh token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid refresh token"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        try {
            String deviceInfo = request.getDeviceInfo() != null ? request.getDeviceInfo() : 
                              httpRequest.getHeader("User-Agent");
            String ipAddress = getClientIpAddress(httpRequest);
            
            AuthService.AuthResponse authResponse = authService.refreshToken(
                request.getRefreshToken(),
                deviceInfo,
                ipAddress
            );
            
            AuthResponse response = new AuthResponse(
                authResponse.getAccessToken(),
                authResponse.getRefreshToken(),
                authResponse.getAccessTokenExpiresIn(),
                authResponse.getRefreshTokenExpiresIn(),
                authResponse.getUserId(),
                authResponse.getUsername(),
                authResponse.getEmail(),
                authResponse.getEmailVerified()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Logout user and revoke tokens")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logout successful"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            String accessToken = extractTokenFromRequest(request);
            String refreshToken = request.getHeader("X-Refresh-Token");
            
            authService.logout(accessToken, refreshToken);
            
            return ResponseEntity.ok(Map.of("message", "Logout successful"));
        } catch (Exception e) {
            logger.error("Logout failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Logout failed"));
        }
    }
    
    @PostMapping("/logout-all")
    @Operation(summary = "Logout from all devices", description = "Revoke all refresh tokens for the user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logout from all devices successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> logoutAll(HttpServletRequest request) {
        try {
            String accessToken = extractTokenFromRequest(request);
            Optional<UserDto> userOpt = authService.getUserFromToken(accessToken);
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid token"));
            }
            
            authService.logoutAll(userOpt.get().getId());
            
            return ResponseEntity.ok(Map.of("message", "Logged out from all devices"));
        } catch (Exception e) {
            logger.error("Logout all failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Logout all failed"));
        }
    }
    
    @GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Get current user's profile information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getProfile(HttpServletRequest request) {
        try {
            String accessToken = extractTokenFromRequest(request);
            Optional<UserDto> userOpt = authService.getUserFromToken(accessToken);
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid token"));
            }
            
            UserDto user = userOpt.get();
            // TODO: Implement social identities via User Service
            // List<SocialIdentity> socialIdentities = userService.getUserSocialIdentities(user);
            
            UserProfile profile = new UserProfile(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getEmailVerified(),
                user.getEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
            );
            
            // TODO: Add social accounts when social identities are implemented
            // List<UserProfile.SocialAccountInfo> socialAccounts = socialIdentities.stream()
            //         .map(si -> new UserProfile.SocialAccountInfo(
            //             si.getProvider(),
            //             si.getLinkedAt(),
            //             si.getProfile()
            //         ))
            //         .collect(Collectors.toList());
            // 
            // profile.setSocialAccounts(socialAccounts);
            
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            logger.error("Get profile failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get profile"));
        }
    }
    
    @GetMapping("/oauth2/success")
    @Operation(summary = "OAuth2 success callback", description = "Handle successful OAuth2 authentication")
    public ResponseEntity<?> oauth2Success(HttpServletRequest httpRequest) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "OAuth2 authentication failed"));
            }
            
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            String provider = (String) oauth2User.getAttribute("provider");
            String providerUserId = oauth2User.getName();
            String email = oauth2User.getAttribute("email");
            String username = oauth2User.getAttribute("name");
            
            Map<String, Object> profile = new HashMap<>(oauth2User.getAttributes());
            
            String clientId = "web";
            String deviceInfo = httpRequest.getHeader("User-Agent");
            String ipAddress = getClientIpAddress(httpRequest);
            
            AuthService.AuthResponse authResponse = authService.socialLogin(
                provider,
                providerUserId,
                email,
                username,
                profile,
                clientId,
                deviceInfo,
                ipAddress
            );
            
            AuthResponse response = new AuthResponse(
                authResponse.getAccessToken(),
                authResponse.getRefreshToken(),
                authResponse.getAccessTokenExpiresIn(),
                authResponse.getRefreshTokenExpiresIn(),
                authResponse.getUserId(),
                authResponse.getUsername(),
                authResponse.getEmail(),
                authResponse.getEmailVerified()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("OAuth2 success handling failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "OAuth2 authentication failed"));
        }
    }
    
    @PostMapping("/validate")
    @Operation(summary = "Validate token", description = "Validate access token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token is valid"),
        @ApiResponse(responseCode = "401", description = "Token is invalid")
    })
    public ResponseEntity<?> validateToken(HttpServletRequest request) {
        try {
            String accessToken = extractTokenFromRequest(request);
            
            if (accessToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("valid", false, "error", "No token provided"));
            }
            
            boolean isValid = authService.validateToken(accessToken);
            
            if (isValid) {
                Optional<UserDto> userOpt = authService.getUserFromToken(accessToken);
                if (userOpt.isPresent()) {
                    UserDto user = userOpt.get();
                    return ResponseEntity.ok(Map.of(
                        "valid", true,
                        "userId", user.getId(),
                        "username", user.getUsername(),
                        "email", user.getEmail()
                    ));
                }
            }
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Invalid token"));
        } catch (Exception e) {
            logger.error("Token validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Token validation failed"));
        }
    }
    
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}