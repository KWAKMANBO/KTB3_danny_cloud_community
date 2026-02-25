package com.ktb.community.repository;

import com.ktb.community.entity.Post;
import com.ktb.community.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    // deletedAt이 null인 게시글만 조회 (삭제되지 않은 게시글)
    List<Post> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    List<Post> findByIdLessThanAndDeletedAtIsNullOrderByCreatedAtDesc(Long cursor, Pageable pageable);

    List<Post> findAllByUser(User user);

    @Query("select p from Post p join fetch p.user where p.id = :postId")
    Optional<Post> findByWithUser(@Param("postId") Long postId);
}
