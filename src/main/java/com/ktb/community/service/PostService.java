package com.ktb.community.service;

import com.ktb.community.dto.request.CreatePostRequestDto;
import com.ktb.community.dto.request.ModifyPostRequestDto;
import com.ktb.community.dto.response.*;
import com.ktb.community.entity.*;
import com.ktb.community.exception.custom.PostNotFoundException;
import com.ktb.community.exception.custom.UnauthorizedException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository postRepository;
    private final CountRepository countRepository;
    private final ImageRepository imageRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final ImageService imageService;
    private final LikeService likeService;

    @Autowired
    public PostService(PostRepository postRepository, CountRepository countRepository, ImageRepository imageRepository, CommentRepository commentRepository, UserRepository userRepository, JwtUtil jwtUtil, ImageService imageService, LikeService likeService) {
        this.postRepository = postRepository;
        this.countRepository = countRepository;
        this.imageRepository = imageRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.imageService = imageService;
        this.likeService = likeService;
    }

    @Transactional
    public CrudPostResponseDto createPost(CreatePostRequestDto createPostRequestDto, String email) {
        Post post = new Post();
        post.setTitle(createPostRequestDto.getTitle());
        post.setContent(createPostRequestDto.getContent());
        User user = this.userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Users not found"));
        post.setUser(user);

        Post savedPost = this.postRepository.save(post);

        // imageKeys를 사용하여 S3 검증 후 DB 저장
        if (createPostRequestDto.getImageKeys() != null && !createPostRequestDto.getImageKeys().isEmpty()) {
            imageService.confirmPostImagesUpload(createPostRequestDto.getImageKeys(), savedPost);
        }

        Count count = new Count();
        count.setPost(savedPost);  // Post 설정 필수!
        count.setLikeCount(0L);
        count.setCommentCount(0L);
        count.setViewCount(0L);
        this.countRepository.save(count);

        return new CrudPostResponseDto(savedPost.getId());
    }

    @Transactional
    public CursorPageResponseDto<PostResponseDto> getPostList(Long cursor, int size, String email) {
        Pageable pageable = PageRequest.of(0, size + 1);

        List<Post> posts;
        if (cursor == null) {
            // null이면 첫페이지
            posts = this.postRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable);
        } else {
            // 다음 페이지
            posts = this.postRepository.findByIdLessThanAndDeletedAtIsNullOrderByCreatedAtDesc(cursor, pageable);
        }

        boolean hasNext = posts.size() > size;
        if (hasNext) {
            // 다음 페이지가 존재한다면
            posts = posts.subList(0, size);
        }

        List<PostResponseDto> postContent = posts.stream()
                .map(post -> {
                    Count count = this.countRepository.findByPostId(post.getId()).orElse(null);
                    boolean isLiked = likeService.checkLike(post.getId(), email);
                    return PostResponseDto.builder()
                            .id(post.getId())
                            .title(post.getTitle())
                            .content(post.getContent())
                            .author(post.getUser().getNickname())
                            .profileImage(post.getUser().getProfileImage())
                            .createdAt(post.getCreatedAt())
                            .isLiked(isLiked)
                            .views(count != null ? count.getViewCount() : 0L)
                            .likes(count != null ? count.getLikeCount() : 0L)
                            .comments(count != null ? count.getCommentCount() : 0L)
                            .build();
                }).collect(Collectors.toList());
        Long nextCursor = !postContent.isEmpty() ? postContent.getLast().getId() : null;

        return new CursorPageResponseDto<>(postContent, nextCursor, hasNext);
    }

    public PostDetailResponseDto getPostContent(Long postId, String email) {
        User user = this.userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = this.postRepository.findByWithUser(postId).orElseThrow(() -> new PostNotFoundException("Not found post"));

        List<String> imageUrls = this.imageRepository.findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAsc(post.getId())
                .stream()
                .map(Image::getUrl)
                .toList();

        // Private 버킷: Presigned Download URL 생성
        List<String> presignedDownloadUrls = imageService.generateDownloadUrls(imageUrls);

        Count count = this.countRepository.findByPostId(post.getId()).orElse(null);
        boolean isLiked = this.likeService.checkLike(postId, email);
        return PostDetailResponseDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .author(post.getUser().getNickname())
                .isMine(user.getId().equals(post.getUser().getId()))
                .images(presignedDownloadUrls)  // Presigned URL 반환
                .createdAt(post.getCreatedAt())
                .isLiked(isLiked)
                .views(count != null ? count.getViewCount() : 0L)
                .likes(count != null ? count.getLikeCount() : 0L)
                .comments(count != null ? count.getCommentCount() : 0L)
                .build();
    }


    @Transactional
    public CrudPostResponseDto modifyPostContent(Long postId, String email, ModifyPostRequestDto modifyPostRequestDto) {
        User user = this.userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = this.postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found"));

        // 작성자 확인
        if (!user.getId().equals(post.getUser().getId())) {
            throw new UnauthorizedException("You are not authorized to modify this post");
        }

        // null이 아닌 필드만 업데이트
        if (modifyPostRequestDto.getTitle() != null) {
            post.setTitle(modifyPostRequestDto.getTitle());
        }
        if (modifyPostRequestDto.getContent() != null) {
            post.setContent(modifyPostRequestDto.getContent());
        }

        // 이미지 변경 로직
        if (modifyPostRequestDto.getImageKeys() != null) {
            // 기존 이미지 삭제 (S3 + DB)
            imageService.deletePostImages(postId);

            // 새 이미지 저장
            if (!modifyPostRequestDto.getImageKeys().isEmpty()) {
                imageService.confirmPostImagesUpload(modifyPostRequestDto.getImageKeys(), post);
            }
        }

        // @Transactional에 의해 자동으로 UPDATE 쿼리 실행 (Dirty Checking)
        return new CrudPostResponseDto(post.getId());
    }

    @Transactional
    public CrudPostResponseDto removePost(Long postId, String email) {
        User user = this.userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Post post = this.postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found"));
        if (!user.getId().equals(post.getUser().getId())) {
            throw new UnauthorizedException("You are not authorized to delete this post");
        }

        // S3에서 이미지 삭제
        imageService.deletePostImages(postId);

        // post를 soft delete
        post.setDeletedAt(LocalDateTime.now());

        // 연관된 댓글들도 soft delete
        List<Comment> comments = this.commentRepository.findByPostId(postId);
        comments.forEach(comment -> comment.setDeletedAt(LocalDateTime.now()));

        return new CrudPostResponseDto(postId);
    }
}

