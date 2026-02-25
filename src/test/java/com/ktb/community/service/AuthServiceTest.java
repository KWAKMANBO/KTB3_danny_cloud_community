package com.ktb.community.service;

import com.ktb.community.dto.request.LoginRequestDto;
import com.ktb.community.dto.request.SignUpRequestDto;
import com.ktb.community.dto.response.AvailabilityResponseDto;
import com.ktb.community.dto.response.LoginResponseDto;
import com.ktb.community.entity.User;
import com.ktb.community.exception.custom.DuplicateEmailException;
import com.ktb.community.exception.custom.UserNotFoundException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AuthService 테스트")
public class AuthServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

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
    @DisplayName("회원가입 테스트")
    class SignUpTest {

        @Test
        @DisplayName("회원가입 성공")
        void signUpUser_Success() {
            // given
            SignUpRequestDto signUpRequestDto = SignUpRequestDto.builder()
                    .email("test@example.com")
                    .password("password123!")
                    .passwordConfirm("password123!")
                    .nickname("testuser")
                    .profileImage("https://example.com/profile.jpg")
                    .build();

            User savedUser = new User();
            savedUser.setId(1L);
            savedUser.setEmail("test@example.com");
            savedUser.setNickname("testuser");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByNickname(anyString())).thenReturn(false);
            when(userService.checkValidityPassword(anyString()))
                    .thenReturn(new AvailabilityResponseDto(true));
            when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword123");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // when
            Long userId = authService.signUpUser(signUpRequestDto);

            // then
            assertThat(userId).isEqualTo(1L);
            verify(userRepository).existsByEmail("test@example.com");
            verify(userRepository).existsByNickname("testuser");
            verify(userService).checkValidityPassword("password123!");
            verify(passwordEncoder).encode("password123!");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("비밀번호와 비밀번호 확인이 일치하지 않으면 예외 발생")
        void signUpUser_PasswordMismatch_ThrowsException() {
            // given
            SignUpRequestDto signUpRequestDto = SignUpRequestDto.builder()
                    .email("test@example.com")
                    .password("password123!")
                    .passwordConfirm("differentPassword!")
                    .nickname("testuser")
                    .build();

            // when & then
            assertThatThrownBy(() -> authService.signUpUser(signUpRequestDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Password and Password Confirm is not same.");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("이메일이 중복되면 예외 발생")
        void signUpUser_DuplicateEmail_ThrowsException() {
            // given
            SignUpRequestDto signUpRequestDto = SignUpRequestDto.builder()
                    .email("duplicate@example.com")
                    .password("password123!")
                    .passwordConfirm("password123!")
                    .nickname("testuser")
                    .build();

            when(userRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signUpUser(signUpRequestDto))
                    .isInstanceOf(DuplicateEmailException.class)
                    .hasMessage("This email already exists");

            verify(userRepository).existsByEmail("duplicate@example.com");
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("닉네임이 중복되면 예외 발생")
        void signUpUser_DuplicateNickname_ThrowsException() {
            // given
            SignUpRequestDto signUpRequestDto = SignUpRequestDto.builder()
                    .email("test@example.com")
                    .password("password123!")
                    .passwordConfirm("password123!")
                    .nickname("duplicateNickname")
                    .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByNickname("duplicateNickname")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signUpUser(signUpRequestDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("This nickname is already exist");

            verify(userRepository).existsByNickname("duplicateNickname");
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("비밀번호 유효성 검증 실패 시 예외 발생")
        void signUpUser_InvalidPassword_ThrowsException() {
            // given
            SignUpRequestDto signUpRequestDto = SignUpRequestDto.builder()
                    .email("test@example.com")
                    .password("weak")
                    .passwordConfirm("weak")
                    .nickname("testuser")
                    .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByNickname(anyString())).thenReturn(false);
            when(userService.checkValidityPassword("weak"))
                    .thenReturn(new AvailabilityResponseDto(false));

            // when & then
            assertThatThrownBy(() -> authService.signUpUser(signUpRequestDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Password does not meet requirements");

            verify(userService).checkValidityPassword("weak");
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("로그인 테스트")
    class LoginTest {

        @Test
        @DisplayName("로그인 성공")
        void login_Success() {
            // given
            LoginRequestDto loginRequestDto = new LoginRequestDto(
                    "test@example.com",
                    "password123!"
            );

            User user = new User();
            user.setId(1L);
            user.setEmail("test@example.com");
            user.setPassword("encodedPassword");

            Authentication authentication = mock(Authentication.class);
            LocalDateTime expirationTime = LocalDateTime.now().plusDays(7);

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(user));
            when(jwtUtil.generateAccessToken(1L, "test@example.com"))
                    .thenReturn("generated_access_token");
            when(jwtUtil.generateRefreshToken(1L))
                    .thenReturn("generated_refresh_token");
            when(jwtUtil.getExpirationFromToken("generated_refresh_token"))
                    .thenReturn(expirationTime);
            doNothing().when(refreshTokenService).removeAllRefreshToken(1L);
            doNothing().when(refreshTokenService).saveRefreshToken(
                    eq("generated_refresh_token"),
                    eq(user),
                    eq(expirationTime)
            );

            // when
            LoginResponseDto result = authService.login(loginRequestDto);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getAccessToken()).isEqualTo("generated_access_token");
            assertThat(result.getRefreshToken()).isEqualTo("generated_refresh_token");
            assertThat(result.getUserId()).isEqualTo(1L);

            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(userRepository).findByEmail("test@example.com");
            verify(refreshTokenService).removeAllRefreshToken(1L);
            verify(jwtUtil).generateAccessToken(1L, "test@example.com");
            verify(jwtUtil).generateRefreshToken(1L);
            verify(jwtUtil).getExpirationFromToken("generated_refresh_token");
            verify(refreshTokenService).saveRefreshToken(
                    eq("generated_refresh_token"),
                    eq(user),
                    eq(expirationTime)
            );
        }

        @Test
        @DisplayName("존재하지 않는 사용자로 로그인 시도 시 예외 발생")
        void login_UserNotFound_ThrowsException() {
            // given
            LoginRequestDto loginRequestDto = new LoginRequestDto(
                    "nonexistent@example.com",
                    "password123!"
            );

            Authentication authentication = mock(Authentication.class);

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(userRepository.findByEmail("nonexistent@example.com"))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.login(loginRequestDto))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found");

            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(userRepository).findByEmail("nonexistent@example.com");
            verify(jwtUtil, never()).generateAccessToken(anyLong(), anyString());
        }

        @Test
        @DisplayName("잘못된 비밀번호로 로그인 시도 시 인증 예외 발생")
        void login_InvalidCredentials_ThrowsException() {
            // given
            LoginRequestDto loginRequestDto = new LoginRequestDto(
                    "test@example.com",
                    "wrongPassword"
            );

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Invalid credentials"));

            // when & then
            assertThatThrownBy(() -> authService.login(loginRequestDto))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");

            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(userRepository, never()).findByEmail(anyString());
            verify(jwtUtil, never()).generateAccessToken(anyLong(), anyString());
        }
    }
}