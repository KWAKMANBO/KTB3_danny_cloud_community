package com.ktb.community.service;

import com.ktb.community.dto.response.ReIssueRefreshTokenDto;
import com.ktb.community.entity.Refresh;
import com.ktb.community.entity.User;
import com.ktb.community.exception.custom.InvalidRefreshTokenException;
import com.ktb.community.exception.custom.UserNotFoundException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.repository.RefreshRepository;
import com.ktb.community.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class RefreshTokenService {
    private final RefreshRepository refreshRepository;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;


    public RefreshTokenService(RefreshRepository refreshRepository, JwtUtil jwtUtil, UserRepository userRepository) {
        this.refreshRepository = refreshRepository;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    public void saveRefreshToken(String token, User user, LocalDateTime expiredAt) {
        Refresh refreshToken = new Refresh();
        refreshToken.setRefreshToken(token);
        refreshToken.setUser(user);
        refreshToken.setExpirationAt(expiredAt);

        refreshRepository.save(refreshToken);
    }

    public Refresh findByToken(String token) {
        return refreshRepository.findByRefreshToken(token).orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
    }

    public Boolean existByToken(String token) {
        return this.refreshRepository.existsByRefreshToken(token);
    }

    public Refresh checkExistRefreshToken(String token) {
        return findByToken(token);
    }

    public String reIssueAccessToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new InvalidRefreshTokenException("Invalid or expired refresh token");
        }
        Refresh refresh = checkExistRefreshToken(refreshToken);

        if (refresh.getExpirationAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        return this.jwtUtil.generateAccessToken(refresh.getUser().getId(), refresh.getUser().getEmail());
    }


    @Transactional
    public ReIssueRefreshTokenDto reIssueRefreshToken(String refreshToken) {
        // 존재하지 않는 다면 유효하지 않은 토큰
        if (!this.existByToken(refreshToken)) {
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }

        LocalDateTime date1 = this.jwtUtil.getExpirationFromToken(refreshToken).truncatedTo(ChronoUnit.DAYS);

        if (date1.isBefore(LocalDateTime.now())){
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }

        LocalDateTime date2 = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        Duration diff = Duration.between(date2, date1);
        long remainingDays = diff.toDays();
        // refresh Token이 30%보다 많이 남았다면
        if (remainingDays > 3){
            return new ReIssueRefreshTokenDto(refreshToken);
        }

        // 3일 이하 남았으면 재발급
        Refresh refresh = checkExistRefreshToken(refreshToken);
        String newRefreshToken = this.jwtUtil.generateRefreshToken(refresh.getUser().getId());
        User user = this.userRepository.findById(refresh.getUser().getId()).orElseThrow(() -> new UserNotFoundException("User not found"));
        LocalDateTime expiredAt = this.jwtUtil.getExpirationFromToken(newRefreshToken);
        this.removeRefreshToken(refreshToken);
        this.saveRefreshToken(newRefreshToken, user, expiredAt);

        return new ReIssueRefreshTokenDto(newRefreshToken);
    }

    public List<Refresh> findAllTokens(Long userId) {
        return refreshRepository.findByUserId(userId);
    }

    public void removeRefreshToken(String token) {
        this.refreshRepository.deleteByRefreshToken(token);
    }

    public void removeAllRefreshToken(Long userId) {
        this.refreshRepository.deleteAllByUserId(userId);
    }

    public void deleteExpiredTokens() {
        List<Refresh> tokens = this.refreshRepository.findAll();
        List<Refresh> expiredTokens = tokens.stream().filter((token) -> token.getExpirationAt().isBefore(LocalDateTime.now())).toList();
        this.refreshRepository.deleteAll(expiredTokens);
    }

    public int calculateRemainingSeconds(String refreshToken) {
        LocalDateTime expirationAt = this.jwtUtil.getExpirationFromToken(refreshToken);
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(now, expirationAt);
        return (int) duration.getSeconds();
    }
}
