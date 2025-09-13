package com.example.addressservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class RetryConfig {
    // This configuration enables Spring Retry functionality
    // The @Retryable annotations in KafkaConsumerService will now work
}
