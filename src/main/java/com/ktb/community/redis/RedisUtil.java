package com.ktb.community.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisUtil {
    private final Duration duration = Duration.ofDays(15);
    private final RedisSingleDataServiceImpl redis;
    private final ObjectMapper objectMapper;

    public RedisUtil(RedisSingleDataServiceImpl redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }
}
