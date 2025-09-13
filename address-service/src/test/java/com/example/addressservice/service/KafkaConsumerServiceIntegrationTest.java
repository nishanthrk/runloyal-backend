package com.example.addressservice.service;

import com.example.addressservice.entity.Address;
import com.example.addressservice.event.ProfileUpdatedEvent;
import com.example.addressservice.repository.AddressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"profile-updated"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    }
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.group-id=test-address-service-group",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext
class KafkaConsumerServiceIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaTemplate<String, String> kafkaTemplate;
    private ProfileUpdatedEvent profileEvent;

    @BeforeEach
    void setUp() {
        // Setup Kafka producer for testing
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Setup test data
        ProfileUpdatedEvent.AddressInfo addressInfo = new ProfileUpdatedEvent.AddressInfo();
        addressInfo.setStreet("123 Test Street");
        addressInfo.setCity("Test City");
        addressInfo.setState("TS");
        addressInfo.setZipCode("12345");
        addressInfo.setCountry("Test Country");

        profileEvent = new ProfileUpdatedEvent();
        profileEvent.setUserId(100L);
        profileEvent.setUsername("testuser");
        profileEvent.setEmail("test@example.com");
        profileEvent.setFirstName("Test");
        profileEvent.setLastName("User");
        profileEvent.setPhoneNumber("+1234567890");
        profileEvent.setAddress(addressInfo);
        profileEvent.setTimestamp(LocalDateTime.now());

        // Clean up any existing data
        addressRepository.deleteAll();
    }

    @Test
    @Transactional
    void testProfileUpdatedEventConsumption_ShouldCreateNewAddress() throws Exception {
        // Given
        String eventJson = objectMapper.writeValueAsString(profileEvent);

        // When
        kafkaTemplate.send("profile-updated", eventJson);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Address> savedAddress = addressRepository.findByUserIdAndIsPrimaryTrue(100L);
            assertTrue(savedAddress.isPresent());
            
            Address address = savedAddress.get();
            assertEquals(100L, address.getUserId());
            assertEquals("123 Test Street", address.getLine1());
            assertEquals("Test City", address.getCity());
            assertEquals("TS", address.getState());
            assertEquals("12345", address.getPostalCode());
            assertEquals("Test Country", address.getCountry());
            assertTrue(address.getIsPrimary());
        });
    }

    @Test
    @Transactional
    void testProfileUpdatedEventConsumption_ShouldUpdateExistingAddress() throws Exception {
        // Given - create existing address
        Address existingAddress = new Address();
        existingAddress.setUserId(100L);
        existingAddress.setLine1("Old Street");
        existingAddress.setCity("Old City");
        existingAddress.setState("OC");
        existingAddress.setPostalCode("54321");
        existingAddress.setCountry("Old Country");
        existingAddress.setIsPrimary(true);
        addressRepository.save(existingAddress);

        String eventJson = objectMapper.writeValueAsString(profileEvent);

        // When
        kafkaTemplate.send("profile-updated", eventJson);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Address> updatedAddress = addressRepository.findByUserIdAndIsPrimaryTrue(100L);
            assertTrue(updatedAddress.isPresent());
            
            Address address = updatedAddress.get();
            assertEquals(100L, address.getUserId());
            assertEquals("123 Test Street", address.getLine1());
            assertEquals("Test City", address.getCity());
            assertEquals("TS", address.getState());
            assertEquals("12345", address.getPostalCode());
            assertEquals("Test Country", address.getCountry());
            assertTrue(address.getIsPrimary());
            
            // Should still be the same address entity (updated, not created new)
            assertEquals(existingAddress.getId(), address.getId());
        });
    }

    @Test
    @Transactional
    void testProfileUpdatedEventConsumption_WithNullAddress_ShouldNotCreateAddress() throws Exception {
        // Given
        profileEvent.setAddress(null);
        String eventJson = objectMapper.writeValueAsString(profileEvent);

        // When
        kafkaTemplate.send("profile-updated", eventJson);

        // Then - wait a bit and verify no address was created
        Thread.sleep(2000); // Give some time for processing
        
        Optional<Address> address = addressRepository.findByUserIdAndIsPrimaryTrue(100L);
        assertFalse(address.isPresent());
    }

    @Test
    @Transactional
    void testProfileUpdatedEventConsumption_WithPartialAddressInfo_ShouldHandleNulls() throws Exception {
        // Given
        ProfileUpdatedEvent.AddressInfo partialAddress = new ProfileUpdatedEvent.AddressInfo();
        partialAddress.setStreet("Partial Street");
        partialAddress.setCity(null);
        partialAddress.setState(null);
        partialAddress.setZipCode(null);
        partialAddress.setCountry(null);
        
        profileEvent.setAddress(partialAddress);
        String eventJson = objectMapper.writeValueAsString(profileEvent);

        // When
        kafkaTemplate.send("profile-updated", eventJson);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Address> savedAddress = addressRepository.findByUserIdAndIsPrimaryTrue(100L);
            assertTrue(savedAddress.isPresent());
            
            Address address = savedAddress.get();
            assertEquals("Partial Street", address.getLine1());
            assertNull(address.getCity());
            assertNull(address.getState());
            assertNull(address.getPostalCode());
            assertNull(address.getCountry());
        });
    }

    @Test
    @Transactional
    void testProfileUpdatedEventConsumption_IdempotentProcessing() throws Exception {
        // Given
        String eventJson = objectMapper.writeValueAsString(profileEvent);

        // When - send the same event twice
        kafkaTemplate.send("profile-updated", eventJson);
        kafkaTemplate.send("profile-updated", eventJson);

        // Then - should still have only one address with correct data
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long addressCount = addressRepository.countByUserId(100L);
            assertEquals(1, addressCount);
            
            Optional<Address> savedAddress = addressRepository.findByUserIdAndIsPrimaryTrue(100L);
            assertTrue(savedAddress.isPresent());
            
            Address address = savedAddress.get();
            assertEquals("123 Test Street", address.getLine1());
            assertEquals("Test City", address.getCity());
        });
    }

    @Test
    void testInvalidJsonMessage_ShouldNotCrashConsumer() throws Exception {
        // Given
        String invalidJson = "{invalid json}";

        // When
        kafkaTemplate.send("profile-updated", invalidJson);

        // Then - should not crash and no address should be created
        Thread.sleep(2000); // Give some time for processing
        
        long addressCount = addressRepository.count();
        assertEquals(0, addressCount);
    }
}