package com.ktb.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.community.dto.RefreshTokenDataDto;
import com.ktb.community.dto.response.ReIssueRefreshTokenDto;
import com.ktb.community.entity.User;
import com.ktb.community.exception.custom.InvalidRefreshTokenException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.redis.RedisSingleDataServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.impl.DefaultClaims;
import io.jsonwebtoken.impl.DefaultJws;
import io.jsonwebtoken.impl.DefaultJwsHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RefreshTokenService 테스트")
public class RefreshTokenServiceTest {

    @Mock
    private RedisSingleDataServiceImpl redis;
    @Mock
    private JwtUtil jwtUtil;
    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Nested
    @DisplayName("Refresh Token 저장 테스트")
    class SaveRefreshTokenTest {

        @Test
        @DisplayName("Refresh Token 저장 성공")
        void saveRefreshToken_Success() throws Exception {
            // given
            String token = "test.refresh.token";
            User user = createUser(1L, "test@example.com", "user1");
            LocalDateTime expireAt = LocalDateTime.now().plusDays(1);

            when(redis.setSingleData(anyString(), anyString(), any(Duration.class))).thenReturn(1);

            // when
            refreshTokenService.saveRefreshToken(token, user, expireAt);

            // then
            verify(redis).setSingleData(
                    eq("refresh_token:" + user.getId()),
                    anyString(),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("Refresh Token 저장 시 Redis에 올바른 데이터 저장")
        void saveRefreshToken_SavesCorrectData() throws Exception {
            // given
            String token = "test.refresh.token";
            User user = createUser(1L, "test@example.com", "user1");
            LocalDateTime expireAt = LocalDateTime.now().plusDays(1);

            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            when(redis.setSingleData(anyString(), anyString(), any(Duration.class))).thenReturn(1);

            // when
            refreshTokenService.saveRefreshToken(token, user, expireAt);

            // then
            verify(redis).setSingleData(
                    eq("refresh_token:1"),
                    valueCaptor.capture(),
                    any(Duration.class)
            );

            RefreshTokenDataDto dto = objectMapper.readValue(valueCaptor.getValue(), RefreshTokenDataDto.class);
            assertThat(dto.getRefreshToken()).isEqualTo(token);
            assertThat(dto.getUserId()).isEqualTo(user.getId());
            assertThat(dto.getEmail()).isEqualTo(user.getEmail());
        }
    }

    @Nested
    @DisplayName("Refresh Token 존재 여부 확인 테스트")
    class ExistByTokenTest {

        @Test
        @DisplayName("유효한 Refresh Token 존재 확인 성공")
        void existByToken_ValidToken_ReturnsTrue() throws Exception {
            // given
            String token = "test.refresh.token";
            Long userId = 1L;
            String email = "test@example.com";

            Jws<Claims> jws = createJws(userId);
            RefreshTokenDataDto tokenData = new RefreshTokenDataDto(token, userId, email);
            String jsonData = objectMapper.writeValueAsString(tokenData);

            when(jwtUtil.parse(token)).thenReturn(jws);
            when(redis.getSingleData("refresh_token:" + userId)).thenReturn(jsonData);

            // when
            Boolean result = refreshTokenService.existByToken(token);

            // then
            assertThat(result).isTrue();
            verify(jwtUtil).parse(token);
            verify(redis).getSingleData("refresh_token:" + userId);
        }

        @Test
        @DisplayName("토큰이 일치하지 않으면 false 반환")
        void existByToken_DifferentToken_ReturnsFalse() throws Exception {
            // given
            String token = "test.refresh.token";
            String differentToken = "different.refresh.token";
            Long userId = 1L;
            String email = "test@example.com";

            Jws<Claims> jws = createJws(userId);
            RefreshTokenDataDto tokenData = new RefreshTokenDataDto(differentToken, userId, email);
            String jsonData = objectMapper.writeValueAsString(tokenData);

            when(jwtUtil.parse(token)).thenReturn(jws);
            when(redis.getSingleData("refresh_token:" + userId)).thenReturn(jsonData);

            // when
            Boolean result = refreshTokenService.existByToken(token);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Redis에 데이터가 없으면 false 반환")
        void existByToken_NoRedisData_ReturnsFalse() {
            // given
            String token = "test.refresh.token";
            Long userId = 1L;

            Jws<Claims> jws = createJws(userId);

            when(jwtUtil.parse(token)).thenReturn(jws);
            when(redis.getSingleData("refresh_token:" + userId)).thenReturn("");

            // when
            Boolean result = refreshTokenService.existByToken(token);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("JWT 파싱 실패 시 false 반환")
        void existByToken_InvalidJwt_ReturnsFalse() {
            // given
            String token = "invalid.token";

            when(jwtUtil.parse(token)).thenReturn(null);

            // when
            Boolean result = refreshTokenService.existByToken(token);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Access Token 재발급 테스트")
    class ReIssueAccessTokenTest {

        @Test
        @DisplayName("Access Token 재발급 성공")
        void reIssueAccessToken_Success() throws Exception {
            // given
            String refreshToken = "test.refresh.token";
            Long userId = 1L;
            String email = "test@example.com";
            String newAccessToken = "new.access.token";

            Jws<Claims> jws = createJws(userId);
            RefreshTokenDataDto tokenData = new RefreshTokenDataDto(refreshToken, userId, email);
            String jsonData = objectMapper.writeValueAsString(tokenData);

            when(jwtUtil.parse(refreshToken)).thenReturn(jws);
            when(jwtUtil.getUserIdFromToken(refreshToken)).thenReturn(userId);
            when(redis.getSingleData("refresh_token:" + userId)).thenReturn(jsonData);
            when(jwtUtil.generateAccessToken(userId, email)).thenReturn(newAccessToken);

            // when
            String result = refreshTokenService.reIssueAccessToken(refreshToken);

            // then
            assertThat(result).isEqualTo(newAccessToken);
            verify(jwtUtil).generateAccessToken(userId, email);
        }

        @Test
        @DisplayName("유효하지 않은 Refresh Token으로 재발급 시 예외 발생")
        void reIssueAccessToken_InvalidToken_ThrowsException() {
            // given
            String refreshToken = "invalid.refresh.token";
            Long userId = 1L;

            Jws<Claims> jws = createJws(userId);

            when(jwtUtil.parse(refreshToken)).thenReturn(jws);
            when(redis.getSingleData("refresh_token:" + userId)).thenReturn("");

            // when & then
            assertThatThrownBy(() -> refreshTokenService.reIssueAccessToken(refreshToken))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessage("Invalid or expired refresh Token");
        }

        @Test
        @DisplayName("토큰 데이터 조회 실패 시 예외 발생")
        void reIssueAccessToken_FailToRetrieveData_ThrowsException() throws Exception {
            // given
            String refreshToken = "test.refresh.token";
            Long userId = 1L;

            Jws<Claims> jws = createJws(userId);
            RefreshTokenDataDto tokenData = new RefreshTokenDataDto(refreshToken, userId, "test@example.com");
            String jsonData = objectMapper.writeValueAsString(tokenData);

            when(jwtUtil.parse(refreshToken)).thenReturn(jws);
            when(jwtUtil.getUserIdFromToken(refreshToken)).thenReturn(userId);
            when(redis.getSingleData("refresh_token:" + userId))
                    .thenReturn(jsonData)  // existByToken에서 true 반환을 위해
                    .thenReturn("");       // getTokenData에서 null 반환을 위해

            // when & then
            assertThatThrownBy(() -> refreshTokenService.reIssueAccessToken(refreshToken))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessage("Failed to retrieve refresh token data");
        }
    }

    @Nested
    @DisplayName("Refresh Token 재발급 테스트")
    class ReIssueRefreshTokenTest {

        @Test
        @DisplayName("Access Token과 Refresh Token 모두 재발급 성공")
        void reIssueRefreshToken_Success() throws Exception {
            // given
            String oldRefreshToken = "old.refresh.token";
            Long userId = 1L;
            String email = "test@example.com";
            String newAccessToken = "new.access.token";
            String newRefreshToken = "new.refresh.token";
            LocalDateTime expiration = LocalDateTime.now().plusDays(1);

            Jws<Claims> jws = createJws(userId);
            RefreshTokenDataDto tokenData = new RefreshTokenDataDto(oldRefreshToken, userId, email);
            String jsonData = objectMapper.writeValueAsString(tokenData);

            when(jwtUtil.parse(oldRefreshToken)).thenReturn(jws);
            when(jwtUtil.getUserIdFromToken(oldRefreshToken)).thenReturn(userId);
            when(redis.getSingleData("refresh_token:" + userId)).thenReturn(jsonData);
            when(jwtUtil.generateAccessToken(userId, email)).thenReturn(newAccessToken);
            when(jwtUtil.generateRefreshToken(userId)).thenReturn(newRefreshToken);
            when(jwtUtil.getExpirationFromToken(newRefreshToken)).thenReturn(expiration);
            when(redis.setSingleData(anyString(), anyString(), any(Duration.class))).thenReturn(1);

            // when
            ReIssueRefreshTokenDto result = refreshTokenService.reIssueRefreshToken(oldRefreshToken);

            // then
            assertThat(result.getAccessToken()).isEqualTo(newAccessToken);
            assertThat(result.getRefreshToken()).isEqualTo(newRefreshToken);
            verify(jwtUtil).generateAccessToken(userId, email);
            verify(jwtUtil).generateRefreshToken(userId);
            verify(redis).setSingleData(
                    eq("refresh_token:" + userId),
                    anyString(),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("유효하지 않은 Refresh Token으로 재발급 시 예외 발생")
        void reIssueRefreshToken_InvalidToken_ThrowsException() {
            // given
            String refreshToken = "invalid.refresh.token";
            Long userId = 1L;

            Jws<Claims> jws = createJws(userId);

            when(jwtUtil.parse(refreshToken)).thenReturn(jws);
            when(redis.getSingleData("refresh_token:" + userId)).thenReturn("");

            // when & then
            assertThatThrownBy(() -> refreshTokenService.reIssueRefreshToken(refreshToken))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessage("Invalid refresh token");
        }
    }

    @Nested
    @DisplayName("Refresh Token 남은 시간 조회 테스트")
    class GetRemainingSecondTest {

        @Test
        @DisplayName("Refresh Token 남은 시간 조회 성공")
        void getRemainingSecond_Success() {
            // given
            String refreshToken = "test.refresh.token";
            LocalDateTime expiration = LocalDateTime.now().plusHours(1);

            when(jwtUtil.getExpirationFromToken(refreshToken)).thenReturn(expiration);

            // when
            int remainingSeconds = refreshTokenService.getRemainingSecond(refreshToken);

            // then
            assertThat(remainingSeconds).isGreaterThan(3500); // 약 1시간 = 3600초
            assertThat(remainingSeconds).isLessThan(3700);
            verify(jwtUtil).getExpirationFromToken(refreshToken);
        }

        @Test
        @DisplayName("만료된 토큰의 남은 시간은 음수")
        void getRemainingSecond_ExpiredToken_NegativeValue() {
            // given
            String refreshToken = "expired.refresh.token";
            LocalDateTime expiration = LocalDateTime.now().minusHours(1);

            when(jwtUtil.getExpirationFromToken(refreshToken)).thenReturn(expiration);

            // when
            int remainingSeconds = refreshTokenService.getRemainingSecond(refreshToken);

            // then
            assertThat(remainingSeconds).isLessThan(0);
        }
    }

    @Nested
    @DisplayName("Refresh Token 삭제 테스트")
    class RemoveAllRefreshTokenTest {

        @Test
        @DisplayName("사용자의 모든 Refresh Token 삭제 성공")
        void removeAllRefreshToken_Success() {
            // given
            Long userId = 1L;

            when(redis.deleteSingleData("refresh_token:" + userId)).thenReturn(1);

            // when
            refreshTokenService.removeAllRefreshToken(userId);

            // then
            verify(redis).deleteSingleData("refresh_token:" + userId);
        }

        @Test
        @DisplayName("존재하지 않는 사용자의 Refresh Token 삭제 시도")
        void removeAllRefreshToken_NonExistentUser() {
            // given
            Long userId = 999L;

            when(redis.deleteSingleData("refresh_token:" + userId)).thenReturn(0);

            // when
            refreshTokenService.removeAllRefreshToken(userId);

            // then
            verify(redis).deleteSingleData("refresh_token:" + userId);
        }
    }

    // Helper methods
    private User createUser(Long id, String email, String nickname) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setNickname(nickname);
        return user;
    }

    private Jws<Claims> createJws(Long userId) {
        Claims claims = new DefaultClaims();
        claims.setSubject(String.valueOf(userId));

        DefaultJwsHeader header = new DefaultJwsHeader();
        return new DefaultJws<>(header, claims, "signature");
    }
}
