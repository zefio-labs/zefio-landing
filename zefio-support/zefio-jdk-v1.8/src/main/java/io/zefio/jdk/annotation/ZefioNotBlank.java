package io.zefio.jdk.annotation;

import javax.validation.Constraint;
import javax.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for validating that a string is not null, not empty, and not composed only of whitespaces.
 * Typically applied to mandatory string fields during the Ingress phase.
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
