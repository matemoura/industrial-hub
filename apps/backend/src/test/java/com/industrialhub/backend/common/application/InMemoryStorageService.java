package com.industrialhub.backend.common.application;

import com.industrialhub.backend.common.infrastructure.StorageService;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class InMemoryStorageService implements StorageService {
    private final Map<String, byte[]> store = new HashMap<>();

    @Override
    public void upload(String key, InputStream content, String contentType, long sizeBytes) {
        try {
            store.put(key, content.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String generatePresignedUrl(String key, Duration ttl) {
        return "http://localhost/presigned/" + key;
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    public boolean contains(String key) {
        return store.containsKey(key);
    }
}
