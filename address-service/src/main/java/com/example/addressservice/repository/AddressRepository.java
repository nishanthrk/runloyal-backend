package com.example.addressservice.repository;

import com.example.addressservice.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    
    /**
     * Find all addresses for a specific user
     */
    List<Address> findByUserIdOrderByIsPrimaryDescCreatedAtDesc(Long userId);
    
    /**
     * Find the primary address for a user
     */
    Optional<Address> findByUserIdAndIsPrimaryTrue(Long userId);
    
    /**
     * Check if a user has any addresses
     */
    boolean existsByUserId(Long userId);
    
    /**
     * Count addresses for a user
     */
    long countByUserId(Long userId);
    
    /**
     * Delete all addresses for a user
     */
    @Modifying
    @Transactional
    void deleteByUserId(Long userId);
    
    /**
     * Set all addresses for a user as non-primary
     * This is used before setting a new primary address
     */
    @Modifying
    @Transactional
    @Query("UPDATE Address a SET a.isPrimary = false WHERE a.userId = :userId")
    void unsetPrimaryForUser(@Param("userId") Long userId);
    
    /**
     * Set a specific address as primary
     */
    @Modifying
    @Transactional
    @Query("UPDATE Address a SET a.isPrimary = true WHERE a.id = :addressId AND a.userId = :userId")
    void setPrimaryAddress(@Param("addressId") Long addressId, @Param("userId") Long userId);
    
    /**
     * Find addresses by partial city match (for search functionality)
     */
    List<Address> findByUserIdAndCityContainingIgnoreCase(Long userId, String city);
    
    /**
     * Find addresses by country
     */
    List<Address> findByUserIdAndCountryIgnoreCase(Long userId, String country);
    
    /**
     * Find addresses by postal code
     */
    List<Address> findByUserIdAndPostalCode(Long userId, String postalCode);
}