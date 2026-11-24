package com.minhaempresa.recrutamentoapi.job.validation;

import com.minhaempresa.recrutamentoapi.job.dto.request.SalaryRangeRequest;
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

/** Salary range: minimum and maximum required and positive, minimum ≤ maximum (frontend messages). */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidSalaryRange.Validator.class)
public @interface ValidSalaryRange {

    String message() default "{error.validation}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidSalaryRange, SalaryRangeRequest> {
        @Override
        public boolean isValid(SalaryRangeRequest value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            boolean valid = true;
            boolean minOk = positive(value.min());
            boolean maxOk = positive(value.max());
            if (!minOk) {
                valid = fail(context, "min", "{job.salary.min.required}");
            }
            if (!maxOk) {
                valid = fail(context, "max", "{job.salary.max.required}");
            }
            if (minOk && maxOk && value.min().compareTo(value.max()) > 0) {
                valid = fail(context, "max", "{job.salary.max.lower}");
            }
            return valid;
        }

        private static boolean positive(BigDecimal amount) {
            return amount != null && amount.signum() > 0;
        }

        private static boolean fail(ConstraintValidatorContext context, String field, String messageTemplate) {
            context.buildConstraintViolationWithTemplate(messageTemplate).addPropertyNode(field).addConstraintViolation();
            return false;
        }
    }
}
