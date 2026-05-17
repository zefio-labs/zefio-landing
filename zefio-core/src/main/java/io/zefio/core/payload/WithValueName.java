package io.zefio.core.payload;

/**
 * A simple contract for objects that wrap or provide a specific value.
 */
public interface WithValueName {
	/** Returns the encapsulated value. */
	Object getValue();
}
