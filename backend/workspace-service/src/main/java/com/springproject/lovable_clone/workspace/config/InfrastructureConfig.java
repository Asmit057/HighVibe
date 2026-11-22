package com.springproject.lovable_clone.workspace.config;

import com.springproject.lovable_clone.common.event.KafkaTopics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.minio.MinioClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Duration;

@Configuration
public class InfrastructureConfig {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.url())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    public NewTopic fileStoreRequestsTopic() {
        return TopicBuilder.name(KafkaTopics.FILE_STORE_REQUESTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic fileStoreResponsesTopic() {
        return TopicBuilder.name(KafkaTopics.FILE_STORE_RESPONSES).partitions(3).replicas(1).build();
    }

    /** Cached file trees are plain Java-serializable records. */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.java()));
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.preview.mode", havingValue = "kubernetes")
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
