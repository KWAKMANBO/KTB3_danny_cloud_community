package com.ktb.community.controller;

import com.ktb.community.dto.response.ApiResponseDto;
import com.ktb.community.redis.RedisSingleDataServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {
    private final RedisSingleDataServiceImpl redis;

    public HealthController(RedisSingleDataServiceImpl redis) {
        this.redis = redis;
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponseDto.success("OK"));
    }

    @GetMapping("/redis")
    public ResponseEntity<ApiResponseDto<String>> redisCheck() {
        return ResponseEntity.ok(ApiResponseDto.success(redis.checkRedisConnection()));
    }
}
