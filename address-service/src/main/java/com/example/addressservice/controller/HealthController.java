package com.example.addressservice.controller;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.addressservice.client.UserServiceClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RestController
@RequestMapping("/health")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private UserServiceClient userServiceClient;

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrapServers;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        Map<String, Object> dependencies = new HashMap<>();
        
        health.put("service", "address-service");
        health.put("version", "1.0.0");
        health.put("timestamp", LocalDateTime.now().toString());
        
        // Check Database connectivity
        Map<String, Object> database = checkDatabase();
        dependencies.put("database", database);
        
        // Check Kafka connectivity
        Map<String, Object> kafka = checkKafka();
        dependencies.put("kafka", kafka);
        
        // Check User Service connectivity
        Map<String, Object> userService = checkUserService();
        dependencies.put("userService", userService);
        
        health.put("dependencies", dependencies);
        
        // Determine overall status
        boolean allHealthy = database.get("status").equals("UP") && 
                           kafka.get("status").equals("UP") &&
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

    private Map<String, Object> checkKafka() {
        Map<String, Object> kafkaHealth = new HashMap<>();
        try {
            long startTime = System.currentTimeMillis();
            
            // Test Kafka connectivity by creating a consumer
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "health-check-group");
            props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
            props.put(ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);
            
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                // Just creating the consumer tests connectivity
                long responseTime = System.currentTimeMillis() - startTime;
                kafkaHealth.put("status", "UP");
                kafkaHealth.put("message", "Kafka connection successful");
                kafkaHealth.put("responseTime", responseTime + "ms");
                kafkaHealth.put("bootstrapServers", kafkaBootstrapServers);
            }
        } catch (Exception e) {
            logger.error("Kafka health check failed", e);
            kafkaHealth.put("status", "DOWN");
            kafkaHealth.put("message", "Kafka connection failed: " + e.getMessage());
            kafkaHealth.put("error", e.getClass().getSimpleName());
            kafkaHealth.put("bootstrapServers", kafkaBootstrapServers);
        }
        return kafkaHealth;
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
