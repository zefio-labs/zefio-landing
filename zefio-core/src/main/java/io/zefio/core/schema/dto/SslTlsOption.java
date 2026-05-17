package io.zefio.core.schema.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import lombok.Data;

/**
 * Data Transfer Object representing generic SSL/TLS configuration options.
 * It encapsulates settings for keystore/truststore configurations, PEM certificates,
 * and specific middleware options.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class SslTlsOption {

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Whether to use SSL. If true, sets JoltSessionAttributes.SECURE=Y",
            nullable = true, example = "false", defaultValue = "false")
    protected Boolean enable = false;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "List of allowed cipher suites, separated by commas (e.g., TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA)",
            nullable = true,
            example = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA")
    private String ciphers;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Whether client authentication is required (mTLS support). If true, the client must authenticate with a certificate (MQ/HTTP only)",
            nullable = true, example = "false", defaultValue = "false")
    private Boolean clientAuth = false;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "Protocol types. Can register multiple like SSL, SSLv3, TLSv1.2, TLSv1.3. Https, Tcp, Websocket support TLS only",
            nullable = true, example = "SSL / SSLv3 / TLSv1.2 / TLSv1.3")
    protected String protocol;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "SSL Key Store type",
            nullable = true, example = "JKS / PKCS12")
    protected String keyStoreType;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "SSL Key Store file path",
            nullable = true, example = "/path/to/keystore.p12")
    protected String keyStore;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "SSL Key Store password",
            nullable = true, example = "password")
    protected String keyPassword;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "SSL Trust Store type",
            nullable = true, example = "JKS / PKCS12")
    protected String trustStoreType;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "SSL Trust Store file path",
            nullable = true, example = "/path/to/truststore.p12")
    protected String trustStore;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "SSL Trust Store password",
            nullable = true, example = "password")
    protected String trustPassword;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "PEM Certificate file path",
            nullable = true, example = "/etc/ssl/client-cert.pem")
    protected String certFilePath;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "PEM Private Key file path",
            nullable = true, example = "/etc/ssl/client-key.pem")
    protected String privateKeyFilePath;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Mainly used in middleware, SSL Flip configuration (MQ specific option)",
            nullable = true, example = "false", defaultValue = "false")
    protected Boolean flipRequired = false;
}
