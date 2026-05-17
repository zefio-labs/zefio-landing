package io.zefio.core.payload;

/**
 * Defines a contract for objects that hold class name metadata.
 * Primarily used by enums or constants to facilitate reflection-based instantiation.
 */
public interface WithClassName {

	/** Returns the full canonical name of the target class. */
	String getClassName();

	/** Returns the class name of the associated Data Transfer Object (DTO). */
	String getDtoClassName();
}
