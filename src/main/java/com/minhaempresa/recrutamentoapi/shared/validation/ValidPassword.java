package com.minhaempresa.recrutamentoapi.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Password policy, identical to the frontend {@code passwordSchema}: 8 to 72 characters (72 is also the
 * BCrypt limit) with at least one letter and one digit. {@code null} is valid: combine with
 * {@code @NotNull}.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPassword.Validator.class)
public @interface ValidPassword {

    int MIN_LENGTH = 8;
    int MAX_LENGTH = 72;

    String message() default "{validation.password.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPassword, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.length() >= MIN_LENGTH
                    && value.length() <= MAX_LENGTH
                    && value.chars().anyMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
                    && value.chars().anyMatch(Character::isDigit);
        }
    }
}
