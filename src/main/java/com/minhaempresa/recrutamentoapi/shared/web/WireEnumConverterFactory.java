package com.minhaempresa.recrutamentoapi.shared.web;

import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * Converts request parameters to {@link WireEnum} constants using the wire value, so
 * {@code ?province=Icolo e Bengo} works like the JSON body. An unknown value results in a 400.
 */
public class WireEnumConverterFactory implements ConverterFactory<String, WireEnum> {

    @Override
    public <T extends WireEnum> Converter<String, T> getConverter(Class<T> targetType) {
        return source -> source.isBlank() ? null : targetType.cast(WireEnums.fromValueOfType(targetType, source.trim()));
    }
}
