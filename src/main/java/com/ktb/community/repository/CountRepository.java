package com.ktb.community.repository;

import com.ktb.community.entity.Count;
import com.ktb.community.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CountRepository extends JpaRepository<Count, Long> {
    Optional<Count> findByPostId(Long postId);

    List<Count> findByPostIn(List<Post> postList);
}
