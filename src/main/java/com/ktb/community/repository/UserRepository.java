package com.ktb.community.repository;

import com.ktb.community.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Boolean existsByEmail(String email);

    Boolean existsByNickname(String nickname);

    Boolean existsByNicknameAndIdNot(String nickname, Long id);

    Optional<User> findByEmail(String email);
}
