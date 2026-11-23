package com.minhaempresa.recrutamentoapi.shared.validation;

import com.minhaempresa.recrutamentoapi.shared.domain.PhoneNumbers;
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
 * Angolan mobile number (9 digits starting with 9, with or without +244), like the frontend
 * {@code phoneSchema}. {@code null} is valid: combine with {@code @NotBlank} when required.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AngolanPhone.Validator.class)
public @interface AngolanPhone {

    String message() default "{validation.phone.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<AngolanPhone, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || PhoneNumbers.isValid(value);
        }
    }
}
