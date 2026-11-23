package com.minhaempresa.recrutamentoapi.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} (used by the notification listeners). Spring Boot's task executor runs on
 * virtual threads ({@code spring.threads.virtual.enabled}).
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // Never log the arguments: events may carry one-time tokens or codes
        return (ex, method, params) -> log.error("Async task {}.{} failed", method.getDeclaringClass().getSimpleName(), method.getName(), ex);
    }
}
