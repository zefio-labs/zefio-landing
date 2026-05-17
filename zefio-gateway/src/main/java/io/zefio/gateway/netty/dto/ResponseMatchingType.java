package io.zefio.gateway.netty.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Strategy for matching responses to requests in the Netty pipeline.
 */
public enum ResponseMatchingType {
    /** One-way communication (No response expected): Handled by FireAndForgetTxnManager */
    NONE,

    /** Session/Channel based matching: Handled by SessionTxnManager */
    SESSION,

    /** Key-based matching using specific fields within the message body: Handled by TelegramTxnManager */
    TELEGRAM;

    @JsonCreator
    public static ResponseMatchingType fromString(String value) {
        if (value == null) return TELEGRAM;
        try {
            return ResponseMatchingType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TELEGRAM;
        }
    }
}
