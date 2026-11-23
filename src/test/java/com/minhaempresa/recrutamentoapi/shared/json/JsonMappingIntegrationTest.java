package com.minhaempresa.recrutamentoapi.shared.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.shared.domain.Currency;
import com.minhaempresa.recrutamentoapi.shared.domain.Money;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

/** JSON produced by the application's Jackson 3 {@link JsonMapper}. */
@IntegrationTest
class JsonMappingIntegrationTest {

    @Autowired
    JsonMapper jsonMapper;

    @Test
    void instantAlwaysHasMilliseconds() {
        assertThat(jsonMapper.writeValueAsString(Instant.parse("2026-11-03T09:30:00Z"))).isEqualTo("\"2026-11-03T09:30:00.000Z\"");
        assertThat(jsonMapper.writeValueAsString(Instant.parse("2026-11-03T09:30:00.123456789Z"))).isEqualTo("\"2026-11-03T09:30:00.123Z\"");
    }

    @Test
    void instantIsReadFromTheFrontendFormat() {
        assertThat(jsonMapper.readValue("\"2026-11-03T09:30:00.000Z\"", Instant.class)).isEqualTo(Instant.parse("2026-11-03T09:30:00Z"));
    }

    @Test
    void jvmRunsInUtc() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }

    @Test
    void moneyMatchesTheFrontendShape() {
        String json = jsonMapper.writeValueAsString(new Money(new BigDecimal("350000.00"), Currency.AOA));
        assertThat(json).isEqualTo("{\"amount\":350000.00,\"currency\":\"AOA\"}");
        assertThat(jsonMapper.readValue("{\"amount\":4500,\"currency\":\"USD\"}", Money.class)).isEqualTo(new Money(new BigDecimal("4500"), Currency.USD));
    }

    @Test
    void pageResponseMatchesTheFrontendPaginatedType() {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5);
        String json = jsonMapper.writeValueAsString(PageResponse.from(page, String::toUpperCase));
        assertThat(json).isEqualTo("{\"items\":[\"A\",\"B\"],\"total\":5,\"page\":1,\"pageSize\":2,\"totalPages\":3}");
    }

    @Test
    void emptyPageStillHasOnePage() {
        var page = new PageImpl<String>(List.of(), PageRequest.of(0, 20), 0);
        assertThat(PageResponse.from(page, s -> s).totalPages()).isEqualTo(1);
    }
}
