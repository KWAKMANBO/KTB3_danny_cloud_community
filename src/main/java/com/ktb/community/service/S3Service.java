package com.ktb.community.service;

import com.ktb.community.config.S3Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Config s3Config;

    @Autowired
    public S3Service(S3Client s3Client, S3Presigner s3Presigner, S3Config s3Config) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Config = s3Config;
    }

    /**
     * 업로드용 Presigned URL 생성
     *
     * @param imageType POST or PROFILE
     * @param fileExtension 파일 확장자 (예: jpg, png)
     * @param userId 사용자 ID
     * @return Presigned URL
     */
    public String generatePresignedUploadUrl(String imageType, String fileExtension, Long userId) {
        String key = generateUniqueKey(imageType, userId, fileExtension);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Config.getBucketName())
                .key(key)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(s3Config.getPresignedUrlExpiration()))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * 다운로드용 Presigned URL 생성
     *
     * @param imageKey S3 키 (예: images/posts/123/1234567890_uuid.jpg)
     * @return Presigned GET URL
     */
    public String generatePresignedDownloadUrl(String imageKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Config.getBucketName())
                .key(imageKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))  // 1시간 유효
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * S3 객체 존재 여부 확인
     *
     * @param imageKey S3 키
     * @return 존재하면 true, 아니면 false
     */
    public boolean doesObjectExist(String imageKey) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(imageKey)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * S3 객체 삭제
     *
     * @param imageKey S3 키
     */
    public void deleteObject(String imageKey) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(s3Config.getBucketName())
                .key(imageKey)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    /**
     * 전체 S3 URL에서 키 추출
     *
     * @param fullUrl 전체 S3 URL
     * @return S3 키 (예: images/posts/123/1234567890_uuid.jpg)
     */
    public String extractKeyFromUrl(String fullUrl) {
        // URL 형식: https://ktb-community-images.s3.ap-northeast-2.amazonaws.com/images/posts/123/1234567890_uuid.jpg
        // 또는: https://s3.ap-northeast-2.amazonaws.com/ktb-community-images/images/posts/123/1234567890_uuid.jpg

        if (fullUrl == null || fullUrl.isEmpty()) {
            return null;
        }

        // 버킷 이름 뒤의 슬래시 이후 부분을 키로 추출
        String bucketName = s3Config.getBucketName();

        // 패턴 1: https://bucket-name.s3.region.amazonaws.com/key
        String pattern1 = bucketName + ".s3." + s3Config.getRegion() + ".amazonaws.com/";
        int index1 = fullUrl.indexOf(pattern1);
        if (index1 != -1) {
            return fullUrl.substring(index1 + pattern1.length());
        }

        // 패턴 2: https://s3.region.amazonaws.com/bucket-name/key
        String pattern2 = "s3." + s3Config.getRegion() + ".amazonaws.com/" + bucketName + "/";
        int index2 = fullUrl.indexOf(pattern2);
        if (index2 != -1) {
            return fullUrl.substring(index2 + pattern2.length());
        }

        // 패턴을 찾지 못한 경우 전체 URL 반환 (fallback)
        return fullUrl;
    }

    /**
     * S3 키로부터 전체 URL 생성
     *
     * @param imageKey S3 키
     * @return 전체 S3 URL
     */
    public String constructUrlFromKey(String imageKey) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                s3Config.getBucketName(),
                s3Config.getRegion(),
                imageKey);
    }

    /**
     * 유니크한 S3 키 생성
     *
     * @param imageType POST or PROFILE
     * @param userId 사용자 ID
     * @param extension 파일 확장자
     * @return 유니크한 S3 키 (예: images/posts/123/1735371000_a1b2c3d4.jpg)
     */
    private String generateUniqueKey(String imageType, Long userId, String extension) {
        String type = imageType.equalsIgnoreCase("POST") ? "posts" : "profiles";
        long timestamp = System.currentTimeMillis() / 1000;  // Unix timestamp (초 단위)
        String uuid = UUID.randomUUID().toString().substring(0, 8);  // UUID 앞 8자리

        return String.format("images/%s/%d/%d_%s.%s", type, userId, timestamp, uuid, extension);
    }

    /**
     * 특정 imageKey에 대한 Presigned URL을 반환 (업로드용)
     * ImageService에서 사용할 수 있도록 key를 직접 받는 메서드
     *
     * @param imageKey 미리 생성된 S3 키
     * @return Presigned PUT URL
     */
    public String generatePresignedUploadUrlForKey(String imageKey) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Config.getBucketName())
                .key(imageKey)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(s3Config.getPresignedUrlExpiration()))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * 유니크한 S3 키를 public 메서드로 노출 (ImageService에서 사용)
     *
     * @param imageType POST or PROFILE
     * @param userId 사용자 ID
     * @param extension 파일 확장자
     * @return 유니크한 S3 키
     */
    public String createUniqueKey(String imageType, Long userId, String extension) {
        return generateUniqueKey(imageType, userId, extension);
    }
}