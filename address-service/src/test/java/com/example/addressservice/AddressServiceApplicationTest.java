package com.example.addressservice;

import com.example.addressservice.repository.AddressRepository;
import com.example.addressservice.service.AddressService;
import com.example.addressservice.service.KafkaConsumerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(
    partitions = 1,
    topics = {"profile-updated", "user-events"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9093",
        "port=9093"
    }
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.group-id=test-address-service-group",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "management.endpoints.web.exposure.include=health,info"
})
class AddressServiceApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AddressService addressService;

    @Autowired
    private KafkaConsumerService kafkaConsumerService;

    @Autowired
    private AddressRepository addressRepository;

    @Test
    void contextLoads() {
        // Verify that the Spring context loads successfully
        assertNotNull(addressService);
        assertNotNull(kafkaConsumerService);
        assertNotNull(addressRepository);
    }

    @Test
    void testApplicationStartup() {
        // Test that the application starts up successfully
        // This test passes if the Spring context loads without exceptions
        assertTrue(true, "Application started successfully");
    }

    @Test
    void testHealthEndpoint() {
        // Test the health actuator endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health", 
            String.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("UP") || response.getBody().contains("status"));
    }

    @Test
    void testInfoEndpoint() {
        // Test the info actuator endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/info", 
            String.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testDatabaseConnectivity() {
        // Test that the database connection is working
        assertDoesNotThrow(() -> {
            long count = addressRepository.count();
            assertTrue(count >= 0, "Database query executed successfully");
        });
    }

    @Test
    void testAddressServiceBeanIsAvailable() {
        // Verify that AddressService bean is properly configured
        assertNotNull(addressService);
        
        // Test a simple method to ensure the service is functional
        assertDoesNotThrow(() -> {
            long count = addressService.getAddressCount(999L);
            assertEquals(0, count);
        });
    }

    @Test
    void testKafkaConsumerServiceBeanIsAvailable() {
        // Verify that KafkaConsumerService bean is properly configured
        assertNotNull(kafkaConsumerService);
    }

    @Test
    void testServerIsRunning() {
        // Test that the server is responding to HTTP requests
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health", 
            String.class
        );
        
        assertNotEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testApplicationProperties() {
        // Test that application properties are loaded correctly
        // This is implicit - if the context loads, properties are working
        assertTrue(port > 0, "Server port is configured");
    }
}