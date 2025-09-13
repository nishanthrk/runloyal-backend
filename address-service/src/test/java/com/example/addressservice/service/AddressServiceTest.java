package com.example.addressservice.service;

import com.example.addressservice.dto.AddressRequest;
import com.example.addressservice.dto.AddressResponse;
import com.example.addressservice.entity.Address;
import com.example.addressservice.event.ProfileUpdatedEvent;
import com.example.addressservice.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private AddressService addressService;

    private ProfileUpdatedEvent profileEvent;
    private ProfileUpdatedEvent.AddressInfo addressInfo;
    private Address existingAddress;

    @BeforeEach
    void setUp() {
        // Setup address info
        addressInfo = new ProfileUpdatedEvent.AddressInfo();
        addressInfo.setStreet("123 Main St");
        addressInfo.setCity("New York");
        addressInfo.setState("NY");
        addressInfo.setZipCode("10001");
        addressInfo.setCountry("USA");

        // Setup profile event
        profileEvent = new ProfileUpdatedEvent();
        profileEvent.setUserId(1L);
        profileEvent.setUsername("testuser");
        profileEvent.setEmail("test@example.com");
        profileEvent.setFirstName("John");
        profileEvent.setLastName("Doe");
        profileEvent.setPhoneNumber("+1234567890");
        profileEvent.setAddress(addressInfo);
        profileEvent.setTimestamp(LocalDateTime.now());

        // Setup existing address
        existingAddress = new Address();
        existingAddress.setId(1L);
        existingAddress.setUserId(1L);
        existingAddress.setLine1("456 Old St");
        existingAddress.setCity("Boston");
        existingAddress.setState("MA");
        existingAddress.setPostalCode("02101");
        existingAddress.setCountry("USA");
        existingAddress.setIsPrimary(true);
    }

    @Test
    void testUpsertAddressFromProfile_WithNullAddress_ShouldReturn() {
        // Given
        profileEvent.setAddress(null);

        // When
        addressService.upsertAddressFromProfile(profileEvent);

        // Then
        verify(addressRepository, never()).findByUserIdAndIsPrimaryTrue(any());
        verify(addressRepository, never()).save(any());
    }

    @Test
    void testUpsertAddressFromProfile_WithExistingPrimaryAddress_ShouldUpdate() {
        // Given
        when(addressRepository.findByUserIdAndIsPrimaryTrue(1L))
                .thenReturn(Optional.of(existingAddress));
        when(addressRepository.save(any(Address.class)))
                .thenReturn(existingAddress);

        // When
        addressService.upsertAddressFromProfile(profileEvent);

        // Then
        verify(addressRepository).findByUserIdAndIsPrimaryTrue(1L);
        verify(addressRepository).save(any(Address.class));
        
        // Verify the address was updated with new values
        assertEquals("123 Main St", existingAddress.getLine1());
        assertEquals("New York", existingAddress.getCity());
        assertEquals("NY", existingAddress.getState());
        assertEquals("10001", existingAddress.getPostalCode());
        assertEquals("USA", existingAddress.getCountry());
        assertTrue(existingAddress.getIsPrimary());
    }

    @Test
    void testUpsertAddressFromProfile_WithoutExistingAddress_ShouldCreateNew() {
        // Given
        when(addressRepository.findByUserIdAndIsPrimaryTrue(1L))
                .thenReturn(Optional.empty());
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        addressService.upsertAddressFromProfile(profileEvent);

        // Then
        verify(addressRepository).findByUserIdAndIsPrimaryTrue(1L);
        verify(addressRepository).save(argThat(address -> 
                address.getUserId().equals(1L) &&
                address.getLine1().equals("123 Main St") &&
                address.getCity().equals("New York") &&
                address.getState().equals("NY") &&
                address.getPostalCode().equals("10001") &&
                address.getCountry().equals("USA") &&
                address.getIsPrimary()
        ));
    }

    @Test
    void testUpsertAddressFromProfile_WithNullStreet_ShouldSetEmptyString() {
        // Given
        addressInfo.setStreet(null);
        when(addressRepository.findByUserIdAndIsPrimaryTrue(1L))
                .thenReturn(Optional.empty());
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        addressService.upsertAddressFromProfile(profileEvent);

        // Then
        verify(addressRepository).save(argThat(address -> 
                address.getLine1().equals("")
        ));
    }

    @Test
    void testUpsertAddressFromProfile_WithPartialAddressInfo_ShouldHandleNulls() {
        // Given
        addressInfo.setCity(null);
        addressInfo.setState(null);
        addressInfo.setZipCode(null);
        addressInfo.setCountry(null);
        
        when(addressRepository.findByUserIdAndIsPrimaryTrue(1L))
                .thenReturn(Optional.empty());
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        addressService.upsertAddressFromProfile(profileEvent);

        // Then
        verify(addressRepository).save(argThat(address -> 
                address.getLine1().equals("123 Main St") &&
                address.getCity() == null &&
                address.getState() == null &&
                address.getPostalCode() == null &&
                address.getCountry() == null
        ));
    }

    @Test
    void testUpsertAddressFromProfile_ShouldBeIdempotent() {
        // Given
        when(addressRepository.findByUserIdAndIsPrimaryTrue(1L))
                .thenReturn(Optional.of(existingAddress));
        when(addressRepository.save(any(Address.class)))
                .thenReturn(existingAddress);

        // When - call twice with same event
        addressService.upsertAddressFromProfile(profileEvent);
        addressService.upsertAddressFromProfile(profileEvent);

        // Then - should be called twice but result should be the same
        verify(addressRepository, times(2)).findByUserIdAndIsPrimaryTrue(1L);
        verify(addressRepository, times(2)).save(any(Address.class));
        
        // Address should have the same final state
        assertEquals("123 Main St", existingAddress.getLine1());
        assertEquals("New York", existingAddress.getCity());
    }

    // ===== CREATE ADDRESS TESTS =====
    
    @Test
    void testCreateAddress_WithValidRequest_ShouldCreateAddress() {
        // Given
        Long userId = 1L;
        AddressRequest request = new AddressRequest();
        request.setLine1("123 Main St");
        request.setCity("New York");
        request.setState("NY");
        request.setCountry("USA");
        request.setPostalCode("10001");
        request.setIsPrimary(false);

        Address savedAddress = new Address();
        savedAddress.setId(1L);
        savedAddress.setUserId(userId);
        savedAddress.setLine1(request.getLine1());
        savedAddress.setCity(request.getCity());
        savedAddress.setState(request.getState());
        savedAddress.setCountry(request.getCountry());
        savedAddress.setPostalCode(request.getPostalCode());
        savedAddress.setIsPrimary(true); // Should be primary if no existing addresses
        savedAddress.setCreatedAt(LocalDateTime.now());
        savedAddress.setUpdatedAt(LocalDateTime.now());

        when(addressRepository.existsByUserId(userId)).thenReturn(false);
        when(addressRepository.save(any(Address.class))).thenReturn(savedAddress);

        // When
        AddressResponse response = addressService.createAddress(userId, request);

        // Then
        assertNotNull(response);
        assertEquals(savedAddress.getId(), response.getId());
        assertEquals(savedAddress.getUserId(), response.getUserId());
        assertEquals(savedAddress.getLine1(), response.getLine1());
        assertTrue(response.getIsPrimary()); // Should be primary as first address
        verify(addressRepository).save(any(Address.class));
        verify(addressRepository, never()).unsetPrimaryForUser(userId);
    }

    @Test
    void testCreateAddress_WithPrimaryRequest_ShouldUnsetExistingPrimary() {
        // Given
        Long userId = 1L;
        AddressRequest request = new AddressRequest();
        request.setLine1("456 Oak Ave");
        request.setCity("Boston");
        request.setState("MA");
        request.setIsPrimary(true);

        Address savedAddress = new Address();
        savedAddress.setId(2L);
        savedAddress.setUserId(userId);
        savedAddress.setLine1(request.getLine1());
        savedAddress.setCity(request.getCity());
        savedAddress.setState(request.getState());
        savedAddress.setIsPrimary(true);
        savedAddress.setCreatedAt(LocalDateTime.now());
        savedAddress.setUpdatedAt(LocalDateTime.now());

        when(addressRepository.save(any(Address.class))).thenReturn(savedAddress);

        // When
        AddressResponse response = addressService.createAddress(userId, request);

        // Then
        assertNotNull(response);
        assertTrue(response.getIsPrimary());
        verify(addressRepository).unsetPrimaryForUser(userId);
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void testCreateAddress_WithExistingAddresses_ShouldNotBePrimary() {
        // Given
        Long userId = 1L;
        AddressRequest request = new AddressRequest();
        request.setLine1("789 Pine St");
        request.setCity("Chicago");
        request.setState("IL");
        request.setIsPrimary(false);

        Address savedAddress = new Address();
        savedAddress.setId(3L);
        savedAddress.setUserId(userId);
        savedAddress.setLine1(request.getLine1());
        savedAddress.setCity(request.getCity());
        savedAddress.setState(request.getState());
        savedAddress.setIsPrimary(false);
        savedAddress.setCreatedAt(LocalDateTime.now());
        savedAddress.setUpdatedAt(LocalDateTime.now());

        when(addressRepository.existsByUserId(userId)).thenReturn(true);
        when(addressRepository.save(any(Address.class))).thenReturn(savedAddress);

        // When
        AddressResponse response = addressService.createAddress(userId, request);

        // Then
        assertNotNull(response);
        assertFalse(response.getIsPrimary());
        verify(addressRepository, never()).unsetPrimaryForUser(userId);
        verify(addressRepository).save(any(Address.class));
    }

    // ===== UPDATE ADDRESS TESTS =====
    
    @Test
    void testUpdateAddress_WithValidRequest_ShouldUpdateAddress() {
        // Given
        Long addressId = 1L;
        Long userId = 1L;
        AddressRequest request = new AddressRequest();
        request.setLine1("Updated Address");
        request.setCity("Updated City");
        request.setState("UC");
        request.setCountry("Updated Country");
        request.setPostalCode("12345");
        request.setIsPrimary(false);

        Address existingAddress = new Address();
        existingAddress.setId(addressId);
        existingAddress.setUserId(userId);
        existingAddress.setLine1("Old Address");
        existingAddress.setCity("Old City");
        existingAddress.setIsPrimary(false);
        existingAddress.setCreatedAt(LocalDateTime.now().minusDays(1));
        existingAddress.setUpdatedAt(LocalDateTime.now().minusDays(1));

        Address updatedAddress = new Address();
        updatedAddress.setId(addressId);
        updatedAddress.setUserId(userId);
        updatedAddress.setLine1(request.getLine1());
        updatedAddress.setCity(request.getCity());
        updatedAddress.setState(request.getState());
        updatedAddress.setCountry(request.getCountry());
        updatedAddress.setPostalCode(request.getPostalCode());
        updatedAddress.setIsPrimary(false);
        updatedAddress.setCreatedAt(existingAddress.getCreatedAt());
        updatedAddress.setUpdatedAt(LocalDateTime.now());

        when(addressRepository.findById(addressId))
                .thenReturn(Optional.of(existingAddress));
        when(addressRepository.save(any(Address.class))).thenReturn(updatedAddress);

        // When
        Optional<AddressResponse> response = addressService.updateAddress(addressId, userId, request);

        // Then
        assertTrue(response.isPresent());
        assertEquals(request.getLine1(), response.get().getLine1());
        assertEquals(request.getCity(), response.get().getCity());
        assertEquals(request.getState(), response.get().getState());
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void testUpdateAddress_WithNonExistentAddress_ShouldReturnEmpty() {
        // Given
        Long addressId = 999L;
        Long userId = 1L;
        AddressRequest request = new AddressRequest();
        request.setLine1("Some Address");

        when(addressRepository.findById(addressId))
                .thenReturn(Optional.empty());

        // When
        Optional<AddressResponse> response = addressService.updateAddress(addressId, userId, request);

        // Then
        assertFalse(response.isPresent());
        verify(addressRepository, never()).save(any(Address.class));
    }

    @Test
    void testUpdateAddress_SetAsPrimary_ShouldUnsetOtherPrimary() {
        // Given
        Long addressId = 1L;
        Long userId = 1L;
        AddressRequest request = new AddressRequest();
        request.setLine1("Primary Address");
        request.setIsPrimary(true);

        Address existingAddress = new Address();
        existingAddress.setId(addressId);
        existingAddress.setUserId(userId);
        existingAddress.setLine1("Old Address");
        existingAddress.setIsPrimary(false);
        existingAddress.setCreatedAt(LocalDateTime.now().minusDays(1));
        existingAddress.setUpdatedAt(LocalDateTime.now().minusDays(1));

        Address updatedAddress = new Address();
        updatedAddress.setId(addressId);
        updatedAddress.setUserId(userId);
        updatedAddress.setLine1(request.getLine1());
        updatedAddress.setIsPrimary(true);
        updatedAddress.setCreatedAt(existingAddress.getCreatedAt());
        updatedAddress.setUpdatedAt(LocalDateTime.now());

        when(addressRepository.findById(addressId))
                .thenReturn(Optional.of(existingAddress));
        when(addressRepository.save(any(Address.class))).thenReturn(updatedAddress);

        // When
        Optional<AddressResponse> response = addressService.updateAddress(addressId, userId, request);

        // Then
        assertTrue(response.isPresent());
        assertTrue(response.get().getIsPrimary());
        verify(addressRepository).unsetPrimaryForUser(userId);
        verify(addressRepository).save(any(Address.class));
    }

    // ===== DELETE ADDRESS TESTS =====
    
    @Test
    void testDeleteAddress_WithExistingAddress_ShouldDeleteSuccessfully() {
        // Given
        Long addressId = 1L;
        Long userId = 1L;
        
        Address existingAddress = new Address();
        existingAddress.setId(addressId);
        existingAddress.setUserId(userId);
        existingAddress.setLine1("Address to delete");
        existingAddress.setIsPrimary(false);
        
        when(addressRepository.findById(addressId))
                .thenReturn(Optional.of(existingAddress));
        
        // When
        boolean result = addressService.deleteAddress(addressId, userId);
        
        // Then
        assertTrue(result);
        verify(addressRepository).delete(existingAddress);
    }
    
    @Test
    void testDeleteAddress_WithNonExistentAddress_ShouldReturnFalse() {
        // Given
        Long addressId = 999L;
        Long userId = 1L;
        
        when(addressRepository.findById(addressId))
                .thenReturn(Optional.empty());
        
        // When
        boolean result = addressService.deleteAddress(addressId, userId);
        
        // Then
        assertFalse(result);
        verify(addressRepository, never()).delete(any(Address.class));
    }
    
    @Test
    void testDeleteAddress_WithPrimaryAddress_ShouldDeleteSuccessfully() {
        // Given
        Long addressId = 1L;
        Long userId = 1L;
        
        Address primaryAddress = new Address();
        primaryAddress.setId(addressId);
        primaryAddress.setUserId(userId);
        primaryAddress.setLine1("Primary address to delete");
        primaryAddress.setIsPrimary(true);
        
        when(addressRepository.findById(addressId))
                .thenReturn(Optional.of(primaryAddress));
        
        // When
        boolean result = addressService.deleteAddress(addressId, userId);
        
        // Then
        assertTrue(result);
        verify(addressRepository).delete(primaryAddress);
    }

    // ===== SET PRIMARY ADDRESS TESTS =====
    
    @Test
    void testSetPrimaryAddress_WithExistingAddress_ShouldSetAsPrimary() {
        // Given
        Long addressId = 1L;
        Long userId = 1L;
        
        Address existingAddress = new Address();
        existingAddress.setId(addressId);
        existingAddress.setUserId(userId);
        existingAddress.setLine1("Address to make primary");
        existingAddress.setIsPrimary(false);
        
        when(addressRepository.findById(addressId))
                .thenReturn(Optional.of(existingAddress));
        
        // When
        boolean result = addressService.setPrimaryAddress(addressId, userId);
        
        // Then
        assertTrue(result);
        verify(addressRepository).unsetPrimaryForUser(userId);
        verify(addressRepository).setPrimaryAddress(addressId, userId);
    }
    
    @Test
    void testSetPrimaryAddress_WithNonExistentAddress_ShouldReturnFalse() {
        // Given
        Long addressId = 999L;
        Long userId = 1L;
        
        when(addressRepository.findById(addressId))
                .thenReturn(Optional.empty());
        
        // When
        boolean result = addressService.setPrimaryAddress(addressId, userId);
        
        // Then
        assertFalse(result);
        verify(addressRepository, never()).unsetPrimaryForUser(userId);
        verify(addressRepository, never()).setPrimaryAddress(addressId, userId);
    }

    // ===== SEARCH AND QUERY TESTS =====
    
    @Test
    void testGetUserAddresses_WithExistingAddresses_ShouldReturnList() {
        // Given
        Long userId = 1L;
        
        Address address1 = new Address();
        address1.setId(1L);
        address1.setUserId(userId);
        address1.setLine1("123 Main St");
        address1.setCity("New York");
        address1.setIsPrimary(true);
        address1.setCreatedAt(LocalDateTime.now());
        address1.setUpdatedAt(LocalDateTime.now());
        
        Address address2 = new Address();
        address2.setId(2L);
        address2.setUserId(userId);
        address2.setLine1("456 Oak Ave");
        address2.setCity("Boston");
        address2.setIsPrimary(false);
        address2.setCreatedAt(LocalDateTime.now());
        address2.setUpdatedAt(LocalDateTime.now());
        
        List<Address> addresses = Arrays.asList(address1, address2);
        
        when(addressRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId))
                .thenReturn(addresses);
        
        // When
        List<AddressResponse> result = addressService.getUserAddresses(userId);
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(address1.getId(), result.get(0).getId());
        assertEquals(address2.getId(), result.get(1).getId());
        assertTrue(result.get(0).getIsPrimary());
        assertFalse(result.get(1).getIsPrimary());
    }
    
    @Test
    void testGetUserAddresses_WithNoAddresses_ShouldReturnEmptyList() {
        // Given
        Long userId = 1L;
        
        when(addressRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId))
                .thenReturn(Collections.emptyList());
        
        // When
        List<AddressResponse> result = addressService.getUserAddresses(userId);
        
        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testGetAddressById_WithExistingAddress_ShouldReturnAddress() {
        // Given
        Long addressId = 1L;
        Long userId = 1L;
        
        Address address = new Address();
        address.setId(addressId);
        address.setUserId(userId);
        address.setLine1("123 Main St");
        address.setCity("New York");
        address.setIsPrimary(true);
        address.setCreatedAt(LocalDateTime.now());
        address.setUpdatedAt(LocalDateTime.now());
        
        when(addressRepository.findById(addressId))
                .thenReturn(Optional.of(address));
        
        // When
        Optional<AddressResponse> result = addressService.getAddressById(addressId, userId);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals(address.getId(), result.get().getId());
        assertEquals(address.getLine1(), result.get().getLine1());
        assertEquals(address.getCity(), result.get().getCity());
    }
    
    @Test
    void testGetAddressById_WithNonExistentAddress_ShouldReturnEmpty() {
        // Given
        Long addressId = 999L;
        Long userId = 1L;
        
        when(addressRepository.findById(addressId))
                .thenReturn(Optional.empty());
        
        // When
        Optional<AddressResponse> result = addressService.getAddressById(addressId, userId);
        
        // Then
        assertFalse(result.isPresent());
    }
    
    @Test
    void testSearchAddressesByCity_WithMatchingAddresses_ShouldReturnList() {
        // Given
        Long userId = 1L;
        String city = "New York";
        
        Address address1 = new Address();
        address1.setId(1L);
        address1.setUserId(userId);
        address1.setLine1("123 Main St");
        address1.setCity(city);
        address1.setIsPrimary(true);
        address1.setCreatedAt(LocalDateTime.now());
        address1.setUpdatedAt(LocalDateTime.now());
        
        Address address2 = new Address();
        address2.setId(2L);
        address2.setUserId(userId);
        address2.setLine1("456 Broadway");
        address2.setCity(city);
        address2.setIsPrimary(false);
        address2.setCreatedAt(LocalDateTime.now());
        address2.setUpdatedAt(LocalDateTime.now());
        
        List<Address> addresses = Arrays.asList(address1, address2);
        
        when(addressRepository.findByUserIdAndCityContainingIgnoreCase(userId, city))
                .thenReturn(addresses);
        
        // When
        List<AddressResponse> result = addressService.searchAddressesByCity(userId, city);
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(city, result.get(0).getCity());
        assertEquals(city, result.get(1).getCity());
    }
    
    @Test
    void testSearchAddressesByCity_WithNoMatches_ShouldReturnEmptyList() {
        // Given
        Long userId = 1L;
        String city = "NonExistentCity";
        
        when(addressRepository.findByUserIdAndCityContainingIgnoreCase(userId, city))
                .thenReturn(Collections.emptyList());
        
        // When
        List<AddressResponse> result = addressService.searchAddressesByCity(userId, city);
        
        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testGetPrimaryAddress_WithExistingPrimary_ShouldReturnAddress() {
        // Given
        Long userId = 1L;
        
        Address primaryAddress = new Address();
        primaryAddress.setId(1L);
        primaryAddress.setUserId(userId);
        primaryAddress.setLine1("123 Main St");
        primaryAddress.setCity("New York");
        primaryAddress.setIsPrimary(true);
        primaryAddress.setCreatedAt(LocalDateTime.now());
        primaryAddress.setUpdatedAt(LocalDateTime.now());
        
        when(addressRepository.findByUserIdAndIsPrimaryTrue(userId))
                .thenReturn(Optional.of(primaryAddress));
        
        // When
        Optional<AddressResponse> result = addressService.getPrimaryAddress(userId);
        
        // Then
        assertTrue(result.isPresent());
        assertTrue(result.get().getIsPrimary());
        assertEquals(primaryAddress.getId(), result.get().getId());
    }
    
    @Test
    void testGetPrimaryAddress_WithNoPrimary_ShouldReturnEmpty() {
        // Given
        Long userId = 1L;
        
        when(addressRepository.findByUserIdAndIsPrimaryTrue(userId))
                .thenReturn(Optional.empty());
        
        // When
        Optional<AddressResponse> result = addressService.getPrimaryAddress(userId);
        
        // Then
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetAddressesByCountry_WithMatchingAddresses_ShouldReturnList() {
        // Given
        Long userId = 1L;
        String country = "USA";
        
        Address address1 = new Address();
        address1.setId(1L);
        address1.setUserId(userId);
        address1.setLine1("123 Main St");
        address1.setCountry(country);
        address1.setCreatedAt(LocalDateTime.now());
        address1.setUpdatedAt(LocalDateTime.now());
        
        List<Address> addresses = Arrays.asList(address1);
        
        when(addressRepository.findByUserIdAndCountryIgnoreCase(userId, country))
                .thenReturn(addresses);
        
        // When
        List<AddressResponse> result = addressService.getAddressesByCountry(userId, country);
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(country, result.get(0).getCountry());
    }
}