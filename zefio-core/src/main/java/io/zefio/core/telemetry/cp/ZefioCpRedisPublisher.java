package io.zefio.core.telemetry.cp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;

/**
 * [New Architecture] Redis Publisher for Control Plane (CP).
 * Replaces the old WebSocket client to ensure complete decoupling and statelessness.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZefioCpRedisPublisher {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JedisPool jedisPool;

    /**
     * Serializes the Map data to JSON and publishes it to the Redis channel.
     * * @param message The telemetry data payload
     */
    public void sendMessage(Map<String, Object> message) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = mapper.writeValueAsString(message);
            jedis.publish("zefio:telemetry", json);
        } catch (Exception e) {
            log.warn("[CP-Agent] Failed to publish message: {}", e.getMessage());
        }
    }

    public void sendCommand(Map<String, Object> command) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = mapper.writeValueAsString(command);
            jedis.publish("zefio:command", json);
        } catch (Exception e) {
            log.error("[CP-Agent] Failed to publish command", e);
        }
    }
}
