package com.springproject.lovable_clone.workspace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        @DefaultValue("http://localhost:9000") String url,
        @DefaultValue("highvibe") String accessKey,
        @DefaultValue("highvibe-secret") String secretKey,
        @DefaultValue("projects") String projectBucket
) {
}
