package com.ktb.community.redis;

import com.ktb.community.config.RedisConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisSingleDataServiceImpl implements RedisSingleDataService {
    private final RedisHandler redisHandler;
    private final RedisConfig redisConfig;

    @Autowired
    public RedisSingleDataServiceImpl(RedisHandler redisHandler, RedisConfig redisConfig) {
        this.redisHandler = redisHandler;
        this.redisConfig = redisConfig;
    }

    @Override
    public int setSingleData(String key, Object value) {
        return redisHandler.executeOperation(() -> redisHandler.getValueOperations().set(key, value));
    }

    @Override
    public int setSingleData(String key, Object value, Duration duration) {
        return redisHandler.executeOperation(() -> redisHandler.getValueOperations().set(key, value, duration));
    }

    @Override
    public String getSingleData(String key) {
        if (redisHandler.getValueOperations().get(key) == null) return "";

        return String.valueOf(redisHandler.getValueOperations().get(key));
    }

    @Override
    public int deleteSingleData(String key) {
        return redisHandler.executeOperation(() -> redisConfig.redisTemplate().delete(key));
    }

    public String checkRedisConnection() {
        try {
            System.out.println("hello");
            String testKey = "connection_test";
            String testValue = "OK";

            redisConfig.redisTemplate().opsForValue().set(testKey, testValue);
            Object result = redisConfig.redisTemplate().opsForValue().get(testKey);
            redisConfig.redisTemplate().delete(testKey);

            if (testValue.equals(String.valueOf(result))) {
                return "Redis connection successful: " + result;
            } else {
                return "Redis connection issue: expected " + testValue + " but got " + result;
            }
        } catch (Exception e) {
            return "Redis connection failed: " + e.getMessage();
        }
    }

}
