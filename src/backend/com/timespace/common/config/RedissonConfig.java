package com.timespace.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String addr = "redis://" + host + ":" + port;
        if (password != null && !password.isEmpty()) {
            config.useSingleServer()
                    .setAddress(addr)
                    .setPassword(password)
                    .setDatabase(database)
                    .setConnectionPoolSize(16)
                    .setConnectionMinimumIdleSize(4);
        } else {
            config.useSingleServer()
                    .setAddress(addr)
                    .setDatabase(database)
                    .setConnectionPoolSize(16)
                    .setConnectionMinimumIdleSize(4);
        }
        return Redisson.create(config);
    }
}
