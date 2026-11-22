package com.springproject.lovable_clone.common.feign;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.core.ResolvableType;
import org.springframework.http.converter.HttpMessageConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class SharedFeignAutoConfigurationTest {

    private static final int THREADS = 16;

    @Test
    void concurrentFirstCallsAllSeeTheConverters() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ObjectProvider<HttpMessageConverter<?>> converters = beanFactory.getBeanProvider(ResolvableType.forClass(HttpMessageConverter.class));
        ObjectProvider<HttpMessageConverterCustomizer> customizers = beanFactory.getBeanProvider(HttpMessageConverterCustomizer.class);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < 200; round++) {
                FeignHttpMessageConverters shared = new SharedFeignAutoConfiguration().feignHttpMessageConverters(converters, customizers);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> sizes = new ArrayList<>();
                for (int t = 0; t < THREADS; t++) {
                    sizes.add(pool.submit(() -> {
                        start.await();
                        return shared.getConverters().size();
                    }));
                }
                start.countDown();
                for (Future<Integer> size : sizes) {
                    assertThat(size.get()).isPositive();
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void replacesFeignsOwnConvertersBean() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SharedFeignAutoConfiguration.class))
                .run(context -> assertThat(context.getBean(FeignHttpMessageConverters.class))
                        .isInstanceOf(SharedFeignAutoConfiguration.ThreadSafeFeignHttpMessageConverters.class));
    }
}
