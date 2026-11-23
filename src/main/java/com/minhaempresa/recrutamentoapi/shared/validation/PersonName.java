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
 * Full name, like the frontend {@code nameSchema}: 5 to 120 characters and at least two words (name
 * and surname). {@code null} is valid: combine with {@code @NotBlank}.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PersonName.Validator.class)
public @interface PersonName {

    String message() default "{validation.name.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<PersonName, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            String trimmed = value.trim();
            return trimmed.length() >= 5 && trimmed.length() <= 120 && trimmed.split("\\s+").length >= 2;
        }
    }
}
