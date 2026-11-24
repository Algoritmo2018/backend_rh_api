package com.minhaempresa.recrutamentoapi.job.validation;

import com.minhaempresa.recrutamentoapi.job.dto.request.ScreeningQuestionRequest;
import com.minhaempresa.recrutamentoapi.job.entity.ScreeningQuestionType;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Set;

/**
 * Rules of the frontend {@code jobFormSchema} for screening questions: choice questions need at least
 * 2 options; eliminatory questions need the expected answer and must be yes/no or single choice; the
 * expected answer is {@code sim}/{@code nao} (yes/no) or one of the options.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidScreeningQuestion.Validator.class)
public @interface ValidScreeningQuestion {

    String message() default "{error.validation}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidScreeningQuestion, ScreeningQuestionRequest> {

        /** Values of the yes/no select of the frontend. */
        static final Set<String> YES_NO_ANSWERS = Set.of("sim", "nao");

        @Override
        public boolean isValid(ScreeningQuestionRequest value, ConstraintValidatorContext context) {
            if (value == null || value.type() == null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            boolean valid = true;
            boolean choice = value.type() == ScreeningQuestionType.SINGLE_CHOICE || value.type() == ScreeningQuestionType.MULTIPLE_CHOICE;
            List<String> options = value.options() == null ? List.of() : value.options();
            if (choice && options.size() < 2) {
                valid = fail(context, "options", "{job.question.options.min}");
            }
            if (value.eliminatory()) {
                String expected = value.expectedAnswer() == null ? "" : value.expectedAnswer().trim();
                if (value.type() != ScreeningQuestionType.YES_NO && value.type() != ScreeningQuestionType.SINGLE_CHOICE) {
                    valid = fail(context, "eliminatory", "{job.question.eliminatory.type}");
                } else if (expected.isEmpty()) {
                    valid = fail(context, "expectedAnswer", "{job.question.expected.required}");
                } else if (value.type() == ScreeningQuestionType.YES_NO ? !YES_NO_ANSWERS.contains(expected) : options.stream().noneMatch(o -> o.trim().equals(expected))) {
                    valid = fail(context, "expectedAnswer", "{job.question.expected.invalid}");
                }
            }
            return valid;
        }

        private static boolean fail(ConstraintValidatorContext context, String field, String messageTemplate) {
            context.buildConstraintViolationWithTemplate(messageTemplate).addPropertyNode(field).addConstraintViolation();
            return false;
        }
    }
}
