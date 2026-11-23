package com.minhaempresa.recrutamentoapi.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.shared.domain.Currency;
import com.minhaempresa.recrutamentoapi.shared.domain.Money;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Flyway, PostgreSQL extensions and the shared JPA base classes against a real database. */
@IntegrationTest
class PersistenceIntegrationTest {

    @Autowired
    TestSampleRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void flywayAppliedTheMainMigration() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void unaccentExtensionIsEnabled() {
        assertThat(jdbc.queryForObject("SELECT unaccent('Huíla Uíge Bié')", String.class)).isEqualTo("Huila Uige Bie");
    }

    @Test
    void idsAreUuidVersion7() {
        TestSample saved = repository.saveAndFlush(new TestSample("Ana", Province.LUANDA, null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId().version()).isEqualTo(7);
    }

    @Test
    void auditingFillsTimestamps() {
        Instant before = Instant.now().minusSeconds(1);
        TestSample saved = repository.saveAndFlush(new TestSample("Ana", Province.LUANDA, null));

        assertThat(saved.getCreatedAt()).isAfter(before);
        assertThat(saved.getUpdatedAt()).isAfter(before);
    }

    @Test
    void enumsAreStoredByConstantNameAndMoneyWithTwoDecimals() {
        TestSample saved = repository.saveAndFlush(new TestSample("Ana", Province.ICOLO_E_BENGO, new Money(new BigDecimal("350000"), Currency.AOA)));

        var row = jdbc.queryForMap("SELECT province, price_amount, price_currency FROM test_samples WHERE id = ?", saved.getId());
        assertThat(row.get("province")).isEqualTo("ICOLO_E_BENGO");
        assertThat(row.get("price_currency")).isEqualTo("AOA");
        assertThat((BigDecimal) row.get("price_amount")).isEqualByComparingTo("350000.00").hasScaleOf(2);

        TestSample reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getProvince()).isEqualTo(Province.ICOLO_E_BENGO);
        assertThat(reloaded.getPrice().currency()).isEqualTo(Currency.AOA);
        assertThat(reloaded.getPrice().amount()).isEqualByComparingTo("350000");
    }

    @Test
    void equalityIsByIdOnly() {
        TestSample saved = repository.saveAndFlush(new TestSample("Ana", Province.LUANDA, null));
        TestSample reloaded = repository.findById(saved.getId()).orElseThrow();
        reloaded.setName("Changed");

        assertThat(reloaded).isEqualTo(saved).hasSameHashCodeAs(saved);
        assertThat(new TestSample("Ana", Province.LUANDA, null)).isNotEqualTo(new TestSample("Ana", Province.LUANDA, null));
        assertThat(saved.toString()).isEqualTo("TestSample(id=" + saved.getId() + ")");
    }
}
