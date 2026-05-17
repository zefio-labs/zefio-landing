package io.zefio.core.common.base;

import lombok.Data;

/**
 * Represents the metadata and configuration details of a specific plugin or module within the system.
 */
@Data
public class PluginMeta {
	private String name;
	private PluginType type;
	private String className;
	private String dtoClassName;
}
