package com.ktb.community.service;

import com.ktb.community.dto.request.CreateCommentRequestDto;
import com.ktb.community.dto.request.UpdateCommentRequestDto;
import com.ktb.community.dto.response.CommentResponseDto;
import com.ktb.community.dto.response.CrudCommentResponseDto;
import com.ktb.community.dto.response.CursorCommentResponseDto;
import com.ktb.community.entity.Comment;
import com.ktb.community.entity.Post;
import com.ktb.community.entity.User;
import com.ktb.community.exception.custom.CommentNotFoundException;
import com.ktb.community.exception.custom.PostNotFoundException;
import com.ktb.community.exception.custom.UnauthorizedException;
import com.ktb.community.exception.custom.UserNotFoundException;
import com.ktb.community.repository.CommentRepository;
import com.ktb.community.repository.PostRepository;
import com.ktb.community.repository.UserRepository;
import com.ktb.community.session.SessionUtil;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("session")
public class SessionCommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final SessionUtil sessionUtil;

    public SessionCommentService(CommentRepository commentRepository, PostRepository postRepository, UserRepository userRepository, SessionUtil sessionUtil) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.sessionUtil = sessionUtil;
    }

    public CursorCommentResponseDto<CommentResponseDto> getCommentList(Long postId, Long cursor, int size, String sid) {

        Long userId = sessionUtil.getSession(sid).getUserId();

        List<Comment> comments;
        Pageable pageable = PageRequest.of(0, size + 1);

        if (cursor == null) {
            // cursor가 null이라면 첫 댓글 리스트 불러오기
            comments = this.commentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(postId, pageable);
        } else {
            // cursor가 존재한다면 cursor를 기반으로 다음 댓글드 불러오기
            comments = this.commentRepository.findByPostIdAndIdLessThanAndDeletedAtIsNullOrderByCreatedAtDesc(postId, cursor, pageable);
        }

        boolean hasNext = comments.size() > size;
        if (hasNext) {
            comments = comments.subList(0, size);
        }

        List<CommentResponseDto> commentList = comments.stream()
                .map(comment -> CommentResponseDto.builder()
                        .id(comment.getId())
                        .author(comment.getUser().getNickname())
                        .content(comment.getContent())
                        .createdAt(comment.getCreatedAt())
                        .isMine(comment.getUser().getId().equals(userId)) // 인증이 추가되면 로직 변경하기
                        .build())
                .toList();

        Long nextCursor = !commentList.isEmpty() ? commentList.getLast().getId() : null;


        return new CursorCommentResponseDto<>(commentList, nextCursor, hasNext);
    }

    @Transactional
    public CrudCommentResponseDto writeComment(Long postId, String sid, CreateCommentRequestDto createCommentRequestDto) {
        Long userId = this.sessionUtil.getSession(sid).getUserId();

        Post post = this.postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Not found post"));
        User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Not found user"));

        Comment comment = new Comment();
        comment.setContent(createCommentRequestDto.getContent());
        comment.setPost(post);
        comment.setUser(user);


        Comment savedComment = this.commentRepository.save(comment);
        return new CrudCommentResponseDto(savedComment.getId());
    }

    @Transactional
    public CrudCommentResponseDto modifyComment(String sid, UpdateCommentRequestDto updateCommentRequestDto) {
        Long userId = sessionUtil.getSession(sid).getUserId();
        // 작성자가 맞는지부터확인
        Comment comment = this.commentRepository.findById(updateCommentRequestDto.getCommentId())
                .orElseThrow(() -> new CommentNotFoundException("Not found comment"));

        if (!userId.equals(comment.getUser().getId())) {
            throw new UnauthorizedException("You are not authorized to modify this comment");
        }

        comment.setContent(updateCommentRequestDto.getContent());
        // @Transactional에 의해 자동으로 UPDATE 쿼리 실행 (Dirty Checking)
        return new CrudCommentResponseDto(comment.getId());
    }

    @Transactional
    public CrudCommentResponseDto removeComment(Long commentId, String sid) {
        Long userId = this.sessionUtil.getSession(sid).getUserId();
        Comment comment = this.commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("Not found comment"));

        if (!userId.equals(comment.getUser().getId())) {
            throw new UnauthorizedException("You are not authorized to delete this comment");
        }

        comment.setDeletedAt(java.time.LocalDateTime.now());
        // @Transactional에 의해 자동으로 UPDATE 쿼리 실행 (Dirty Checking)

        return new CrudCommentResponseDto(commentId);
    }



}
