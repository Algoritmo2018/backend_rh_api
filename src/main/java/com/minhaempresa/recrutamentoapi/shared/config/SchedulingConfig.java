package com.minhaempresa.recrutamentoapi.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs: the outbox worker and the daily cleanups. Jobs must be safe with several
 * instances (row locks with {@code SKIP LOCKED}, idempotent deletes).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
