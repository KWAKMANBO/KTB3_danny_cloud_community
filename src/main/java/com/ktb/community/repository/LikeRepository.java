package com.ktb.community.repository;

import com.ktb.community.entity.Like;
import com.ktb.community.entity.LikePK;
import com.ktb.community.entity.Post;
import com.ktb.community.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LikeRepository extends JpaRepository<Like, LikePK> {
    boolean existsByIdAndDeletedAtIsNull(LikePK pk);

    boolean existsByUserAndPostAndDeletedAtIsNull(User user, Post post);

    @Query("select l.id.postId from Like l " +
           "where l.id.userId = :userId and l.id.postId in :postIds and l.deletedAt is null")
    List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
