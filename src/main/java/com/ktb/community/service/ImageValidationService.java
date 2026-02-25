package com.ktb.community.service;

import com.ktb.community.exception.custom.InvalidFileTypeException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ImageValidationService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final int MAX_POST_IMAGES = 10;
    private static final int MAX_PROFILE_IMAGES = 1;

    /**
     * 파일 확장자 검증
     *
     * @param extension 파일 확장자
     * @throws InvalidFileTypeException 허용되지 않은 파일 타입인 경우
     */
    public void validateFileExtension(String extension) {
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase().strip())) {
            throw new InvalidFileTypeException(
                    "허용되지 않은 파일 형식입니다. 허용된 형식: " + String.join(", ", ALLOWED_EXTENSIONS)
            );
        }
    }

    /**
     * 이미지 개수 검증
     *
     * @param count 이미지 개수
     * @param imageType POST 또는 PROFILE
     * @throws IllegalArgumentException 이미지 개수가 범위를 벗어난 경우
     */
    public void validateImageCount(int count, String imageType) {
        int maxCount = imageType.equalsIgnoreCase("POST") ? MAX_POST_IMAGES : MAX_PROFILE_IMAGES;

        if (count <= 0 || count > maxCount) {
            throw new IllegalArgumentException(
                    String.format("이미지 개수는 1개 이상 %d개 이하여야 합니다.", maxCount)
            );
        }
    }
}