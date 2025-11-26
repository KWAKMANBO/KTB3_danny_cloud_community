package com.ktb.community.service;

import com.ktb.community.dto.request.ChangePasswordRequestDto;
import com.ktb.community.dto.request.ModifyNicknameRequestDto;
import com.ktb.community.dto.response.AvailabilityResponseDto;
import com.ktb.community.dto.response.CrudUserResponseDto;
import com.ktb.community.dto.response.UserInfoResponseDto;
import com.ktb.community.entity.User;
import com.ktb.community.exception.custom.DuplicateNicknameException;
import com.ktb.community.exception.custom.InvalidNicknameException;
import com.ktb.community.exception.custom.InvalidPasswordException;
import com.ktb.community.exception.custom.UserNotFoundException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("UserService 테스트")
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private CountRepository countRepository;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private RefreshRepository refreshRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

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
    @DisplayName("이메일 중복 확인 테스트")
    class CheckDuplicateEmailTest {

        @Test
        @DisplayName("사용 가능한 이메일")
        void checkDuplicateEmail_Available() {
            // given
            String email = "available@example.com";
            when(userRepository.existsByEmail(email)).thenReturn(false);

            // when
            AvailabilityResponseDto result = userService.checkDuplicateEmail(email);

            // then
            assertThat(result.getIsAvailable()).isTrue();
            verify(userRepository).existsByEmail(email);
        }

        @Test
        @DisplayName("중복된 이메일")
        void checkDuplicateEmail_Duplicate() {
            // given
            String email = "duplicate@example.com";
            when(userRepository.existsByEmail(email)).thenReturn(true);

            // when
            AvailabilityResponseDto result = userService.checkDuplicateEmail(email);

            // then
            assertThat(result.getIsAvailable()).isFalse();
            verify(userRepository).existsByEmail(email);
        }
    }

    @Nested
    @DisplayName("비밀번호 유효성 검증 테스트")
    class CheckValidityPasswordTest {

        @Test
        @DisplayName("유효한 비밀번호 - 모든 조건 충족")
        void checkValidityPassword_Valid() {
            // given
            String password = "password123!";

            // when
            AvailabilityResponseDto result = userService.checkValidityPassword(password);

            // then
            assertThat(result.getIsAvailable()).isTrue();
        }

        @Test
        @DisplayName("8자 미만인 비밀번호")
        void checkValidityPassword_TooShort() {
            // given
            String password = "pwd1!";

            // when
            AvailabilityResponseDto result = userService.checkValidityPassword(password);

            // then
            assertThat(result.getIsAvailable()).isFalse();
        }

        @Test
        @DisplayName("소문자가 없는 비밀번호")
        void checkValidityPassword_NoLowercase() {
            // given
            String password = "PASSWORD123!";

            // when
            AvailabilityResponseDto result = userService.checkValidityPassword(password);

            // then
            assertThat(result.getIsAvailable()).isFalse();
        }

        @Test
        @DisplayName("숫자가 없는 비밀번호")
        void checkValidityPassword_NoDigit() {
            // given
            String password = "password!";

            // when
            AvailabilityResponseDto result = userService.checkValidityPassword(password);

            // then
            assertThat(result.getIsAvailable()).isFalse();
        }

        @Test
        @DisplayName("특수문자가 없는 비밀번호")
        void checkValidityPassword_NoSpecialChar() {
            // given
            String password = "password123";

            // when
            AvailabilityResponseDto result = userService.checkValidityPassword(password);

            // then
            assertThat(result.getIsAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("내 정보 조회 테스트")
    class ReadMyInfoTest {

        @Test
        @DisplayName("내 정보 조회 성공")
        void readMyInfo_Success() {
            // given
            String email = "test@example.com";
            User user = new User();
            user.setEmail(email);
            user.setNickname("testuser");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            // when
            UserInfoResponseDto result = userService.readMyInfo(email);

            // then
            assertThat(result.getEmail()).isEqualTo(email);
            assertThat(result.getNickname()).isEqualTo("testuser");
            verify(userRepository).findByEmail(email);
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void readMyInfo_UserNotFound_ThrowsException() {
            // given
            String email = "notfound@example.com";
            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.readMyInfo(email))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("Not found user.");

            verify(userRepository).findByEmail(email);
        }
    }

    @Nested
    @DisplayName("닉네임 변경 테스트")
    class ChangeNicknameTest {

        @Test
        @DisplayName("닉네임 변경 성공")
        void changeNickname_Success() {
            // given
            String email = "test@example.com";
            String oldNickname = "oldnickname";
            String newNickname = "newnickname";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);
            user.setNickname(oldNickname);

            ModifyNicknameRequestDto requestDto = new ModifyNicknameRequestDto(newNickname);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(userRepository.existsByNicknameAndIdNot(newNickname, 1L)).thenReturn(false);

            // when
            CrudUserResponseDto result = userService.changeNickname(email, requestDto);

            // then
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(user.getNickname()).isEqualTo(newNickname);
            verify(userRepository).findByEmail(email);
            verify(userRepository).existsByNicknameAndIdNot(newNickname, 1L);
        }

        @Test
        @DisplayName("동일한 닉네임으로 변경 시도 시 예외 발생")
        void changeNickname_SameNickname_ThrowsException() {
            // given
            String email = "test@example.com";
            String nickname = "samenickname";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);
            user.setNickname(nickname);

            ModifyNicknameRequestDto requestDto = new ModifyNicknameRequestDto(nickname);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> userService.changeNickname(email, requestDto))
                    .isInstanceOf(InvalidNicknameException.class)
                    .hasMessage("Same nickname is not acceptable");

            verify(userRepository).findByEmail(email);
            verify(userRepository, never()).existsByNicknameAndIdNot(anyString(), anyLong());
        }

        @Test
        @DisplayName("이미 사용 중인 닉네임으로 변경 시도 시 예외 발생")
        void changeNickname_DuplicateNickname_ThrowsException() {
            // given
            String email = "test@example.com";
            String oldNickname = "oldnickname";
            String newNickname = "duplicatenickname";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);
            user.setNickname(oldNickname);

            ModifyNicknameRequestDto requestDto = new ModifyNicknameRequestDto(newNickname);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(userRepository.existsByNicknameAndIdNot(newNickname, 1L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.changeNickname(email, requestDto))
                    .isInstanceOf(DuplicateNicknameException.class)
                    .hasMessage("This nickname is already in use");

            verify(userRepository).findByEmail(email);
            verify(userRepository).existsByNicknameAndIdNot(newNickname, 1L);
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void changeNickname_UserNotFound_ThrowsException() {
            // given
            String email = "notfound@example.com";
            ModifyNicknameRequestDto requestDto = new ModifyNicknameRequestDto("newnickname");

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.changeNickname(email, requestDto))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("Not found user");

            verify(userRepository).findByEmail(email);
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 테스트")
    class ChangePasswordTest {

        @Test
        @DisplayName("비밀번호 변경 성공")
        void changePassword_Success() {
            // given
            String email = "test@example.com";
            String currentPassword = "oldPassword123!";
            String newPassword = "newPassword456@";
            String encodedOldPassword = "encodedOldPassword";
            String encodedNewPassword = "encodedNewPassword";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);
            user.setPassword(encodedOldPassword);

            ChangePasswordRequestDto requestDto = new ChangePasswordRequestDto(
                    currentPassword,
                    newPassword
            );

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(currentPassword, encodedOldPassword)).thenReturn(true);
            when(passwordEncoder.encode(newPassword)).thenReturn(encodedNewPassword);

            // when
            CrudUserResponseDto result = userService.changePassword(email, requestDto);

            // then
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(user.getPassword()).isEqualTo(encodedNewPassword);
            verify(userRepository).findByEmail(email);
            verify(passwordEncoder).matches(currentPassword, encodedOldPassword);
            verify(passwordEncoder).encode(newPassword);
        }

        @Test
        @DisplayName("현재 비밀번호가 일치하지 않으면 예외 발생")
        void changePassword_IncorrectCurrentPassword_ThrowsException() {
            // given
            String email = "test@example.com";
            String wrongPassword = "wrongPassword";
            String newPassword = "newPassword456@";
            String encodedPassword = "encodedPassword";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);
            user.setPassword(encodedPassword);

            ChangePasswordRequestDto requestDto = new ChangePasswordRequestDto(
                    wrongPassword,
                    newPassword
            );

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(wrongPassword, encodedPassword)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.changePassword(email, requestDto))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessage("Current password is incorrect");

            verify(userRepository).findByEmail(email);
            verify(passwordEncoder).matches(wrongPassword, encodedPassword);
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면 예외 발생")
        void changePassword_SamePassword_ThrowsException() {
            // given
            String email = "test@example.com";
            String password = "samePassword123!";
            String encodedPassword = "encodedPassword";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);
            user.setPassword(encodedPassword);

            ChangePasswordRequestDto requestDto = new ChangePasswordRequestDto(
                    password,
                    password
            );

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(password, encodedPassword)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.changePassword(email, requestDto))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessage("New password must be different from current password");

            verify(userRepository).findByEmail(email);
            verify(passwordEncoder).matches(password, encodedPassword);
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("새 비밀번호가 유효성 검증을 통과하지 못하면 예외 발생")
        void changePassword_InvalidNewPassword_ThrowsException() {
            // given
            String email = "test@example.com";
            String currentPassword = "oldPassword123!";
            String invalidNewPassword = "weak";
            String encodedPassword = "encodedPassword";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);
            user.setPassword(encodedPassword);

            ChangePasswordRequestDto requestDto = new ChangePasswordRequestDto(
                    currentPassword,
                    invalidNewPassword
            );

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(currentPassword, encodedPassword)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.changePassword(email, requestDto))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessage("New password does not meet requirements");

            verify(userRepository).findByEmail(email);
            verify(passwordEncoder).matches(currentPassword, encodedPassword);
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void changePassword_UserNotFound_ThrowsException() {
            // given
            String email = "notfound@example.com";
            ChangePasswordRequestDto requestDto = new ChangePasswordRequestDto(
                    "oldPassword123!",
                    "newPassword456@"
            );

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.changePassword(email, requestDto))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("Not found user");

            verify(userRepository).findByEmail(email);
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }
}