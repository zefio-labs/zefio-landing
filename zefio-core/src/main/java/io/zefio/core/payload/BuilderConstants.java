package io.zefio.core.payload;

import io.zefio.core.payload.builder.FixedPayloadBuilder;
import io.zefio.core.payload.builder.JsonPayloadBuilder;
import io.zefio.core.payload.builder.XmlPayloadBuilder;

/**
 * Maps short telegram format identifiers to their respective PayloadBuilder implementations.
 * Used by the factory to dynamically instantiate builders based on flow configuration.
 */
public enum BuilderConstants implements WithClassName {

	Fixed(FixedPayloadBuilder.class.getName()),
	JSON(JsonPayloadBuilder.class.getName()),
	XML(XmlPayloadBuilder.class.getName());

	private final String className;

	BuilderConstants(String className) {
		this.className = className;
	}

	@Override
	public String getClassName() {
		return className;
	}

	@Override
	public String getDtoClassName() {
		return className;
	}
}
