package com.ktb.community.redis;

import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public interface RedisSingleDataService {
    int setSingleData(String key, Object value);

    int setSingleData(String key, Object value, Duration duration);

    String getSingleData(String key);

    int deleteSingleData(String key);
}
