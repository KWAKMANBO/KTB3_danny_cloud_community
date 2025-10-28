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

    @Autowired
    public PostService(PostRepository postRepository, CountRepository countRepository, ImageRepository imageRepository, CommentRepository commentRepository, UserRepository userRepository, JwtUtil jwtUtil) {
        this.postRepository = postRepository;
        this.countRepository = countRepository;
        this.imageRepository = imageRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public CrudPostResponseDto createPost(CreatePostRequestDto createPostRequestDto, String email) {
        Post post = new Post();
        post.setTitle(createPostRequestDto.getTitle());
        post.setContent(createPostRequestDto.getContent());
        User user = this.userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Users not found"));
        post.setUser(user);

        Post savedPost = this.postRepository.save(post);

        if (!createPostRequestDto.getImages().isEmpty()) {
            List<String> imageUrls = createPostRequestDto.getImages();
            List<Image> images = new ArrayList<>();
            for (int i = 0; i < createPostRequestDto.getImages().size(); i++) {
                Image img = new Image();
                img.setUrl(imageUrls.get(i));
                img.setPost(savedPost);
                img.setDisplayOrder(i);
                images.add(img);
            }
            this.imageRepository.saveAll(images);
        }

        Count count = new Count();
        count.setPost(savedPost);  // Post 설정 필수!
        count.setLikeCount(0L);
        count.setCommentCount(0L);
        count.setViewCount(0L);
        this.countRepository.save(count);

        return new CrudPostResponseDto(savedPost.getId());
    }

    public CursorPageResponseDto<PostResponseDto> getPostList(Long cursor, int size) {
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

                    return PostResponseDto.builder()
                            .id(post.getId())
                            .title(post.getTitle())
                            .content(post.getContent())
                            .author(post.getUser().getNickname())
                            .createdAt(post.getCreatedAt())
                            .views(count != null ? count.getViewCount() : 0L)
                            .likes(count != null ? count.getLikeCount() : 0L)
                            .comments(count != null ? count.getCommentCount() : 0L)
                            .build();
                }).collect(Collectors.toList());
        Long nextCursor = !postContent.isEmpty() ? postContent.getLast().getId() : null;

        return new CursorPageResponseDto<>(postContent, nextCursor, hasNext);
    }

    public PostDetailResponseDto getPostContent(Long postId, String token) {
        Long userId = this.jwtUtil.extractUserIdFromToken(token);

        Post post = this.postRepository.findByWithUser(postId).orElseThrow(() -> new PostNotFoundException("Not found post"));

        List<String> images = this.imageRepository.findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAsc(post.getId())
                .stream()
                .map(Image::getUrl)
                .toList();

        Count count = this.countRepository.findByPostId(post.getId()).orElse(null);
        return PostDetailResponseDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .author(post.getUser().getNickname())
                .isMine(userId.equals(post.getUser().getId()))
                .images(images)
                .createdAt(post.getCreatedAt())
                .views(count != null ? count.getViewCount() : 0L)
                .likes(count != null ? count.getLikeCount() : 0L)
                .comments(count != null ? count.getCommentCount() : 0L)
                .build();
    }


    @Transactional
    public CrudPostResponseDto modifyPostContent(Long postId, String token, ModifyPostRequestDto modifyPostRequestDto) {
        // JWT에서 userId 추출
        Long userId = this.jwtUtil.extractUserIdFromToken(token);

        Post post = this.postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found"));

        // 작성자 확인
        if (!userId.equals(post.getUser().getId())) {
            throw new UnauthorizedException("You are not authorized to modify this post");
        }

        // null이 아닌 필드만 업데이트
        if (modifyPostRequestDto.getTitle() != null) {
            post.setTitle(modifyPostRequestDto.getTitle());
        }
        if (modifyPostRequestDto.getContent() != null) {
            post.setContent(modifyPostRequestDto.getContent());
        }

        // TODO : 이미지 변경 로직은 추후 추가하기
        // if (modifyPostRequestDto.getImages() != null) {
        //     // 이미지 업데이트 로직
        // }

        // @Transactional에 의해 자동으로 UPDATE 쿼리 실행 (Dirty Checking)
        return new CrudPostResponseDto(post.getId());
    }

    @Transactional
    public CrudPostResponseDto removePost(Long postId, String token) {
        Long userId = this.jwtUtil.extractUserIdFromToken(token);
        Post post = this.postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found"));
        if (!userId.equals(post.getUser().getId())) {
            throw new UnauthorizedException("You are not authorized to delete this post");
        }

        // post를 soft delete
        post.setDeletedAt(LocalDateTime.now());

        // 연관된 댓글들도 soft delete
        List<Comment> comments = this.commentRepository.findByPostId(postId);
        comments.forEach(comment -> comment.setDeletedAt(LocalDateTime.now()));

        // TODO : 이미지 로직 추가되면 이미지 삭제로직 추가하기


        return new CrudPostResponseDto(postId);
    }
}

