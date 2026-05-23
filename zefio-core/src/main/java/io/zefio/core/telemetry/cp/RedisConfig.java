package io.zefio.core.telemetry.cp;

import io.zefio.core.config.ZefioProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.net.URI;

@Slf4j
@Configuration
public class RedisConfig {

    @Bean
    public JedisPool jedisPool(ZefioProperties zefioProperties) {
        String redisUrlStr = zefioProperties.getCp().getRedis().getUrl();
        log.info("[RedisConfig] Initializing global JedisPool: {}", redisUrlStr);

        try {
            URI redisUri = new URI(redisUrlStr);
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(16);
            poolConfig.setMaxIdle(8);
            poolConfig.setMinIdle(2);

            return new JedisPool(poolConfig, redisUri);
        } catch (Exception e) {
            log.error("[RedisConfig] Failed to initialize JedisPool", e);
            throw new RuntimeException("Redis connection failed", e);
        }
    }
}
