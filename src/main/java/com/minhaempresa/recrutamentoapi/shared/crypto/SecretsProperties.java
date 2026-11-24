package com.minhaempresa.recrutamentoapi.shared.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keys that encrypt secrets stored in the database ({@code app.secrets.*}), e.g. SMTP passwords. Only
 * from the environment ({@code APP_SECRETS_KEY}), never in git.
 *
 * @param key current key: 32 random bytes in base64 ({@code openssl rand -base64 32}); empty = no
 *     secret can be stored
 * @param previousKey the key before a rotation, still accepted to decrypt (empty when not rotating)
 */
@ConfigurationProperties(prefix = "app.secrets")
public record SecretsProperties(String key, String previousKey) {

    @Override
    public String toString() {
        return "SecretsProperties(key=" + (key == null || key.isBlank() ? "<unset>" : "<set>") + ")";
    }
}
