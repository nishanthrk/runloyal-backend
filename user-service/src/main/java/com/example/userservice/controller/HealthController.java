package com.example.userservice.controller;

import com.example.userservice.client.AddressServiceClient;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RestController
@RequestMapping("/actuator")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AddressServiceClient addressServiceClient;

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrapServers;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        Map<String, Object> dependencies = new HashMap<>();
        
        health.put("service", "user-service");
        health.put("version", "1.0.0");
        health.put("timestamp", LocalDateTime.now().toString());
        
        // Check Database connectivity
        Map<String, Object> database = checkDatabase();
        dependencies.put("database", database);
        
        // Check Kafka connectivity
        Map<String, Object> kafka = checkKafka();
        dependencies.put("kafka", kafka);
        
        // Check Address Service connectivity
        Map<String, Object> addressService = checkAddressService();
        dependencies.put("addressService", addressService);
        
        health.put("dependencies", dependencies);
        
        // Determine overall status
        boolean allHealthy = database.get("status").equals("UP") && 
                           kafka.get("status").equals("UP") && 
                           addressService.get("status").equals("UP");
        
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
            
            // Test Kafka connectivity by creating a producer
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
            props.put(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);
            
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
                // Just creating the producer tests connectivity
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

    private Map<String, Object> checkAddressService() {
        Map<String, Object> addressServiceHealth = new HashMap<>();
        try {
            long startTime = System.currentTimeMillis();
            // Try to get address service health endpoint
            ResponseEntity<Map> response = addressServiceClient.getHealth();
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (response.getStatusCode().is2xxSuccessful()) {
                addressServiceHealth.put("status", "UP");
                addressServiceHealth.put("message", "Address Service connection successful");
                addressServiceHealth.put("responseTime", responseTime + "ms");
                addressServiceHealth.put("addressServiceStatus", response.getBody().get("status"));
            } else {
                addressServiceHealth.put("status", "DOWN");
                addressServiceHealth.put("message", "Address Service returned non-2xx status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("Address Service health check failed", e);
            addressServiceHealth.put("status", "DOWN");
            addressServiceHealth.put("message", "Address Service connection failed: " + e.getMessage());
            addressServiceHealth.put("error", e.getClass().getSimpleName());
        }
        return addressServiceHealth;
    }
}
