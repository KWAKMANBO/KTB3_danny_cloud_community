package com.ktb.community.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.community.service.RedisSingleDataServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class SessionUtil {
    private final Duration duration = Duration.ofHours(1);
    private final RedisSingleDataServiceImpl redis;
    private final ObjectMapper objectMapper;

    @Autowired
    public SessionUtil(RedisSingleDataServiceImpl redis) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
    }



    public String createSession(Long userId, String nickname) {
        try {
            String uuid = String.valueOf(UUID.randomUUID());
            SessionData sessionData = new SessionData(userId, nickname);
            // Json 객체를 String으로
            String value = objectMapper.writeValueAsString(sessionData);

            int result = redis.setSingleData(uuid, value, duration);
            if (result == 0) {
                throw new RuntimeException("세션 생성 실패");
            }

            return uuid;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("세션 데이터 직렬화 실패", e);
        }
    }

    public SessionData getSession(String uuid) {
        try {
            String value = redis.getSingleData(uuid);

            if (value == null || value.isEmpty()) {
                return null;
            }

            return objectMapper.readValue(value, SessionData.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("세션 데이터 역직렬화 실패", e);
        }
    }

    public boolean removeSession(String uuid) {
        int result = redis.deleteSingleData(uuid);
        return result == 1;
    }

}
