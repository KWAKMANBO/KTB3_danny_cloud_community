package com.ktb.community.repository;

import com.ktb.community.entity.Session;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface SessionRepository extends CrudRepository<Session, String> {
    Optional<Session> findByUserId(Long userId);

    Optional<Session> findByNickname(String Nickname);
}
