package com.minhaempresa.recrutamentoapi.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrontendLinksTest {

    private final FrontendLinks links = new FrontendLinks(new AppProperties(
            "https://fehoiram.co.ao/software_rh",
            "https://fehoiram.co.ao/software_rh/candidato",
            new AppProperties.Defaults("Angolana", "Português", java.time.ZoneId.of("Africa/Luanda")),
            new AppProperties.Cors(List.of("https://fehoiram.co.ao")),
            new AppProperties.Jwt("x".repeat(32), "api", Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofDays(30), Duration.ofSeconds(20))));

    @Test
    void linksUseTheFrontendRoutesUnderTheBaseUrl() {
        assertThat(links.login()).isEqualTo("https://fehoiram.co.ao/software_rh/login");
        assertThat(links.forgotPassword()).isEqualTo("https://fehoiram.co.ao/software_rh/recuperar-senha");
        assertThat(links.passwordReset("abc_DEF-123")).isEqualTo("https://fehoiram.co.ao/software_rh/redefinir-senha?token=abc_DEF-123");
        assertThat(links.invitation("t")).isEqualTo("https://fehoiram.co.ao/software_rh/aceitar-convite?token=t");
        assertThat(links.emailVerification("t")).isEqualTo("https://fehoiram.co.ao/software_rh/confirmar-email?token=t");
    }

    @Test
    void tokensAreEncoded() {
        assertThat(links.passwordReset("a b&c")).endsWith("?token=a%20b%26c");
    }
}
