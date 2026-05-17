package io.zefio.core;

import io.zefio.core.common.util.CommonUtils;
import io.zefio.core.factory.PluginContext;
import org.apache.commons.lang3.ObjectUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Abstract class for event-driven Ingress modules (e.g., Kafka, RabbitMQ).
 * Manages a dedicated thread pool for message consumers.
 */
public abstract class EventDrivenIngress extends ReactiveIngress {

	protected ExecutorService executor;
	protected int numOfConsumer = 1;

	public EventDrivenIngress(PluginContext context) {
		super(context);
	}

	@Override
	public void initialise() throws Exception {
		// Set up a dedicated thread pool for the Ingress consumers
		this.executor = Executors.newFixedThreadPool(numOfConsumer,
				CommonUtils.getThreadFactory(this.flowName + "-" + this.pluginName)
		);
		super.initialise();
	}

	@Override
	protected void doStart() throws Exception {
		// Delegate actual listener startup to child classes
		startListening();
	}

	protected abstract void startListening() throws Exception;

	@Override
	public void close() {
		if(ObjectUtils.isNotEmpty(this.executor)) {
			this.executor.shutdown();
			this.executor = null;
		}
		super.close();
	}
}
