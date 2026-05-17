package io.zefio.core.common.base;

import lombok.Data;
import java.util.List;

/**
 * A wrapper class used to deserialize and hold a collection of plugin metadata configurations.
 */
@Data
public class PluginMetaList {
	private List<PluginMeta> modules;
}
