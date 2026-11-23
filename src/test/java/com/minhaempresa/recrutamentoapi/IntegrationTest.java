package com.minhaempresa.recrutamentoapi;

import com.minhaempresa.recrutamentoapi.support.TestSupportConfiguration;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application context against PostgreSQL and Mailpit containers, with MockMvc. Every test class
 * using it (with the same properties) shares the same cached context and containers.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class, TestUsers.class})
public @interface IntegrationTest {}
