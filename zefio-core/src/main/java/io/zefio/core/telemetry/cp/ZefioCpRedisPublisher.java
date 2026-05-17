package io.zefio.core.telemetry.cp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.config.ZefioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.net.URI;
import java.util.Map;

/**
 * [New Architecture] Redis Publisher for Control Plane (CP).
 * Replaces the old WebSocket client to ensure complete decoupling and statelessness.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZefioCpRedisPublisher implements InitializingBean, DisposableBean {

    private final ZefioProperties zefioProperties;
    private final ObjectMapper mapper = new ObjectMapper();
    private JedisPool jedisPool;

    // The Redis channel name subscribed by the Control Plane (CP)
    private static final String TELEMETRY_CHANNEL = "zefio:telemetry";

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!zefioProperties.getCp().isEnabled()) {
            log.info("[CP-Agent] Control Plane telemetry is disabled.");
            return;
        }

        // Retrieves the Redis URL from the configuration (e.g., redis://localhost:6379/0)
        String redisUrlStr = zefioProperties.getCp().getRedis().getUrl();
        log.info("[CP-Agent] Initializing Redis connection to: {}", redisUrlStr);

        try {
            URI redisUri = new URI(redisUrlStr);
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(8); // Lightweight configuration for CP telemetry
            poolConfig.setMaxIdle(8);
            poolConfig.setMinIdle(1);

            this.jedisPool = new JedisPool(poolConfig, redisUri);

            // Connection test (Ping)
            try (Jedis jedis = jedisPool.getResource()) {
                if ("PONG".equals(jedis.ping())) {
                    log.info("[CP-Agent] Successfully connected to Redis Hub.");
                }
            }
        } catch (Exception e) {
            log.error("[CP-Agent] Failed to initialize Redis Pool. URL: {}", redisUrlStr, e);
        }
    }

    /**
     * Serializes the Map data to JSON and publishes it to the Redis channel.
     * * @param message The telemetry data payload
     */
    public void sendMessage(Map<String, Object> message) {
        if (jedisPool == null || jedisPool.isClosed()) return;

        try (Jedis jedis = jedisPool.getResource()) {
            String json = mapper.writeValueAsString(message);
            jedis.publish(TELEMETRY_CHANNEL, json);
        } catch (Exception e) {
            log.warn("[CP-Agent] Failed to publish message to Redis: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() throws Exception {
        if (this.jedisPool != null && !this.jedisPool.isClosed()) {
            this.jedisPool.close();
            log.info("[CP-Agent] Redis Pool closed gracefully.");
        }
    }
}
