package com.minhaempresa.recrutamentoapi.notification.email;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * Adds a TEXT-mode resolver for the plain-text version of emails ({@code templates/email/*.txt}).
 * Spring Boot registers it in the Thymeleaf engine next to its default HTML resolver; it is consulted
 * first but only claims names ending in {@code .txt}.
 */
@Configuration
public class EmailTemplateConfig {

    @Bean
    ITemplateResolver textEmailTemplateResolver() {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setPrefix("classpath:/templates/");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setResolvablePatterns(Set.of("*.txt"));
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        resolver.setCheckExistence(true);
        resolver.setOrder(1);
        return resolver;
    }
}
