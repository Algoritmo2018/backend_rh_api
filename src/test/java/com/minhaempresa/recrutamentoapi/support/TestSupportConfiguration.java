package com.minhaempresa.recrutamentoapi.support;

import com.minhaempresa.recrutamentoapi.TestcontainersConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.testcontainers.containers.GenericContainer;

/** Beans shared by the integration tests. */
@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfiguration {

    /** Replaces the system clock everywhere it is injected (services, JWT validation). */
    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    /** Takes the SMS channel before the log-only sender. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    RecordingSmsSender recordingSmsSender() {
        return new RecordingSmsSender();
    }

    @Bean
    MailpitClient mailpitClient(GenericContainer<?> mailpitContainer) {
        return new MailpitClient("http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(TestcontainersConfiguration.MAILPIT_HTTP_PORT));
    }
}
