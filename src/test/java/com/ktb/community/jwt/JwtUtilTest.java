package com.ktb.community.jwt;

import com.ktb.community.exception.custom.InvalidRefreshTokenException;
import com.ktb.community.repository.RefreshRepository;
import com.ktb.community.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.Key;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtUtil 테스트")
public class JwtUtilTest {

    private JwtUtil jwtUtil;
    private String secretKey;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;

    @Mock
    private RefreshRepository refreshRepository;
    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 테스트용 비밀키 생성 (256비트 = 32바이트)
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            keyBytes[i] = (byte) i;
        }
        secretKey = Base64.getEncoder().encodeToString(keyBytes);

        // 만료 시간 설정
        accessTokenExpiration = 3600000L; // 1시간
        refreshTokenExpiration = 86400000L; // 24시간

        jwtUtil = new JwtUtil(secretKey, accessTokenExpiration, refreshTokenExpiration, refreshRepository, userRepository);
    }

    @Nested
    @DisplayName("Access Token 생성 테스트")
    class GenerateAccessTokenTest {

        @Test
        @DisplayName("Access Token 생성 성공")
        void generateAccessToken_Success() {
            // given
            Long userId = 1L;
            String email = "test@example.com";

            // when
            String token = jwtUtil.generateAccessToken(userId, email);

            // then
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();
            assertThat(token.split("\\.")).hasSize(3); // JWT는 헤더.페이로드.서명 3부분으로 구성
        }

        @Test
        @DisplayName("생성된 Access Token에서 userId 추출 성공")
        void generateAccessToken_ExtractUserId_Success() {
            // given
            Long userId = 1L;
            String email = "test@example.com";

            // when
            String token = jwtUtil.generateAccessToken(userId, email);
            Long extractedUserId = jwtUtil.getUserIdFromToken(token);

            // then
            assertThat(extractedUserId).isEqualTo(userId);
        }

        @Test
        @DisplayName("생성된 Access Token에서 email 추출 성공")
        void generateAccessToken_ExtractEmail_Success() {
            // given
            Long userId = 1L;
            String email = "test@example.com";

            // when
            String token = jwtUtil.generateAccessToken(userId, email);
            String extractedEmail = jwtUtil.getEmailFromToken(token);

            // then
            assertThat(extractedEmail).isEqualTo(email);
        }

        @Test
        @DisplayName("생성된 Access Token의 만료 시간 확인")
        void generateAccessToken_CheckExpiration_Success() {
            // given
            Long userId = 1L;
            String email = "test@example.com";

            // when
            LocalDateTime beforeGeneration = LocalDateTime.now();
            String token = jwtUtil.generateAccessToken(userId, email);
            LocalDateTime afterGeneration = LocalDateTime.now();
            LocalDateTime expiration = jwtUtil.getExpirationFromToken(token);

            // then
            // 만료 시간이 현재 시간 + 1시간 근처여야 함
            assertThat(expiration).isAfter(beforeGeneration);
            assertThat(expiration).isBefore(afterGeneration.plusHours(2));
        }
    }

    @Nested
    @DisplayName("Refresh Token 생성 테스트")
    class GenerateRefreshTokenTest {

        @Test
        @DisplayName("Refresh Token 생성 성공")
        void generateRefreshToken_Success() {
            // given
            Long userId = 1L;

            // when
            String token = jwtUtil.generateRefreshToken(userId);

            // then
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("생성된 Refresh Token에서 userId 추출 성공")
        void generateRefreshToken_ExtractUserId_Success() {
            // given
            Long userId = 1L;

            // when
            String token = jwtUtil.generateRefreshToken(userId);
            Long extractedUserId = jwtUtil.getUserIdFromToken(token);

            // then
            assertThat(extractedUserId).isEqualTo(userId);
        }

        @Test
        @DisplayName("생성된 Refresh Token의 만료 시간 확인")
        void generateRefreshToken_CheckExpiration_Success() {
            // given
            Long userId = 1L;

            // when
            LocalDateTime beforeGeneration = LocalDateTime.now();
            String token = jwtUtil.generateRefreshToken(userId);
            LocalDateTime afterGeneration = LocalDateTime.now();
            LocalDateTime expiration = jwtUtil.getExpirationFromToken(token);

            // then
            // 만료 시간이 현재 시간 + 24시간 근처여야 함
            assertThat(expiration).isAfter(beforeGeneration);
            assertThat(expiration).isBefore(afterGeneration.plusDays(2));
        }
    }

    @Nested
    @DisplayName("토큰 파싱 테스트")
    class ParseTokenTest {

        @Test
        @DisplayName("유효한 토큰 파싱 성공")
        void parse_ValidToken_Success() {
            // given
            Long userId = 1L;
            String email = "test@example.com";
            String token = jwtUtil.generateAccessToken(userId, email);

            // when
            Jws<Claims> result = jwtUtil.parse(token);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getBody().getSubject()).isEqualTo(String.valueOf(userId));
            assertThat(result.getBody().get("email", String.class)).isEqualTo(email);
        }

        @Test
        @DisplayName("잘못된 서명의 토큰 파싱 시 예외 발생")
        void parse_InvalidSignature_ThrowsException() {
            // given
            Long userId = 1L;
            String email = "test@example.com";
            String token = jwtUtil.generateAccessToken(userId, email);

            // 토큰의 서명 부분을 임의로 변경
            String[] parts = token.split("\\.");
            String invalidToken = parts[0] + "." + parts[1] + ".invalidsignature";

            // when & then
            assertThatThrownBy(() -> jwtUtil.parse(invalidToken))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessage("Invalid JWT Token");
        }

        @Test
        @DisplayName("만료된 토큰 파싱 시 예외 발생")
        void parse_ExpiredToken_ThrowsException() throws InterruptedException {
            // given
            // 만료 시간이 매우 짧은 JwtUtil 생성
            JwtUtil shortExpirationJwtUtil = new JwtUtil(
                    secretKey,
                    1L, // 1ms
                    1L,
                    refreshRepository,
                    userRepository
            );

            Long userId = 1L;
            String email = "test@example.com";
            String token = shortExpirationJwtUtil.generateAccessToken(userId, email);

            // 토큰이 만료될 때까지 대기
            Thread.sleep(10);

            // when & then
            assertThatThrownBy(() -> jwtUtil.parse(token))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessage("Expired JWT Token");
        }

        @Test
        @DisplayName("잘못된 형식의 토큰 파싱 시 예외 발생")
        void parse_MalformedToken_ThrowsException() {
            // given
            String malformedToken = "invalid.token.format";

            // when & then
            assertThatThrownBy(() -> jwtUtil.parse(malformedToken))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessage("Invalid JWT Token");
        }

        @Test
        @DisplayName("빈 토큰 파싱 시 예외 발생")
        void parse_EmptyToken_ThrowsException() {
            // given
            String emptyToken = "";

            // when & then
            assertThatThrownBy(() -> jwtUtil.parse(emptyToken))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }
    }

    @Nested
    @DisplayName("토큰에서 정보 추출 테스트")
    class ExtractFromTokenTest {

        @Test
        @DisplayName("토큰에서 이메일 추출 성공")
        void getEmailFromToken_Success() {
            // given
            Long userId = 1L;
            String email = "test@example.com";
            String token = jwtUtil.generateAccessToken(userId, email);

            // when
            String extractedEmail = jwtUtil.getEmailFromToken(token);

            // then
            assertThat(extractedEmail).isEqualTo(email);
        }

        @Test
        @DisplayName("토큰에서 사용자 ID 추출 성공")
        void getUserIdFromToken_Success() {
            // given
            Long userId = 123L;
            String email = "test@example.com";
            String token = jwtUtil.generateAccessToken(userId, email);

            // when
            Long extractedUserId = jwtUtil.getUserIdFromToken(token);

            // then
            assertThat(extractedUserId).isEqualTo(userId);
        }

        @Test
        @DisplayName("토큰에서 만료 시간 추출 성공")
        void getExpirationFromToken_Success() {
            // given
            Long userId = 1L;
            String email = "test@example.com";
            LocalDateTime beforeGeneration = LocalDateTime.now();
            String token = jwtUtil.generateAccessToken(userId, email);
            LocalDateTime afterGeneration = LocalDateTime.now();

            // when
            LocalDateTime expiration = jwtUtil.getExpirationFromToken(token);

            // then
            assertThat(expiration).isNotNull();
            assertThat(expiration).isAfter(beforeGeneration);
            assertThat(expiration).isBefore(afterGeneration.plusHours(2));
        }

        @Test
        @DisplayName("Refresh Token에서 이메일 추출 시 null 반환 (email claim 없음)")
        void getEmailFromToken_RefreshToken_ReturnsNull() {
            // given
            Long userId = 1L;
            String refreshToken = jwtUtil.generateRefreshToken(userId);

            // when
            String extractedEmail = jwtUtil.getEmailFromToken(refreshToken);

            // then
            assertThat(extractedEmail).isNull();
        }
    }

    @Nested
    @DisplayName("토큰 일관성 테스트")
    class TokenConsistencyTest {

        @Test
        @DisplayName("동일한 정보로 생성한 토큰은 다른 토큰이어야 함 (iat가 다름)")
        void generateToken_SameInfo_DifferentTokens() throws InterruptedException {
            // given
            Long userId = 1L;
            String email = "test@example.com";

            // when
            String token1 = jwtUtil.generateAccessToken(userId, email);
            Thread.sleep(1000); // 발급 시간을 다르게 하기 위해 약간의 지연
            String token2 = jwtUtil.generateAccessToken(userId, email);

            // then
            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("서로 다른 userId로 생성한 토큰은 다른 정보 반환")
        void generateToken_DifferentUserId_DifferentExtractedInfo() {
            // given
            Long userId1 = 1L;
            Long userId2 = 2L;
            String email = "test@example.com";

            // when
            String token1 = jwtUtil.generateAccessToken(userId1, email);
            String token2 = jwtUtil.generateAccessToken(userId2, email);

            Long extractedUserId1 = jwtUtil.getUserIdFromToken(token1);
            Long extractedUserId2 = jwtUtil.getUserIdFromToken(token2);

            // then
            assertThat(extractedUserId1).isEqualTo(userId1);
            assertThat(extractedUserId2).isEqualTo(userId2);
            assertThat(extractedUserId1).isNotEqualTo(extractedUserId2);
        }
    }
}