package io.zefio.jdk.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for validating that a string is not null, not empty, and not composed of only whitespaces.
 * Primarily used for Ingress data validation to ensure core configuration integrity.
 */
@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Constraint(validatedBy = {})
@Documented
public @interface ZefioNotBlank {
	String message() default "must not be blank";
	Class<?>[] groups() default {};
	Class<? extends Payload>[] payload() default {};
}
