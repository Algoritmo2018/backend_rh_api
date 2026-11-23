package com.minhaempresa.recrutamentoapi.settings.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhaempresa.recrutamentoapi.shared.domain.Codes;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodesAndVersionsTest {

    @Test
    void codesAreAccentFreeSlugs() {
        assertThat(Codes.slugify("Não cumpre os requisitos obrigatórios")).isEqualTo("nao-cumpre-os-requisitos-obrigatorios");
        assertThat(Codes.slugify("  Pedido de documentos (WhatsApp)!  ")).isEqualTo("pedido-de-documentos-whatsapp");
        assertThat(Codes.slugify("???")).isEqualTo("item");
        assertThat(Codes.slugify("a".repeat(100))).hasSize(60);
    }

    @Test
    void codesAreMadeUnique() {
        Set<String> taken = Set.of("salario", "salario-2");

        assertThat(Codes.unique("Salário", taken::contains)).isEqualTo("salario-3");
        assertThat(Codes.unique("Outro", taken::contains)).isEqualTo("outro");
    }

    @Test
    void consentVersionsAdvanceWithinTheYearAndRestartEachYear() {
        assertThat(CompanySettingsService.nextVersion("2026.1", 2026)).isEqualTo("2026.2");
        assertThat(CompanySettingsService.nextVersion("2026.9", 2026)).isEqualTo("2026.10");
        assertThat(CompanySettingsService.nextVersion("2026.4", 2027)).isEqualTo("2027.1");
        assertThat(CompanySettingsService.nextVersion("inválida", 2026)).isEqualTo("2026.1");
    }
}
