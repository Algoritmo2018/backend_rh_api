package com.minhaempresa.recrutamentoapi.shared.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cache backed by Caffeine ({@code spring.cache.*}). Cached values are immutable DTOs; every
 * write evicts the affected cache. See {@link CacheNames}.
 */
@Configuration
@EnableCaching
public class CacheConfig {}
