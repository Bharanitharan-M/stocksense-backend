package com.stocksense.stocksense_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // Start with 5 threads
        executor.setMaxPoolSize(20); // Max 20 threads to prevent overwhelming JVM/Python
        executor.setQueueCapacity(500); // Queue up to 500 tasks
        executor.setThreadNamePrefix("AIAsync-");
        // Reject strategy: if queue is full, run in caller's thread (slows down the scheduler)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
