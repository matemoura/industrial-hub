package com.industrialhub.backend.common.infrastructure;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {
    void upload(String key, InputStream content, String contentType, long sizeBytes);
    String generatePresignedUrl(String key, Duration ttl);
    void delete(String key);
}
