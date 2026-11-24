package com.minhaempresa.recrutamentoapi.client.validation;

import com.minhaempresa.recrutamentoapi.client.dto.request.ContactRequestCreateRequest;
import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestKind;
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
 * Company requests need the company name (at least 2 characters) and between 1 and 500 vacancies, like
 * the frontend {@code ContactForm}. Errors are reported on the {@code company} and {@code vacancies}
 * fields.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidContactRequest.Validator.class)
public @interface ValidContactRequest {

    String message() default "{error.validation}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidContactRequest, ContactRequestCreateRequest> {

        static final int MAX_VACANCIES = 500;

        @Override
        public boolean isValid(ContactRequestCreateRequest value, ConstraintValidatorContext context) {
            if (value == null || value.kind() != ContactRequestKind.COMPANY) {
                return true;
            }
            boolean valid = true;
            context.disableDefaultConstraintViolation();
            if (value.company() == null || value.company().trim().length() < 2) {
                valid = fail(context, "company", "{contact.company.required}");
            }
            if (value.vacancies() == null || value.vacancies() < 1) {
                valid = fail(context, "vacancies", "{contact.vacancies.min}");
            } else if (value.vacancies() > MAX_VACANCIES) {
                valid = fail(context, "vacancies", "{contact.vacancies.max}");
            }
            return valid;
        }

        private static boolean fail(ConstraintValidatorContext context, String field, String messageTemplate) {
            context.buildConstraintViolationWithTemplate(messageTemplate).addPropertyNode(field).addConstraintViolation();
            return false;
        }
    }
}
