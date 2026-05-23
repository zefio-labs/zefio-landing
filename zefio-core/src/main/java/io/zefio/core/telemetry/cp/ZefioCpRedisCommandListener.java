package io.zefio.core.telemetry.cp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.zefio.core.beans.FlowSettingsBean;
import io.zefio.core.config.ZefioProperties;
import io.zefio.core.config.flow.FlowSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.Map;

/**
 * Listener that subscribes to the Redis 'zefio:command' channel.
 * Receives hot-reload commands from the Seed DP and orchestrates the pipeline swap.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZefioCpRedisCommandListener implements InitializingBean, DisposableBean {

    private final ZefioProperties zefioProperties;
    private final JedisPool jedisPool;
    private final FlowSettingsBean flowSettingsBean; // 💡 The engine orchestrator for hot-swapping

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private Thread subscriberThread;
    private JedisPubSub pubSub;

    @Override
    public void afterPropertiesSet() {
        subscriberThread = new Thread(() -> {
            // 주입받은 jedisPool을 사용
            try (Jedis jedis = jedisPool.getResource()) {
                pubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        handleCommand(message);
                    }
                };
                log.info("[CP-Agent] Subscribed to Redis channel: zefio:command");
                jedis.subscribe(pubSub, "zefio:command");
            } catch (Exception e) {
                log.error("Redis Command Listener Failed", e);
            }
        }, "Zefio-Redis-Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    private void handleCommand(String jsonMessage) {
        try {
            // 1. Parse the incoming JSON command
            Map<String, Object> command = jsonMapper.readValue(jsonMessage, new TypeReference<Map<String, Object>>() {});

            // 2. Target Group filtering to ensure only relevant nodes process this command
            String targetGroup = (String) command.getOrDefault("targetGroup", "main");
            if (!targetGroup.equals(zefioProperties.getNode().getGroup())) {
                log.debug("[CP-Agent] Command ignored. Node group mismatch. (Expected: {}, Actual: {})",
                        targetGroup, zefioProperties.getNode().getGroup());
                return;
            }

            // 3. Process hot-reload action
            if ("hot-reload".equals(command.get("action"))) {
                Map<String, Object> payload = (Map<String, Object>) command.get("payload");
                String yamlStr = (String) payload.get("yaml");

                log.info("🔥 [CP-Agent] Hot-Reloading Pipeline on Node: {}", zefioProperties.getNode().getId());

                // 4. Convert YAML string to FlowSettings object and trigger hotSwap
                FlowSettings newSettings = yamlMapper.readValue(yamlStr, FlowSettings.class);
                flowSettingsBean.hotSwap(newSettings);

                log.info("✅ [CP-Agent] Pipeline hot-swap completed.");
            }
        } catch (Exception e) {
            log.error("[CP-Agent] Critical error during command execution", e);
        }
    }

    @Override
    public void destroy() {
        if (pubSub != null && pubSub.isSubscribed()) {
            pubSub.unsubscribe();
        }
        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }
        log.info("[CP-Agent] Redis Command Listener shut down.");
    }
}
