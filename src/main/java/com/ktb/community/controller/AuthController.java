package com.ktb.community.controller;

import com.ktb.community.dto.request.LoginRequestDto;
import com.ktb.community.dto.request.SignUpRequestDto;
import com.ktb.community.dto.response.ApiResponseDto;
import com.ktb.community.dto.response.CrudUserResponseDto;
import com.ktb.community.dto.response.LoginResponseDto;
import com.ktb.community.service.AuthService;
import com.ktb.community.service.RefreshTokenService;
import com.ktb.community.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserService userService;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.expiration.refresh}")
    private long refreshTokenExpiration;

    @Autowired
    public AuthController(UserService userService, AuthService authService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }


    @PostMapping()
    ResponseEntity<ApiResponseDto<?>> signUp(@RequestBody @Valid SignUpRequestDto signUpRequestDto, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            // 모든 필드를 확인하는 로직이 너무 길어 하나로 통합해서 유효하지 않은 필드를 가졌음을 표현
            String message = "Not valid form";
            return ResponseEntity.badRequest().body(ApiResponseDto.error(message));
        }

        Long userId = this.authService.signUpUser(signUpRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(new CrudUserResponseDto(userId)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponseDto<?>> login(
            @RequestBody @Valid LoginRequestDto loginRequestDto,
            HttpServletResponse response) {
        LoginResponseDto loginResponse = this.authService.login(loginRequestDto);

        // Refresh Token을 HttpOnly 쿠키로 설정
        Cookie refreshTokenCookie = new Cookie("refresh_token", loginResponse.getRefreshToken());
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(false);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge((int) (refreshTokenExpiration / 1000)); // application.yml 값 사용 (밀리초 → 초)
        response.addCookie(refreshTokenCookie);

        // 응답 DTO에서는 refreshToken을 null로 설정 (보안)
        LoginResponseDto responseDto = new LoginResponseDto(
                loginResponse.getAccessToken(),
                null,
                loginResponse.getUserId()
        );

        return ResponseEntity.ok(ApiResponseDto.success(responseDto));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto<?>> logout(HttpServletResponse response) {
        // Refresh Token 쿠키 삭제 (MaxAge를 0으로 설정)
        Cookie refreshTokenCookie = new Cookie("refresh_token", null);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(false);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0);  // 즉시 만료
        response.addCookie(refreshTokenCookie);

        return ResponseEntity.ok(ApiResponseDto.success("Logout successful"));
    }

    // AccessToken 재발급 (RefreshToken도 필요시 갱신)
    @PostMapping("/refresh-access-token")
    public ResponseEntity<ApiResponseDto<?>> refreshAccessToken(
            @CookieValue("refresh_token") String refreshToken,
            HttpServletResponse response) {
        // 새 refresh token 받아오기
        var reIssued = this.refreshTokenService.reIssueRefreshToken(refreshToken);

        // 실제 토큰의 남은 만료 시간 계산
        int actualMaxAge = this.refreshTokenService.getRemainingSecond(reIssued.getRefreshToken());

        // 새 refresh token을 쿠키에 설정
        Cookie newRefreshTokenCookie = new Cookie("refresh_token", reIssued.getRefreshToken());
        newRefreshTokenCookie.setHttpOnly(true);
        newRefreshTokenCookie.setSecure(false);
        newRefreshTokenCookie.setPath("/");
        newRefreshTokenCookie.setMaxAge(actualMaxAge);  // 실제 남은 시간으로 설정
        response.addCookie(newRefreshTokenCookie);

        // 새 access token도 함께 발급
        String newAccessToken = this.refreshTokenService.reIssueAccessToken(reIssued.getRefreshToken());

        return ResponseEntity.ok().body(ApiResponseDto.success(
            new LoginResponseDto(newAccessToken, null, null)
        ));
    }

}