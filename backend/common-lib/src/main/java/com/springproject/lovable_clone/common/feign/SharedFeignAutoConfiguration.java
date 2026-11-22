package com.springproject.lovable_clone.common.feign;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;

import java.util.List;

/**
 * Spring Cloud OpenFeign fills {@link FeignHttpMessageConverters} lazily without locking: it stores an empty
 * list first and adds the converters afterwards. Two concurrent first calls of a Feign client can therefore see
 * the empty list and fail with "'messageConverters' must not be empty". Feign declares its bean with
 * {@code @ConditionalOnMissingBean}, so every Feign client uses this thread-safe one instead.
 */
@AutoConfiguration
@ConditionalOnClass(FeignHttpMessageConverters.class)
public class SharedFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FeignHttpMessageConverters feignHttpMessageConverters(ObjectProvider<HttpMessageConverter<?>> messageConverters,
                                                                 ObjectProvider<HttpMessageConverterCustomizer> customizers) {
        return new ThreadSafeFeignHttpMessageConverters(messageConverters, customizers);
    }

    static class ThreadSafeFeignHttpMessageConverters extends FeignHttpMessageConverters {

        ThreadSafeFeignHttpMessageConverters(ObjectProvider<HttpMessageConverter<?>> messageConverters,
                                             ObjectProvider<HttpMessageConverterCustomizer> customizers) {
            super(messageConverters, customizers);
        }

        @Override
        public synchronized List<HttpMessageConverter<?>> getConverters() {
            return super.getConverters();
        }
    }
}
