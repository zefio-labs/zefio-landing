package io.zefio.jdk.annotation;

import javax.validation.Constraint;
import javax.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for validating that a collection or string is not null and not empty.
 * Ensures mandatory multi-value parameters are provided.
 */
@Target({ FIELD, PARAMETER, METHOD })
@Retention(RUNTIME)
@Constraint(validatedBy = ZefioNotEmptyValidator.class)
@Documented
public @interface ZefioNotEmpty {
	String message() default "must not be empty";
	Class<?>[] groups() default {};
	Class<? extends Payload>[] payload() default {};
}
