package com.ktb.community.handler;

import com.ktb.community.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisHandler {
    private static final Logger log = LoggerFactory.getLogger(RedisHandler.class);
    private final RedisConfig redisConfig;

    public ListOperations<String, Object> getListOperations() {
        return redisConfig.redisTemplate().opsForList();
    }

    public ValueOperations<String, Object> getValueOperations() {
        return redisConfig.redisTemplate().opsForValue();
    }

    public int executeOperation(Runnable operation) {
        try {
            operation.run();
            return 1;
        } catch (Exception e) {
            log.error(String.valueOf(e));
            return 0;
        }
    }
}
