package com.ktb.community.service;

import com.ktb.community.dto.response.PresignedUrlResponseDto;
import com.ktb.community.entity.Image;
import com.ktb.community.entity.Post;
import com.ktb.community.exception.custom.ImageNotFoundException;
import com.ktb.community.repository.ImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ImageService {

    private final S3Service s3Service;
    private final ImageValidationService imageValidationService;
    private final ImageRepository imageRepository;

    @Autowired
    public ImageService(S3Service s3Service, ImageValidationService imageValidationService, ImageRepository imageRepository) {
        this.s3Service = s3Service;
        this.imageValidationService = imageValidationService;
        this.imageRepository = imageRepository;
    }

    /**
     * 게시글 이미지용 Presigned URL 요청 처리
     *
     * @param count 이미지 개수
     * @param fileExtension 파일 확장자
     * @param userId 사용자 ID
     * @return Presigned URL 응답 리스트
     */
    public List<PresignedUrlResponseDto> requestPostImageUploadUrls(int count, String fileExtension, Long userId) {
        // 검증
        imageValidationService.validateImageCount(count, "POST");
        imageValidationService.validateFileExtension(fileExtension);

        List<PresignedUrlResponseDto> responses = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String imageKey = s3Service.createUniqueKey("POST", userId, fileExtension);
            String presignedUrl = s3Service.generatePresignedUploadUrlForKey(imageKey);
            String expiresAt = calculateExpirationTime();

            PresignedUrlResponseDto response = PresignedUrlResponseDto.builder()
                    .presignedUrl(presignedUrl)
                    .imageKey(imageKey)
                    .expiresAt(expiresAt)
                    .build();

            responses.add(response);
        }

        return responses;
    }

    /**
     * 프로필 이미지용 Presigned URL 요청 처리
     *
     * @param fileExtension 파일 확장자
     * @param userId 사용자 ID
     * @return Presigned URL 응답
     */
    public PresignedUrlResponseDto requestProfileImageUploadUrl(String fileExtension, Long userId) {
        // 검증
        imageValidationService.validateImageCount(1, "PROFILE");
        imageValidationService.validateFileExtension(fileExtension);

        String imageKey = s3Service.createUniqueKey("PROFILE", userId, fileExtension);
        String presignedUrl = s3Service.generatePresignedUploadUrlForKey(imageKey);
        String expiresAt = calculateExpirationTime();

        return PresignedUrlResponseDto.builder()
                .presignedUrl(presignedUrl)
                .imageKey(imageKey)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * 게시글 이미지 업로드 확인 및 DB 저장
     *
     * @param imageKeys S3 이미지 키 리스트
     * @param post 게시글
     * @return 생성된 Image 엔티티 리스트
     */
    @Transactional
    public List<Image> confirmPostImagesUpload(List<String> imageKeys, Post post) {
        List<Image> images = new ArrayList<>();

        for (int i = 0; i < imageKeys.size(); i++) {
            String imageKey = imageKeys.get(i);

            // S3에 파일 존재 여부 확인
            if (!s3Service.doesObjectExist(imageKey)) {
                throw new ImageNotFoundException(
                        "S3에서 이미지를 찾을 수 없습니다: " + imageKey +
                        ". 업로드가 완료되었는지 확인해주세요."
                );
            }

            // 전체 URL 생성
            String fullUrl = s3Service.constructUrlFromKey(imageKey);

            // Image 엔티티 생성
            Image image = new Image();
            image.setUrl(fullUrl);
            image.setPost(post);
            image.setDisplayOrder(i);

            images.add(image);
        }

        // DB에 저장
        return imageRepository.saveAll(images);
    }

    /**
     * 프로필 이미지 업로드 확인
     *
     * @param imageKey S3 이미지 키
     * @return 전체 S3 URL
     */
    public String confirmProfileImageUpload(String imageKey) {
        // S3에 파일 존재 여부 확인
        if (!s3Service.doesObjectExist(imageKey)) {
            throw new ImageNotFoundException(
                    "S3에서 이미지를 찾을 수 없습니다: " + imageKey +
                    ". 업로드가 완료되었는지 확인해주세요."
            );
        }

        // 전체 URL 생성
        return s3Service.constructUrlFromKey(imageKey);
    }

    /**
     * 게시글 이미지 S3/DB 삭제
     *
     * @param postId 게시글 ID
     */
    @Transactional
    public void deletePostImages(Long postId) {
        // DB에서 해당 포스트의 이미지 조회
        List<Image> images = imageRepository.findByPostIdAndDeletedAtIsNull(postId);

        for (Image image : images) {
            // S3에서 삭제
            String imageKey = s3Service.extractKeyFromUrl(image.getUrl());
            if (imageKey != null) {
                try {
                    s3Service.deleteObject(imageKey);
                } catch (Exception e) {
                    // S3 삭제 실패 시 로그만 남기고 계속 진행
                    // 실제로는 logger를 사용해야 함
                    System.err.println("S3 이미지 삭제 실패: " + imageKey + " - " + e.getMessage());
                }
            }

            // DB에서 soft delete
            image.setDeletedAt(LocalDateTime.now());
        }
    }

    /**
     * 이미지 URL 리스트에 대한 Presigned Download URL 생성
     *
     * @param imageUrls 전체 S3 URL 리스트
     * @return Presigned Download URL 리스트
     */
    public List<String> generateDownloadUrls(List<String> imageUrls) {
        List<String> downloadUrls = new ArrayList<>();

        for (String url : imageUrls) {
            if (url != null && !url.isEmpty()) {
                String key = s3Service.extractKeyFromUrl(url);
                String downloadUrl = s3Service.generatePresignedDownloadUrl(key);
                downloadUrls.add(downloadUrl);
            }
        }

        return downloadUrls;
    }

    /**
     * 단일 이미지 URL에 대한 Presigned Download URL 생성
     *
     * @param imageUrl 전체 S3 URL
     * @return Presigned Download URL (null일 경우 null 반환)
     */
    public String generateDownloadUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        String key = s3Service.extractKeyFromUrl(imageUrl);
        return s3Service.generatePresignedDownloadUrl(key);
    }

    /**
     * 만료 시간 계산 (현재 시간 + presigned URL expiration)
     *
     * @return ISO 8601 형식의 만료 시간
     */
    private String calculateExpirationTime() {
        // S3Config에서 설정된 expiration 시간(초)을 가져와서 현재 시간에 더함
        long expirationSeconds = 900; // 기본 15분 (S3Config에서 가져오는 것이 이상적)
        Instant expirationInstant = Instant.now().plusSeconds(expirationSeconds);

        // ISO 8601 형식으로 변환
        return DateTimeFormatter.ISO_INSTANT.format(expirationInstant);
    }

}