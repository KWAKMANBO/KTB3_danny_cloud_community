package com.ktb.community.redis;

import com.ktb.community.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisSingleDataServiceImpl implements RedisSingleDataService {
    private final RedisHandler redisHandler;
    private final RedisConfig redisConfig;

    /*
     * Redis 단일 데이터 값을 등록 및 수정
     * 성공하면 1을 반환, 실패하면 0을 반환
     * */
    @Override
    public int setSingleData(String key, Object value) {
        return redisHandler.executeOperation(() -> redisHandler.getValueOperations().set(key, value));
    }

    /*
     * Redis 단일 데이터 값을 등록 및 수정
     * 성공하면 1을 반환, 실패하면 0을 반환
     * duration이 있다면 메모리상의 유효시간을 의미
     * */
    @Override
    public int setSingleData(String key, Object value, Duration duration) {
        return this.redisHandler.executeOperation(() -> redisHandler.getValueOperations().set(key, value, duration));
    }

    /*
     * 데이터 값을 조회
     * */
    @Override
    public String getSingleData(String key) {
        if (redisHandler.getValueOperations().get(key) == null) return "";
        return String.valueOf(redisHandler.getValueOperations().get(key));
    }

    /*
     * 키를 기반으로 값을 삭제
     * */
    @Override
    public int deleteSingleData(String key) {
        return redisHandler.executeOperation(() -> redisConfig.redisTemplate().delete(key));
    }
}
