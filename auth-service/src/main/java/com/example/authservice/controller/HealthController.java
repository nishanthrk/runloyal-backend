package com.example.authservice.controller;

import com.example.authservice.service.UserServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserServiceClient userServiceClient;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        Map<String, Object> dependencies = new HashMap<>();
        
        health.put("service", "auth-service");
        health.put("version", "1.0.0");
        health.put("timestamp", LocalDateTime.now().toString());
        
        // Check Database connectivity
        Map<String, Object> database = checkDatabase();
        dependencies.put("database", database);
        
        // Check Redis connectivity
        Map<String, Object> redis = checkRedis();
        dependencies.put("redis", redis);
        
        // Check User Service connectivity
        Map<String, Object> userService = checkUserService();
        dependencies.put("userService", userService);
        
        health.put("dependencies", dependencies);
        
        // Determine overall status
        boolean allHealthy = database.get("status").equals("UP") && 
                           redis.get("status").equals("UP") && 
                           userService.get("status").equals("UP");
        
        health.put("status", allHealthy ? "UP" : "DOWN");
        
        return ResponseEntity.status(allHealthy ? 200 : 503).body(health);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> dbHealth = new HashMap<>();
        try {
            try (Connection connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid(5); // 5 second timeout
                dbHealth.put("status", isValid ? "UP" : "DOWN");
                dbHealth.put("message", isValid ? "Database connection successful" : "Database connection invalid");
                dbHealth.put("responseTime", "N/A");
            }
        } catch (Exception e) {
            logger.error("Database health check failed", e);
            dbHealth.put("status", "DOWN");
            dbHealth.put("message", "Database connection failed: " + e.getMessage());
            dbHealth.put("error", e.getClass().getSimpleName());
        }
        return dbHealth;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> redisHealth = new HashMap<>();
        try {
            long startTime = System.currentTimeMillis();
            String testKey = "health-check:" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(testKey, "test", java.time.Duration.ofSeconds(10));
            String value = (String) redisTemplate.opsForValue().get(testKey);
            redisTemplate.delete(testKey);
            long responseTime = System.currentTimeMillis() - startTime;
            
            if ("test".equals(value)) {
                redisHealth.put("status", "UP");
                redisHealth.put("message", "Redis connection successful");
                redisHealth.put("responseTime", responseTime + "ms");
            } else {
                redisHealth.put("status", "DOWN");
                redisHealth.put("message", "Redis read/write test failed");
            }
        } catch (Exception e) {
            logger.error("Redis health check failed", e);
            redisHealth.put("status", "DOWN");
            redisHealth.put("message", "Redis connection failed: " + e.getMessage());
            redisHealth.put("error", e.getClass().getSimpleName());
        }
        return redisHealth;
    }

    private Map<String, Object> checkUserService() {
        Map<String, Object> userServiceHealth = new HashMap<>();
        try {
            long startTime = System.currentTimeMillis();
            // Try to get user service health endpoint
            ResponseEntity<Map> response = userServiceClient.getHealth();
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (response.getStatusCode().is2xxSuccessful()) {
                userServiceHealth.put("status", "UP");
                userServiceHealth.put("message", "User Service connection successful");
                userServiceHealth.put("responseTime", responseTime + "ms");
                userServiceHealth.put("userServiceStatus", response.getBody().get("status"));
            } else {
                userServiceHealth.put("status", "DOWN");
                userServiceHealth.put("message", "User Service returned non-2xx status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("User Service health check failed", e);
            userServiceHealth.put("status", "DOWN");
            userServiceHealth.put("message", "User Service connection failed: " + e.getMessage());
            userServiceHealth.put("error", e.getClass().getSimpleName());
        }
        return userServiceHealth;
    }
}
