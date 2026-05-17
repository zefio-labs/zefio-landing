package io.zefio.core.payload.builder.config;

import io.zefio.core.payload.BuilderConstants;
import io.zefio.core.payload.PayloadBuilder;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.InvocationTargetException;

/**
 * Represents the definition of a telegram, including its name, format type, and associated values.
 * It provides a Builder that dynamically instantiates the appropriate PayloadBuilder implementation
 * using reflection based on the telegram's type or a custom class name.
 */
@Setter
@Getter
public class Telegram {
	private String name;
	private Type type;
	private String clazz;
	private TelegramValues values;

	public enum Type {
		Fixed, JSON, XML, OBJECT
	}

	public static class Builder {
		private String name;
		private Type type;
		private TelegramValues values;

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder type(Type type) {
			this.type = type;
			return this;
		}

		public Builder values(TelegramValues values) {
			this.values = values;
			return this;
		}

		/**
		 * Instantiates a PayloadBuilder.
		 * It first attempts to resolve the builder using pre-defined constants based on the Type enum.
		 * If that fails, it falls back to instantiating via a explicitly provided class name.
		 */
		@SuppressWarnings({ "unchecked" })
		public PayloadBuilder build() throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
			Telegram telegram = new Telegram(name, type, values);
			try {
				// Attempt to load implementation via Type-to-ClassName mapping
				Class<PayloadBuilder> builderClazz = (Class<PayloadBuilder>) Class.forName(BuilderConstants.valueOf(telegram.getType().name()).getClassName());
				return builderClazz.getConstructor(Telegram.class).newInstance(telegram);
			} catch (NullPointerException | ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
					 InvocationTargetException ignored) {
				try {
					// Fallback: load via custom clazz string
					Class<PayloadBuilder> builderClazz = (Class<PayloadBuilder>) Class.forName(telegram.getClazz());
					return builderClazz.getConstructor(Telegram.class).newInstance(telegram);
				} catch (NullPointerException | ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
						 InvocationTargetException e) {
					throw e;
				}
			}
		}
	}

	private Telegram(String name, Type type, TelegramValues values) {
		this.name = name;
		this.type = type;
		this.values = values;
	}
}
