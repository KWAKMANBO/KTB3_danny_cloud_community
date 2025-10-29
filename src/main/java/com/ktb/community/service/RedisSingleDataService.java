package com.ktb.community.service;

import org.springframework.stereotype.Service;

import java.time.Duration;

// Redis 단일 데이터를 처리하는 비즈니스 로직 인터페이스
@Service
public interface RedisSingleDataService {
    // Redis 단일 데잍 겂을 등록
    int setSingleData(String key, Object value);

    // Redis 단일 데이터 값을 등록 및 수정
    int setSingleData(String key, Object value, Duration duration);

    // Redis 단일 데이터를 조회
    String getSingleData(String key);

    // Redis키를 기반으로 단일 데이터의 값을 삭제
    int deleteSingleData(String key);
}
