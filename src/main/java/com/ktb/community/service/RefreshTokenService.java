package com.ktb.community.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.community.dto.RefreshTokenDataDto;
import com.ktb.community.dto.response.ReIssueRefreshTokenDto;
import com.ktb.community.entity.User;
import com.ktb.community.exception.custom.InvalidRefreshTokenException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.redis.RedisSingleDataServiceImpl;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
public class RefreshTokenService {
    private final RedisSingleDataServiceImpl redis;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;


    public RefreshTokenService(RedisSingleDataServiceImpl redis, JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.redis = redis;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    public void saveRefreshToken(String token, User user, LocalDateTime expireAt) {
        saveRefreshTokenWithUserInfo(token, user.getId(), user.getEmail(), expireAt);
    }

    private void saveRefreshTokenWithUserInfo(String token, Long userId, String email, LocalDateTime expiration) {
        try {
            String key = "refresh_token:" + userId;
            long ttl = Duration.between(LocalDateTime.now(), expiration).getSeconds();

            RefreshTokenDataDto refreshTokenDataDto = new RefreshTokenDataDto(token, userId, email);

            String value = objectMapper.writeValueAsString(refreshTokenDataDto);
            redis.setSingleData(key, value, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            throw new InvalidRefreshTokenException("Not a valid token");
        }


    }


    public Boolean existByToken(String token) {
        var jws = jwtUtil.parse(token);
        if (jws == null) return false;

        long userId = Long.parseLong(jws.getBody().getSubject());

        String key = "refresh_token:" + userId;
        String value = redis.getSingleData(key);

        if (value == null || value.isEmpty()) return false;

        try {
            RefreshTokenDataDto refreshToken = objectMapper.readValue(value, RefreshTokenDataDto.class);
            return token.equals(refreshToken.getRefreshToken());
        } catch (JsonProcessingException e) {
            log.error("json process error in checking exist token");
            return false;
        }
    }

    public String reIssueAccessToken(String refreshToken) {
        if (!existByToken(refreshToken)) {
            throw new InvalidRefreshTokenException("Invalid or expired refresh Token");
        }

        RefreshTokenDataDto refreshTokenDataDto = getTokenData(refreshToken);
        if (refreshTokenDataDto == null) {
            throw new InvalidRefreshTokenException("Failed to retrieve refresh token data");
        }

        return jwtUtil.generateAccessToken(refreshTokenDataDto.getUserId(), refreshTokenDataDto.getEmail());
    }


    @Transactional
    public ReIssueRefreshTokenDto reIssueRefreshToken(String refreshToken) {
        // 존재하지 않는 다면 유효하지 않은 토큰
        if (!this.existByToken(refreshToken)) {
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }

        RefreshTokenDataDto refreshTokenDataDto = getTokenData(refreshToken);
        if (refreshTokenDataDto == null) {
            throw new InvalidRefreshTokenException("Failed to retrieve refresh token data");
        }

        String newAccessToken = jwtUtil.generateAccessToken(refreshTokenDataDto.getUserId(), refreshTokenDataDto.getEmail());
        String newRefreshToken = jwtUtil.generateRefreshToken(refreshTokenDataDto.getUserId());
        saveRefreshTokenWithUserInfo(newRefreshToken, refreshTokenDataDto.getUserId(), refreshTokenDataDto.getEmail(), jwtUtil.getExpirationFromToken(newRefreshToken));

        return new ReIssueRefreshTokenDto(newAccessToken, newRefreshToken);

    }

    private RefreshTokenDataDto getTokenData(String token) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        String key = "refresh_token:" + userId;
        String value = redis.getSingleData(key);
        if (value == null || value.isEmpty()) return null;

        try {
            return objectMapper.readValue(value, RefreshTokenDataDto.class);
        } catch (JsonProcessingException e) {
            log.error("json process error in checking exist token");
            return null;
        }
    }

    public int getRemainingSecond(String refreshToken) {
        LocalDateTime expirationAt = this.jwtUtil.getExpirationFromToken(refreshToken);
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(now, expirationAt);
        return (int) duration.getSeconds();
    }

    public void removeAllRefreshToken(Long userId) {
        String key = "refresh_token:" + userId;
        redis.deleteSingleData(key);
    }

}
