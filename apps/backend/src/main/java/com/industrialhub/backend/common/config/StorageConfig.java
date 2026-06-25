package com.industrialhub.backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.net.URI;

@Configuration
public class StorageConfig {

    @Value("${app.storage.endpoint:}")
    private String endpoint;

    @Value("${app.storage.access-key:}")
    private String accessKey;

    @Value("${app.storage.secret-key:}")
    private String secretKey;

    @Value("${app.storage.region:us-east-1}")
    private String region;

    private AwsCredentialsProvider credentialsProvider() {
        if (accessKey == null || accessKey.isBlank()) {
            return AnonymousCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
            .credentialsProvider(credentialsProvider())
            .region(Region.of(region))
            .forcePathStyle(true);
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
            .credentialsProvider(credentialsProvider())
            .region(Region.of(region));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
