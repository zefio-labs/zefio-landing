package io.zefio.jdk.annotation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import java.util.Collection;

/**
 * Validator implementation for {@link ZefioNotEmpty}.
 * Checks if the targeted collection instance is non-null and contains at least one element.
 */
public class ZefioNotEmptyValidator implements ConstraintValidator<ZefioNotEmpty, Collection<?>> {

	/**
	 * Performs the actual validation check.
	 *
	 * @param value   The collection to validate.
	 * @param context Context in which the constraint is evaluated.
	 * @return true if the collection is not null and not empty; false otherwise.
	 */
	@Override
	public boolean isValid(Collection<?> value, ConstraintValidatorContext context) {
		return value != null && !value.isEmpty();
	}
}
