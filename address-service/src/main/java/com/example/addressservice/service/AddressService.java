package com.example.addressservice.service;

import com.example.addressservice.dto.AddressRequest;
import com.example.addressservice.dto.AddressResponse;
import com.example.addressservice.entity.Address;
import com.example.addressservice.event.ProfileUpdatedEvent;
import com.example.addressservice.repository.AddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class AddressService {
    
    private static final Logger logger = LoggerFactory.getLogger(AddressService.class);
    
    private final AddressRepository addressRepository;
    
    @Autowired
    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }
    
    /**
     * Create a new address for a user
     */
    public AddressResponse createAddress(Long userId, AddressRequest request) {
        logger.info("Creating address for user: {}", userId);
        
        Address address = new Address();
        address.setUserId(userId);
        address.setLine1(request.getLine1());
        address.setLine2(request.getLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setCountry(request.getCountry());
        address.setPostalCode(request.getPostalCode());
        
        // Handle primary address logic
        if (request.getIsPrimary() != null && request.getIsPrimary()) {
            // If this should be primary, unset any existing primary address
            addressRepository.unsetPrimaryForUser(userId);
            address.setIsPrimary(true);
        } else {
            // If user has no addresses, make this the primary one
            if (!addressRepository.existsByUserId(userId)) {
                address.setIsPrimary(true);
            } else {
                address.setIsPrimary(false);
            }
        }
        
        Address savedAddress = addressRepository.save(address);
        logger.info("Address created with ID: {} for user: {}", savedAddress.getId(), userId);
        
        return AddressResponse.from(savedAddress);
    }
    
    /**
     * Get all addresses for a user
     */
    @Transactional(readOnly = true)
    public List<AddressResponse> getUserAddresses(Long userId) {
        logger.debug("Fetching addresses for user: {}", userId);
        
        List<Address> addresses = addressRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId);
        return addresses.stream()
                .map(AddressResponse::from)
                .collect(Collectors.toList());
    }
    
    /**
     * Get a specific address by ID
     */
    @Transactional(readOnly = true)
    public Optional<AddressResponse> getAddressById(Long addressId, Long userId) {
        logger.debug("Fetching address: {} for user: {}", addressId, userId);
        
        return addressRepository.findById(addressId)
                .filter(address -> address.getUserId().equals(userId))
                .map(AddressResponse::from);
    }
    
    /**
     * Get primary address for a user
     */
    @Transactional(readOnly = true)
    public Optional<AddressResponse> getPrimaryAddress(Long userId) {
        logger.debug("Fetching primary address for user: {}", userId);
        
        return addressRepository.findByUserIdAndIsPrimaryTrue(userId)
                .map(AddressResponse::from);
    }
    
    /**
     * Update an existing address
     */
    public Optional<AddressResponse> updateAddress(Long addressId, Long userId, AddressRequest request) {
        logger.info("Updating address: {} for user: {}", addressId, userId);
        
        Optional<Address> addressOpt = addressRepository.findById(addressId)
                .filter(address -> address.getUserId().equals(userId));
        
        if (addressOpt.isEmpty()) {
            logger.warn("Address not found or access denied: {} for user: {}", addressId, userId);
            return Optional.empty();
        }
        
        Address address = addressOpt.get();
        address.setLine1(request.getLine1());
        address.setLine2(request.getLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setCountry(request.getCountry());
        address.setPostalCode(request.getPostalCode());
        
        // Handle primary address logic
        if (request.getIsPrimary() != null && request.getIsPrimary() && !address.getIsPrimary()) {
            // If setting as primary, unset any existing primary address
            addressRepository.unsetPrimaryForUser(userId);
            address.setIsPrimary(true);
        } else if (request.getIsPrimary() != null && !request.getIsPrimary() && address.getIsPrimary()) {
            // If unsetting primary, check if user has other addresses to set as primary
            address.setIsPrimary(false);
            List<Address> otherAddresses = addressRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId)
                    .stream()
                    .filter(addr -> !addr.getId().equals(addressId))
                    .collect(Collectors.toList());
            
            if (!otherAddresses.isEmpty()) {
                // Set the first (oldest) address as primary
                Address newPrimary = otherAddresses.get(otherAddresses.size() - 1);
                newPrimary.setIsPrimary(true);
                addressRepository.save(newPrimary);
            }
        }
        
        Address updatedAddress = addressRepository.save(address);
        logger.info("Address updated: {} for user: {}", addressId, userId);
        
        return Optional.of(AddressResponse.from(updatedAddress));
    }
    
    /**
     * Delete an address
     */
    public boolean deleteAddress(Long addressId, Long userId) {
        logger.info("Deleting address: {} for user: {}", addressId, userId);
        
        Optional<Address> addressOpt = addressRepository.findById(addressId)
                .filter(address -> address.getUserId().equals(userId));
        
        if (addressOpt.isEmpty()) {
            logger.warn("Address not found or access denied: {} for user: {}", addressId, userId);
            return false;
        }
        
        Address address = addressOpt.get();
        boolean wasPrimary = address.getIsPrimary();
        
        addressRepository.delete(address);
        
        // If deleted address was primary, set another address as primary
        if (wasPrimary) {
            List<Address> remainingAddresses = addressRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId);
            if (!remainingAddresses.isEmpty()) {
                Address newPrimary = remainingAddresses.get(0);
                newPrimary.setIsPrimary(true);
                addressRepository.save(newPrimary);
                logger.info("Set new primary address: {} for user: {}", newPrimary.getId(), userId);
            }
        }
        
        logger.info("Address deleted: {} for user: {}", addressId, userId);
        return true;
    }
    
    /**
     * Set an address as primary
     */
    public boolean setPrimaryAddress(Long addressId, Long userId) {
        logger.info("Setting primary address: {} for user: {}", addressId, userId);
        
        Optional<Address> addressOpt = addressRepository.findById(addressId)
                .filter(address -> address.getUserId().equals(userId));
        
        if (addressOpt.isEmpty()) {
            logger.warn("Address not found or access denied: {} for user: {}", addressId, userId);
            return false;
        }
        
        // Unset current primary and set new primary
        addressRepository.unsetPrimaryForUser(userId);
        addressRepository.setPrimaryAddress(addressId, userId);
        
        logger.info("Primary address set: {} for user: {}", addressId, userId);
        return true;
    }
    
    /**
     * Search addresses by city
     */
    @Transactional(readOnly = true)
    public List<AddressResponse> searchAddressesByCity(Long userId, String city) {
        logger.debug("Searching addresses by city: {} for user: {}", city, userId);
        
        List<Address> addresses = addressRepository.findByUserIdAndCityContainingIgnoreCase(userId, city);
        return addresses.stream()
                .map(AddressResponse::from)
                .collect(Collectors.toList());
    }
    
    /**
     * Get addresses by country
     */
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddressesByCountry(Long userId, String country) {
        logger.debug("Fetching addresses by country: {} for user: {}", country, userId);
        
        List<Address> addresses = addressRepository.findByUserIdAndCountryIgnoreCase(userId, country);
        return addresses.stream()
                .map(AddressResponse::from)
                .collect(Collectors.toList());
    }
    
    /**
     * Delete all addresses for a user (used when user is deleted)
     */
    public void deleteAllUserAddresses(Long userId) {
        logger.info("Deleting all addresses for user: {}", userId);
        addressRepository.deleteByUserId(userId);
    }
    
    /**
     * Get address count for a user
     */
    @Transactional(readOnly = true)
    public long getAddressCount(Long userId) {
        return addressRepository.countByUserId(userId);
    }
    
    /**
     * Upsert address from ProfileUpdated event (idempotent operation)
     */
    @Transactional
    public void upsertAddressFromProfile(ProfileUpdatedEvent event) {
        if (event.getAddress() == null) {
            return; // No address information to process
        }
        
        ProfileUpdatedEvent.AddressInfo addressInfo = event.getAddress();
        Long userId = event.getUserId();
        
        // Find existing primary address for the user
        Optional<Address> existingPrimary = addressRepository.findByUserIdAndIsPrimaryTrue(userId);
        
        Address address;
        if (existingPrimary.isPresent()) {
            // Update existing primary address
            address = existingPrimary.get();
        } else {
            // Create new address
            address = new Address();
            address.setUserId(userId);
            address.setIsPrimary(true);
        }
        
        // Update address fields from the event
        address.setLine1(addressInfo.getStreet() != null ? addressInfo.getStreet() : "");
        address.setCity(addressInfo.getCity());
        address.setState(addressInfo.getState());
        address.setCountry(addressInfo.getCountry());
        address.setPostalCode(addressInfo.getZipCode());
        
        // Save the address (create or update)
        addressRepository.save(address);
    }
}