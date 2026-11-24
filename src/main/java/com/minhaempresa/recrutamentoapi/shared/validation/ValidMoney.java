package com.minhaempresa.recrutamentoapi.shared.validation;

import com.minhaempresa.recrutamentoapi.shared.domain.Money;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;

/**
 * A {@link Money} value with a positive amount (at most 2 decimals and 17 integer digits) and a
 * currency. {@code null} is valid: the amount is optional unless combined with {@code @NotNull}.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidMoney.Validator.class)
public @interface ValidMoney {

    String message() default "{validation.money.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidMoney, Money> {
        @Override
        public boolean isValid(Money value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            BigDecimal amount = value.amount();
            return amount != null
                    && value.currency() != null
                    && amount.signum() > 0
                    && amount.stripTrailingZeros().scale() <= 2
                    && amount.precision() - amount.scale() <= 17;
        }
    }
}
