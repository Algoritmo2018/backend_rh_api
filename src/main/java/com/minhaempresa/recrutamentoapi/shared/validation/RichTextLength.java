package com.minhaempresa.recrutamentoapi.shared.validation;

import com.minhaempresa.recrutamentoapi.shared.text.RichText;
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
 * Rich text (HTML) with at least {@code minText} characters of visible text (after sanitizing, like
 * the frontend {@code htmlToText}) and at most {@code maxHtml} characters of HTML. {@code null} is
 * valid: combine with {@code @NotNull} when required.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RichTextLength.Validator.class)
public @interface RichTextLength {

    String message();

    int minText() default 0;

    int maxHtml() default 20_000;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<RichTextLength, String> {

        private int minText;
        private int maxHtml;

        @Override
        public void initialize(RichTextLength annotation) {
            minText = annotation.minText();
            maxHtml = annotation.maxHtml();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.length() <= maxHtml && RichText.toText(RichText.sanitize(value)).length() >= minText;
        }
    }
}
