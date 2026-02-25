package com.ktb.community.repository;

import com.ktb.community.entity.Comment;
import com.ktb.community.entity.Post;
import com.ktb.community.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query("select c from Comment c join fetch c.user where c.post.id = :postId and c.deletedAt is null order by c.id desc")
    List<Comment> findByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(@Param("postId") Long postId, Pageable pageable);

    @Query("select c from Comment c join fetch c.user where c.post.id = :postId and c.id < :cursor and c.deletedAt is null order by c.id desc ")
    List<Comment> findByPostIdAndIdLessThanAndDeletedAtIsNullOrderByCreatedAtDesc(
            @Param("postId") Long postId, @Param("cursor") Long cursor, Pageable pageable);

    List<Comment> findByPostId(Long postId);

    List<Comment> findByUser(User user);

    List<Comment> findByPostIn(List<Post> postList);
}
