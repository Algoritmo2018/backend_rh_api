package com.minhaempresa.recrutamentoapi.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    static String newKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void roundTripWithARandomIvPerValue() {
        SecretCipher cipher = new SecretCipher(new SecretsProperties(newKey(), null));

        String first = cipher.encrypt("senha-de-aplicacao 123 çã");
        String second = cipher.encrypt("senha-de-aplicacao 123 çã");

        assertThat(first).startsWith("v1:").doesNotContain("senha");
        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("senha-de-aplicacao 123 çã");
        assertThat(cipher.decrypt(second)).isEqualTo("senha-de-aplicacao 123 çã");
    }

    @Test
    void aTamperedValueIsRejectedInsteadOfDecryptingToWrongText() {
        SecretCipher cipher = new SecretCipher(new SecretsProperties(newKey(), null));
        byte[] data = Base64.getDecoder().decode(cipher.encrypt("segredo").substring(3));
        data[data.length - 5] ^= 0x01;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(data);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("segredo");
        assertThatThrownBy(() -> cipher.decrypt("v1:AAAA")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("plain")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theWrongKeyCannotDecrypt() {
        String stored = new SecretCipher(new SecretsProperties(newKey(), null)).encrypt("segredo");

        SecretCipher other = new SecretCipher(new SecretsProperties(newKey(), null));

        assertThatThrownBy(() -> other.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void afterARotationOldValuesAreReadAndNewValuesUseTheNewKey() {
        String oldKey = newKey();
        String newKey = newKey();
        String stored = new SecretCipher(new SecretsProperties(oldKey, null)).encrypt("segredo");

        SecretCipher rotated = new SecretCipher(new SecretsProperties(newKey, oldKey));
        String rewritten = rotated.encrypt("segredo");

        assertThat(rotated.decrypt(stored)).isEqualTo("segredo");
        assertThat(new SecretCipher(new SecretsProperties(newKey, null)).decrypt(rewritten)).isEqualTo("segredo");
    }

    @Test
    void withoutAKeyNothingCanBeEncrypted() {
        SecretCipher cipher = new SecretCipher(new SecretsProperties("", null));

        assertThat(cipher.isConfigured()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt("segredo")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidKeysStopTheStartup() {
        assertThatThrownBy(() -> new SecretCipher(new SecretsProperties("not base64!", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_SECRETS_KEY");
        assertThatThrownBy(() -> new SecretCipher(new SecretsProperties(Base64.getEncoder().encodeToString(new byte[16]), null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new SecretCipher(new SecretsProperties(newKey(), "curta")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_SECRETS_KEY_PREVIOUS");
    }
}
