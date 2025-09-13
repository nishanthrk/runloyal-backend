package com.example.addressservice.controller;

import com.example.addressservice.dto.AddressRequest;
import com.example.addressservice.dto.AddressResponse;
import com.example.addressservice.service.AddressService;
import com.example.addressservice.controller.UserIdExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/addresses")
@Validated
@Tag(name = "Address Management", description = "APIs for managing user addresses")
public class AddressController {
    
    private static final Logger logger = LoggerFactory.getLogger(AddressController.class);
    
    private final AddressService addressService;
    private final UserIdExtractor userIdExtractor;
    
    @Autowired
    public AddressController(AddressService addressService, UserIdExtractor userIdExtractor) {
        this.addressService = addressService;
        this.userIdExtractor = userIdExtractor;
    }
    
    @Operation(summary = "Create a new address", description = "Create a new address for the authenticated user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Address created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(
            @Parameter(description = "Address details", required = true)
            @Valid @RequestBody AddressRequest request) {
        
        Long userId = userIdExtractor.extractUserIdFromRequest();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        logger.info("Creating address for user: {}", userId);
        
        try {
            AddressResponse response = addressService.createAddress(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Error creating address for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @Operation(summary = "Get all user addresses", description = "Retrieve all addresses for the authenticated user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Addresses retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public ResponseEntity<List<AddressResponse>> getUserAddresses() {
        
        Long userId = userIdExtractor.extractUserIdFromRequest();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        logger.debug("Fetching addresses for user: {}", userId);
        
        try {
            List<AddressResponse> addresses = addressService.getUserAddresses(userId);
            return ResponseEntity.ok(addresses);
        } catch (Exception e) {
            logger.error("Error fetching addresses for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @Operation(summary = "Get address by ID", description = "Retrieve a specific address by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Address retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Address not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/{addressId}")
    public ResponseEntity<AddressResponse> getAddressById(
            @Parameter(description = "Address ID", required = true)
            @PathVariable @NotNull @Positive Long addressId) {
        
        Long userId = userIdExtractor.extractUserIdFromRequest();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        logger.debug("Fetching address: {} for user: {}", addressId, userId);
        
        try {
            Optional<AddressResponse> address = addressService.getAddressById(addressId, userId);
            return address.map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error fetching address: {} for user: {}", addressId, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @Operation(summary = "Get primary address", description = "Retrieve the primary address for the authenticated user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Primary address retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "No primary address found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/primary")
    public ResponseEntity<AddressResponse> getPrimaryAddress() {
        
        Long userId = userIdExtractor.extractUserIdFromRequest();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        logger.debug("Fetching primary address for user: {}", userId);
        
        try {
            Optional<AddressResponse> address = addressService.getPrimaryAddress(userId);
            return address.map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error fetching primary address for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @Operation(summary = "Update address", description = "Update an existing address")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Address updated successfully"),
        @ApiResponse(responseCode = "404", description = "Address not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping("/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(
            @Parameter(description = "Address ID", required = true)
            @PathVariable @NotNull @Positive Long addressId,
            @Parameter(description = "Updated address details", required = true)
            @Valid @RequestBody AddressRequest request) {
        
        Long userId = userIdExtractor.extractUserIdFromRequest();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        logger.info("Updating address: {} for user: {}", addressId, userId);
        
        try {
            Optional<AddressResponse> updatedAddress = addressService.updateAddress(addressId, userId, request);
            return updatedAddress.map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error updating address: {} for user: {}", addressId, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @Operation(summary = "Delete address", description = "Delete an existing address")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Address deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Address not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @Parameter(description = "Address ID", required = true)
            @PathVariable @NotNull @Positive Long addressId) {
        
        Long userId = userIdExtractor.extractUserIdFromRequest();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        logger.info("Deleting address: {} for user: {}", addressId, userId);
        
        try {
            boolean deleted = addressService.deleteAddress(addressId, userId);
            return deleted ? ResponseEntity.noContent().build() 
                          : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error deleting address: {} for user: {}", addressId, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @Operation(summary = "Set primary address", description = "Set an address as the primary address")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Primary address set successfully"),
        @ApiResponse(responseCode = "404", description = "Address not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PatchMapping("/{addressId}/primary")
    public ResponseEntity<Void> setPrimaryAddress(
            @Parameter(description = "Address ID", required = true)
            @PathVariable @NotNull @Positive Long addressId) {
        
        Long userId = userIdExtractor.extractUserIdFromRequest();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        logger.info("Setting primary address: {} for user: {}", addressId, userId);
        
        try {
            boolean success = addressService.setPrimaryAddress(addressId, userId);
            return success ? ResponseEntity.ok().build() 
                          : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error setting primary address: {} for user: {}", addressId, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @Operation(summary = "Search addresses by city", description = "Search user addresses by city name")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Addresses retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/search")
    public ResponseEntity<List<AddressResponse>> searchAddressesByCity(
            @Parameter(description = "City name to search for", required = true)
            @RequestParam("city") String city) {
        
        Long userId = userIdExtractor.extractUserIdFromRequest();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        logger.debug("Searching addresses by city: {} for user: {}", city, userId);
        
        try {
            List<AddressResponse> addresses = addressService.searchAddressesByCity(userId, city);
            return ResponseEntity.ok(addresses);
        } catch (Exception e) {
            logger.error("Error searching addresses by city: {} for user: {}", city, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @Operation(summary = "Get addresses by country", description = "Get user addresses filtered by country")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Addresses retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/country/{country}")
    public ResponseEntity<List<AddressResponse>> getAddressesByCountry(
            @Parameter(description = "Country name", required = true)
            @PathVariable String country) {
        
        Long userId = userIdExtractor.extractUserIdFromRequest();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        logger.debug("Fetching addresses by country: {} for user: {}", country, userId);
        
        try {
            List<AddressResponse> addresses = addressService.getAddressesByCountry(userId, country);
            return ResponseEntity.ok(addresses);
        } catch (Exception e) {
            logger.error("Error fetching addresses by country: {} for user: {}", country, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @Operation(summary = "Get address count", description = "Get the total number of addresses for the user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Address count retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/count")
    public ResponseEntity<Long> getAddressCount() {
        
        Long userId = userIdExtractor.extractUserIdFromRequest();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        logger.debug("Fetching address count for user: {}", userId);
        
        try {
            long count = addressService.getAddressCount(userId);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            logger.error("Error fetching address count for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}