package com.ktb.community.repository;

import com.ktb.community.entity.Refresh;
import com.ktb.community.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.sql.Ref;
import java.util.List;
import java.util.Optional;

public interface RefreshRepository extends JpaRepository<Refresh, Long> {
    List<Refresh> findByUserId(Long userId);

    Optional<Refresh> findByRefreshToken(String refreshToken);

    void deleteByRefreshToken(String token);

    void deleteAllByUserId(Long userId);

    boolean existsByRefreshToken(String refreshToken);

}
