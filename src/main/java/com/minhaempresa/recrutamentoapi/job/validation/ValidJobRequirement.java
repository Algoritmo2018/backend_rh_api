package com.minhaempresa.recrutamentoapi.job.validation;

import com.minhaempresa.recrutamentoapi.job.dto.request.JobRequirementRequest;
import com.minhaempresa.recrutamentoapi.job.entity.RequirementCategory;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Every requirement except {@code experience} needs the value compared with the profile (frontend rule). */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidJobRequirement.Validator.class)
public @interface ValidJobRequirement {

    String message() default "{job.requirement.value.required}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidJobRequirement, JobRequirementRequest> {
        @Override
        public boolean isValid(JobRequirementRequest value, ConstraintValidatorContext context) {
            if (value == null || value.category() == null || value.category() == RequirementCategory.EXPERIENCE) {
                return true;
            }
            if (value.value() != null && !value.value().isBlank()) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("{job.requirement.value.required}").addPropertyNode("value").addConstraintViolation();
            return false;
        }
    }
}
