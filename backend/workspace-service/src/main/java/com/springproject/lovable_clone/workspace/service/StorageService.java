package com.springproject.lovable_clone.workspace.service;

import com.springproject.lovable_clone.workspace.config.MinioProperties;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Project file bodies in MinIO (S3 API). Object key layout: {projectId}/{path}, so a runner
 * pod can mirror one project with "mc mirror projects/{projectId}/".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        try {
            String bucket = properties.projectBucket();
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket {}", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO is not reachable at {}: {}", properties.url(), e.getMessage());
        }
    }

    public static String objectKey(Long projectId, String path) {
        return projectId + "/" + path;
    }

    public void put(String key, byte[] content, String contentType) {
        try (InputStream in = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.projectBucket())
                    .object(key)
                    .stream(in, content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Could not store " + key, e);
        }
    }

    public byte[] get(String key) {
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.projectBucket())
                .object(key)
                .build())) {
            return in.readAllBytes();
        } catch (ErrorResponseException e) {
            throw new StorageException("File body missing for " + key, e);
        } catch (Exception e) {
            throw new StorageException("Could not read " + key, e);
        }
    }

    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(properties.projectBucket()).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Could not delete " + key, e);
        }
    }


    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
