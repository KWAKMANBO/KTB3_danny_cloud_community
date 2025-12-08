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


    @Transactional(readOnly = false)
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

        // 이미지 변경 로직 - 하위 호환성을 위한 기존 방식 (전체 교체)
        if (modifyPostRequestDto.getImageKeys() != null) {
            // 기존 이미지 삭제 (S3 + DB)
            imageService.deletePostImages(postId);

            // 새 이미지 저장
            if (!modifyPostRequestDto.getImageKeys().isEmpty()) {
                imageService.confirmPostImagesUpload(modifyPostRequestDto.getImageKeys(), post);
            }
        }
        // 새로운 방식: 부분 추가/삭제
        else {
            // 1. 특정 이미지 삭제
            if (modifyPostRequestDto.getDeleteImageIds() != null && !modifyPostRequestDto.getDeleteImageIds().isEmpty()) {
                imageService.deleteImagesByIds(modifyPostRequestDto.getDeleteImageIds());
            }

            // 2. 새 이미지 추가
            if (modifyPostRequestDto.getAddImageKeys() != null && !modifyPostRequestDto.getAddImageKeys().isEmpty()) {
                System.out.println("=== 이미지 추가 시작 ===");
                System.out.println("추가할 이미지 키: " + modifyPostRequestDto.getAddImageKeys());

                // 현재 이미지 개수 확인
                long currentImageCount = imageRepository.findByPostIdAndDeletedAtIsNull(postId).size();
                long newImageCount = modifyPostRequestDto.getAddImageKeys().size();
                long deleteImageCount = modifyPostRequestDto.getDeleteImageIds() != null
                    ? modifyPostRequestDto.getDeleteImageIds().size() : 0;

                System.out.println("현재 이미지 개수: " + currentImageCount);
                System.out.println("추가할 개수: " + newImageCount);
                System.out.println("삭제할 개수: " + deleteImageCount);

                // 최종 이미지 개수 검증 (최대 10개)
                long finalImageCount = currentImageCount - deleteImageCount + newImageCount;
                if (finalImageCount > 10) {
                    throw new IllegalArgumentException(
                        String.format("이미지는 최대 10개까지만 가능합니다. (현재: %d, 삭제: %d, 추가: %d, 최종: %d)",
                            currentImageCount, deleteImageCount, newImageCount, finalImageCount)
                    );
                }

                // 새 이미지의 displayOrder는 기존 이미지 최대값 + 1부터 시작
                List<Image> existingImages = imageRepository.findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAsc(postId);
                int nextDisplayOrder = existingImages.isEmpty() ? 0 :
                    existingImages.stream()
                        .mapToInt(Image::getDisplayOrder)
                        .max()
                        .orElse(-1) + 1;

                System.out.println("다음 displayOrder: " + nextDisplayOrder);
                System.out.println("Post ID: " + post.getId() + ", Post 영속 상태: " + (post != null));

                List<Image> newImages = new ArrayList<>();
                for (int i = 0; i < modifyPostRequestDto.getAddImageKeys().size(); i++) {
                    String imageKey = modifyPostRequestDto.getAddImageKeys().get(i);
                    System.out.println("처리 중인 이미지 키: " + imageKey);

                    try {
                        // S3에 파일 존재 여부 확인 및 URL 생성
                        String fullUrl = imageService.confirmSinglePostImageUpload(imageKey);
                        System.out.println("S3 URL 생성 성공: " + fullUrl);

                        Image image = new Image();
                        image.setUrl(fullUrl);
                        image.setPost(post);
                        image.setDisplayOrder(nextDisplayOrder + i);

                        System.out.println("Image 생성: URL=" + image.getUrl() +
                                         ", PostId=" + (image.getPost() != null ? image.getPost().getId() : "null") +
                                         ", DisplayOrder=" + image.getDisplayOrder());

                        newImages.add(image);
                    } catch (Exception e) {
                        System.err.println("이미지 처리 중 에러: " + e.getMessage());
                        e.printStackTrace();
                        throw e;
                    }
                }

                System.out.println("저장할 이미지 개수: " + newImages.size());

                // DB에 저장 (post와의 매핑 포함)
                List<Image> savedImages = imageRepository.saveAll(newImages);
                System.out.println("저장된 이미지 개수: " + savedImages.size());

                for (Image img : savedImages) {
                    System.out.println("저장된 이미지 ID: " + img.getId() +
                                     ", Post ID: " + (img.getPost() != null ? img.getPost().getId() : "null"));
                }

                System.out.println("=== 이미지 추가 완료 ===");
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

