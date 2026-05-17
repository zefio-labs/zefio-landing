package io.zefio.jdk.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Collection;

/**
 * Validator implementation for the {@link ZefioNotEmpty} annotation.
 * Checks if the targeted collection is non-null and contains at least one element.
 */
public class ZefioNotEmptyValidator implements ConstraintValidator<ZefioNotEmpty, Collection<?>> {

	/**
	 * Determines if the provided collection is valid based on its presence and size.
	 *
	 * @param value The collection to validate.
	 * @param context Context in which the constraint is evaluated.
	 * @return true if the collection is not null and not empty, false otherwise.
	 */
	@Override
	public boolean isValid(Collection<?> value, ConstraintValidatorContext context) {
		return value != null && !value.isEmpty();
	}
}
