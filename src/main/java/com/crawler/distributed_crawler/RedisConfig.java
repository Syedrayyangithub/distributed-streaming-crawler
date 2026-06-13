package com.crawler.distributed_crawler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.embedded.RedisServer;

@Configuration
public class RedisConfig {

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() {
        try {
            // Force block initialization to spin up the process completely synchronous
            redisServer = new RedisServer(6379);
            redisServer.start();
            System.out.println("🍃 Internal In-Memory Redis Server started successfully on port 6379!");
        } catch (Exception e) {
            System.out.println("Redis server lifecycle warning: " + e.getMessage());
        }
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Explicitly defining the connection factory ensures it attaches safely to localhost
        return new LettuceConnectionFactory("localhost", 6379);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        
        return template;
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) {
            try {
                redisServer.stop();
                System.out.println("🍂 Internal In-Memory Redis Server shut down cleanly.");
            } catch (Exception e) {
                System.out.println("Error shutting down embedded Redis: " + e.getMessage());
            }
        }
    }
}