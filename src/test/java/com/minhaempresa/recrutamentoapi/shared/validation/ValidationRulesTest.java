package com.minhaempresa.recrutamentoapi.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhaempresa.recrutamentoapi.shared.domain.PhoneNumbers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Same rules as the frontend ({@code lib/validation.ts}, {@code lib/format.ts}). */
class ValidationRulesTest {

    @ParameterizedTest
    @CsvSource({
        "923456789,          +244923456789",
        "923 456 789,        +244923456789",
        "+244 923 456 789,   +244923456789",
        "244923456789,       +244923456789",
        "(+244) 923-456-789, +244923456789",
    })
    void validAngolanMobilesAreNormalizedToE164(String input, String expected) {
        assertThat(PhoneNumbers.normalize(input)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"823456789", "92345678", "9234567890", "+351923456789", "abc", ""})
    void invalidPhonesAreRejected(String input) {
        assertThat(PhoneNumbers.isValid(input)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "Senha123,  true",
        "abcdefg1,  true",
        "12345678,  false",
        "abcdefgh,  false",
        "Ab1,       false",
    })
    void passwordNeedsEightCharactersWithLettersAndDigits(String password, boolean valid) {
        assertThat(new ValidPassword.Validator().isValid(password, null)).isEqualTo(valid);
    }

    @ParameterizedTest
    @ValueSource(ints = {72, 73})
    void passwordIsLimitedTo72Characters(int length) {
        String password = "a1" + "x".repeat(length - 2);
        assertThat(new ValidPassword.Validator().isValid(password, null)).isEqualTo(length <= 72);
    }

    @ParameterizedTest
    @CsvSource({
        "Ana Kiala,       true",
        "Edna Mbala Kiala, true",
        "Ana,             false",
        "Anaaaaaa,        false",
    })
    void nameNeedsFirstNameAndSurname(String name, boolean valid) {
        assertThat(new PersonName.Validator().isValid(name, null)).isEqualTo(valid);
    }
}
