package com.industrialhub.backend.common.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;

    @Value("${app.storage.bucket}")
    private String bucket;

    @Value("${app.storage.presign-ttl-minutes:15}")
    private long presignTtlMinutes;

    @Override
    public void upload(String key, InputStream content, String contentType, long sizeBytes) {
        try {
            s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType(contentType).contentLength(sizeBytes).build(),
                RequestBody.fromInputStream(content, sizeBytes));
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageException("Falha no upload para o storage", ex);
        }
    }

    @Override
    public String generatePresignedUrl(String key, Duration ttl) {
        try {
            PresignedGetObjectRequest req = presigner.presignGetObject(b -> b
                .signatureDuration(ttl)
                .getObjectRequest(r -> r.bucket(bucket).key(key)));
            return req.url().toString();
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageException("Falha ao gerar URL pré-assinada", ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException ignored) {
            // idempotente
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageException("Falha ao deletar do storage", ex);
        }
    }
}
