package com.ktb.community.service;

import com.ktb.community.dto.request.LoginRequestDto;
import com.ktb.community.dto.request.SignUpRequestDto;
import com.ktb.community.dto.response.ApiResponseDto;
import com.ktb.community.dto.response.LoginResponseDto;
import com.ktb.community.entity.Refresh;
import com.ktb.community.entity.User;
import com.ktb.community.exception.custom.DuplicateEmailException;
import com.ktb.community.exception.custom.InvalidCredentialsException;
import com.ktb.community.exception.custom.UserNotFoundException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    @Autowired
    public AuthService(UserRepository userRepository, UserService userService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, AuthenticationManager authenticationManager, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
    }

    public Long signUpUser(SignUpRequestDto signUpRequestDto) {
        // 비밀번호와 비밀번호 확인이 동일한지 검사
        if (!signUpRequestDto.getPassword().equals(signUpRequestDto.getPasswordConfirm())) {
            throw new IllegalArgumentException("Password and Password Confirm is not same.");
        }

        // email이 중복되는지 확인
        if (this.userRepository.existsByEmail(signUpRequestDto.getEmail())) {
            throw new DuplicateEmailException("This email already exists");
        }

        if (this.userRepository.existsByNickname(signUpRequestDto.getNickname())) {
            throw new IllegalArgumentException("This nickname is already exist");
        }

        User user = new User();
        user.setEmail(signUpRequestDto.getEmail());
        if (!this.userService.checkValidityPassword(signUpRequestDto.getPassword()).getIsAvailable()) {
            throw new IllegalArgumentException("Password does not meet requirements");
        }
        // bcyrpt로 암호화 추가하기
        user.setPassword(passwordEncoder.encode(signUpRequestDto.getPassword()));
        user.setNickname(signUpRequestDto.getNickname());
        user.setProfileImage(signUpRequestDto.getProfileImage());

        return this.userRepository.save(user).getId();
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequestDto.getEmail(), loginRequestDto.getPassword())
        );

        User user = this.userRepository.findByEmail(loginRequestDto.getEmail()).orElseThrow(() -> new UserNotFoundException("User not found"));

        // 다중 로그인을 사용하려면 추후 삭제하기
        this.refreshTokenService.removeAllRefreshToken(user.getId());

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        LocalDateTime expirationAt = this.jwtUtil.getExpirationFromToken(refreshToken);
        this.refreshTokenService.saveRefreshToken(refreshToken, user, expirationAt);

        return new LoginResponseDto(accessToken, refreshToken, user.getId());
    }

}
