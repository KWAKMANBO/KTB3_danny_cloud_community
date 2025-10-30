package com.ktb.community.service;

import com.ktb.community.dto.response.LikeResponseDto;
import com.ktb.community.entity.*;
import com.ktb.community.exception.custom.NotExistLikeException;
import com.ktb.community.exception.custom.PostNotFoundException;
import com.ktb.community.exception.custom.UserNotFoundException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.repository.CountRepository;
import com.ktb.community.repository.LikeRepository;
import com.ktb.community.repository.PostRepository;
import com.ktb.community.repository.UserRepository;
import com.ktb.community.session.SessionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Profile("session")
public class SessionLikeService {
    private final SessionUtil sessionUtil;
    private final LikeRepository likeRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CountRepository countRepository;

    @Autowired
    public SessionLikeService(SessionUtil sessionUtil,
                              LikeRepository likeRepository,
                              UserRepository userRepository,
                              PostRepository postRepository,
                              CountRepository countRepository) {
        this.sessionUtil = sessionUtil;
        this.likeRepository = likeRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.countRepository = countRepository;
    }

    @Transactional
    public LikeResponseDto likePost(Long postId, String sid) {
        // TODO :  Redis도입하면 DB가아닌 Redis에서 관리하도록 변경하기
        Long userId = sessionUtil.getSession(sid).getUserId();

        // 연관된 유저와 게시글 찾기
        Post post = postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException("Not found Post"));
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("Not found User"));
        LikePK pk = new LikePK(userId, postId);

        Like like = likeRepository.findById(pk).orElse(null);

        Count count = countRepository.findByPostId(postId)
                .orElseThrow(() -> new PostNotFoundException("Not found post"));

        if (like == null) {
            // 좋아요가 없으면 새로 생성
            like = new Like();
            like.setId(pk);
            like.setUser(user);
            like.setPost(post);
            likeRepository.save(like);
            count.setLikeCount(count.getLikeCount() + 1);
        } else if (like.getDeletedAt() != null) {
            // 삭제된 좋아요 복구
            like.setDeletedAt(null);
            count.setLikeCount(count.getLikeCount() + 1);
        }
        // else: 이미 활성화된 좋아요 존재 → 아무 작업도 하지 않고 postId만 반환

        return new LikeResponseDto(postId, true);
    }

    @Transactional
    public LikeResponseDto unLikePost(Long postId, String sid) {
        // TODO :  Redis도입하면 DB가아닌 Redis에서 관리하도록 변경하기
        Long userId = sessionUtil.getSession(sid).getUserId();
        LikePK pk = new LikePK(userId, postId);
        Like like = likeRepository.findById(pk).orElseThrow(() -> new NotExistLikeException("Not exist like"));

        Count count = countRepository.findByPostId(postId).orElseThrow(() -> new PostNotFoundException("Not found post"));
        like.setDeletedAt(LocalDateTime.now());
        count.setLikeCount(count.getLikeCount() - 1);

        return new LikeResponseDto(postId, false);
    }


}
