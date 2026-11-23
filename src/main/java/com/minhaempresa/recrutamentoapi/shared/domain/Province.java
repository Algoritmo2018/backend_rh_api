package com.minhaempresa.recrutamentoapi.shared.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The 21 provinces of Angola (2024 political-administrative division). The wire value is the exact
 * Portuguese name used by the frontend; the constant is an accent-free English-style identifier.
 */
public enum Province implements WireEnum {
    BENGO("Bengo"),
    BENGUELA("Benguela"),
    BIE("Bié"),
    CABINDA("Cabinda"),
    CUANDO("Cuando"),
    CUBANGO("Cubango"),
    CUANZA_NORTE("Cuanza Norte"),
    CUANZA_SUL("Cuanza Sul"),
    CUNENE("Cunene"),
    HUAMBO("Huambo"),
    HUILA("Huíla"),
    ICOLO_E_BENGO("Icolo e Bengo"),
    LUANDA("Luanda"),
    LUNDA_NORTE("Lunda Norte"),
    LUNDA_SUL("Lunda Sul"),
    MALANJE("Malanje"),
    MOXICO("Moxico"),
    MOXICO_LESTE("Moxico Leste"),
    NAMIBE("Namibe"),
    UIGE("Uíge"),
    ZAIRE("Zaire");

    private final String value;

    Province(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Province fromValue(String value) {
        return WireEnums.fromValue(Province.class, value);
    }
}
