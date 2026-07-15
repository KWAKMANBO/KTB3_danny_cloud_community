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

    @Query("select p from Post p join fetch p.user where p.deletedAt is null order by p.id desc")
    List<Post> findByDeletedAtIsNullOrderByIdDesc(Pageable pageable);

    @Query("select p from Post p join fetch p.user where p.id < :cursor and p.deletedAt is null order by p.id desc")
    List<Post> findByIdLessThanAndDeletedAtIsNullOrderByIdDesc(@Param("cursor") Long cursor, Pageable pageable);

    List<Post> findAllByUser(User user);

    @Query("select p from Post p join fetch p.user where p.id = :postId")
    Optional<Post> findByWithUser(@Param("postId") Long postId);
}