package com.minhaempresa.recrutamentoapi.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

/** Wire-value mapping of {@link WireEnum}s with a plain Jackson 3 mapper (no Spring). */
class WireEnumTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void provinceListMatchesTheFrontend() {
        assertThat(WireEnums.values(Province.class)).containsExactly(
                "Bengo", "Benguela", "Bié", "Cabinda", "Cuando", "Cubango", "Cuanza Norte", "Cuanza Sul", "Cunene", "Huambo", "Huíla",
                "Icolo e Bengo", "Luanda", "Lunda Norte", "Lunda Sul", "Malanje", "Moxico", "Moxico Leste", "Namibe", "Uíge", "Zaire");
    }

    @Test
    void serializesToTheWireValue() {
        assertThat(mapper.writeValueAsString(Province.ICOLO_E_BENGO)).isEqualTo("\"Icolo e Bengo\"");
        assertThat(mapper.writeValueAsString(Currency.AOA)).isEqualTo("\"AOA\"");
    }

    @Test
    void deserializesFromTheWireValue() {
        assertThat(mapper.readValue("\"Huíla\"", Province.class)).isEqualTo(Province.HUILA);
        assertThat(mapper.readValue("\"USD\"", Currency.class)).isEqualTo(Currency.USD);
    }

    @Test
    void rejectsTheJavaConstantNameAndUnknownValues() {
        assertThatThrownBy(() -> mapper.readValue("\"ICOLO_E_BENGO\"", Province.class)).isInstanceOf(DatabindException.class);
        assertThatThrownBy(() -> mapper.readValue("\"Lisboa\"", Province.class)).isInstanceOf(DatabindException.class);
    }

    @Test
    void lookupByValue() {
        assertThat(WireEnums.fromValue(Province.class, "Moxico Leste")).isEqualTo(Province.MOXICO_LESTE);
        assertThatThrownBy(() -> WireEnums.fromValue(Province.class, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
