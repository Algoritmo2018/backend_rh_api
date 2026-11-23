package com.minhaempresa.recrutamentoapi.shared.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves user-facing messages from {@code messages.properties}. User-facing text must never be
 * hard-coded in Java: add a key to the properties file and resolve it here.
 */
@Component
@RequiredArgsConstructor
public class Messages {

    private final MessageSource messageSource;

    /**
     * Message for {@code key} in the current locale, with {@code {0}}-style arguments. Returns the key
     * itself when it is missing, so a forgotten key is visible instead of failing the request.
     */
    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
