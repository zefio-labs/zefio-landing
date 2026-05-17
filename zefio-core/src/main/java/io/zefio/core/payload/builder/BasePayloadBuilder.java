package io.zefio.core.payload.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.ApplicationAttributes;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.core.payload.builder.config.*;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
import io.zefio.core.payload.util.BytesUtils;
import io.zefio.jdk.JMSWrapper;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.UUID;

/**
 * Abstract base class for payload construction and finalization.
 * It provides common logic for extracting Transaction IDs (Correlation IDs)
 * and handles final byte manipulation such as encoding conversion and
 * protocol framing (Length-header or Delimiter-based) before sending to an upstream target.
 */
public abstract class BasePayloadBuilder implements PayloadBuilder {

	protected final Logger log = LoggerFactory.getLogger(getClass());
	protected final ObjectMapper mapper = new ObjectMapper();
	protected final Telegram telegram;

	public BasePayloadBuilder(Telegram telegram){
		this.telegram = telegram;
	}

	@Override
	public Payload withBody(Object original, Charset encoding) throws FlowException {
		byte[] datas = null;
		try {
			datas = preWithBody(original, encoding);
		} catch(Exception e){
			// Preserve original FlowException; translate others to FORMAT_ERROR
			if (e instanceof FlowException) throw (FlowException) e;
			throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
		}
		Payload payload = new ZefioMessage(datas, encoding);
		if (this.telegram != null) {
			payload.setTelegramName(this.telegram.getName());
		}

		if (StringUtils.isNotBlank(payload.getTrxID())) {
			return payload;
		}

		// Transaction ID (TrxID) extraction logic
		CorrelationField corr = this.telegram.getValues().getCorrelation();
		if (corr.getType() == CorrelationIdType.SpEL) {
			String trxID = PayloadExpressionEvaluator.evaluate(corr.getExpression(), payload, String.class);

			// Strict validation: Error out if SpEL extraction results in an empty value
			if (ObjectUtils.isEmpty(trxID)) {
				log.error("SpEL Correlation [{}] evaluated to null or empty", corr.getExpression());
				throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Required SpEL Correlation not found");
			}
			payload.setTrxID(trxID.trim());
		} else {
			try {
				// Maintain backward compatibility for JsonPath, Offset, etc.
				payload.setTrxID(extractCorrelationId(original, payload.getBody(), encoding));
			} catch(Exception e){
				if (e instanceof FlowException) throw (FlowException) e;
				throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
			}
		}
		return payload;
	}

	private byte[] preWithBody(Object original, Charset encoding) throws Exception {
		byte[] datas = JMSWrapper.getJMSMessage(original, encoding);
		if( ObjectUtils.isEmpty(datas) ) {
			if( original instanceof org.springframework.amqp.core.Message ) {
				org.springframework.amqp.core.Message amqpMessage = (org.springframework.amqp.core.Message) original;
				datas = amqpMessage.getBody();
			}
			else if( original instanceof byte[] ) {
				datas = (byte[]) original;
			}
			else if( original instanceof String && !ObjectUtils.isEmpty(encoding) ) {
				datas = ((String) original).getBytes(encoding);
			}
			else {
				datas = mapper.writeValueAsBytes(original);
			}
		}
		return datas;
	};

	public String extractCorrelationIdByJms(Object original) {
		String trxID = "";
		switch (this.telegram.getValues().getCorrelation().getType()) {
			case JMSMessageID:
			case JMSCorrelationID:
				try {
					if (telegram.getValues().getCorrelation().getType() == CorrelationIdType.JMSMessageID)
						trxID = JMSWrapper.getJMSMessageID(original);
					if (telegram.getValues().getCorrelation().getType() == CorrelationIdType.JMSCorrelationID)
						trxID = JMSWrapper.getJMSCorrelationID(original);
				} catch(Exception e){
					log.warn("Failed to get trxID from JMSMessage. Falling back to UUID. Reason: {}", e.getMessage());
					trxID = UUID.randomUUID().toString().replace("-", "");
				}
				break;
			default:
				trxID = UUID.randomUUID().toString().replace("-", "");
				break;
		}
		return trxID;
	}

	@Override
	public Telegram getTelegram() {
		return this.telegram;
	}

	/**
	 * Finalizes the payload before it is sent to an upstream system.
	 * This method handles encoding conversion and protocol framing (e.g., adding length headers).
	 */
	@Override
	public Payload finalizeUpstreamPayload(Payload payload, Charset target) throws FlowException {
		try {
			// 1. Adjust character encoding
			if (!this.telegram.getValues().getEncodingIgnore()) {
				BytesUtils.changeEncoding(payload, target);
			}

			// 2. Accessing getBody() here ensures ZefioMessage processes the final encoding
			byte[] currentBody = payload.getBody();

			// 3. Process Length-based Framing
			FramingField framing = this.telegram.getValues().getFraming();
			if (framing != null && framing.getType() == FramingType.Length) {

				// Priority 1: Use settings defined in the configuration file
				boolean doLengthUpdate = Boolean.TRUE.equals(framing.getLengthDataUpdate());

				// Priority 2: Use dynamic hint from headers (e.g., set by custom filters) if present
				if (payload.containsKeyHeader(ApplicationAttributes.DYNAMIC_LENGTH_UPDATE)) {
					Object dynamicHint = payload.getHeader(ApplicationAttributes.DYNAMIC_LENGTH_UPDATE);
					if (dynamicHint instanceof Boolean) {
						doLengthUpdate = (Boolean) dynamicHint;
					} else if (dynamicHint instanceof String) {
						doLengthUpdate = Boolean.parseBoolean((String) dynamicHint);
					}
					log.debug("DYNAMIC_LENGTH_UPDATE hint detected: [{}]", doLengthUpdate);
				}

				if (doLengthUpdate && ObjectUtils.allNotNull(framing.getLengthDataSize(), framing.getLengthDataInclude())) {
					byte[] updatedBody = BytesUtils.updateLength(
							payload.getBody(),
							framing.getLengthDataSize(),
							framing.getLengthDataInclude()
					);
					payload.setBody(updatedBody);
					if (log.isDebugEnabled()) {
						log.debug("Length header updated. Total packet size: [{}]", updatedBody.length);
					}
				}
			}
			// 4. Process Delimiter-based Framing (Common for TCP XML/JSON)
			else if (framing != null && framing.getType() == FramingType.Delimiter && framing.getDelimiter() != null) {
				byte[] delimiterBytes = framing.getDelimiter().getBytes(target);

				// Append delimiter only if it is not already present at the end of the body
				boolean endsWith = currentBody.length >= delimiterBytes.length &&
						java.util.Arrays.equals(
								java.util.Arrays.copyOfRange(currentBody, currentBody.length - delimiterBytes.length, currentBody.length),
								delimiterBytes
						);

				if (!endsWith) {
					byte[] newBody = new byte[currentBody.length + delimiterBytes.length];
					System.arraycopy(currentBody, 0, newBody, 0, currentBody.length);
					System.arraycopy(delimiterBytes, 0, newBody, currentBody.length, delimiterBytes.length);
					payload.setBody(newBody);
				}
			}
		} catch (Exception e) {
			if (e instanceof FlowException) throw (FlowException) e;
			log.error("Error during upstream payload finalization", e);
			throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
		}
		return payload;
	}
}
