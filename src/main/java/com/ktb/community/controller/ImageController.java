package com.ktb.community.controller;

import com.ktb.community.dto.response.ApiResponseDto;
import com.ktb.community.dto.response.PresignedUrlResponseDto;
import com.ktb.community.entity.User;
import com.ktb.community.exception.custom.UserNotFoundException;
import com.ktb.community.repository.UserRepository;
import com.ktb.community.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/images")
public class ImageController {

    private final ImageService imageService;
    private final UserRepository userRepository;

    @Autowired
    public ImageController(ImageService imageService, UserRepository userRepository) {
        this.imageService = imageService;
        this.userRepository = userRepository;
    }

    /**
     * Presigned URL 요청 엔드포인트
     *
     * GET /api/images/upload-url?count=3&imageType=POST&fileExtension=jpg
     *
     * @param count 이미지 개수
     * @param imageType POST 또는 PROFILE
     * @param fileExtension 파일 확장자 (기본값: jpg)
     * @param authentication JWT 인증 정보
     * @return Presigned URL 리스트
     */
    @GetMapping("/upload-url")
    public ResponseEntity<ApiResponseDto<List<PresignedUrlResponseDto>>> getUploadUrl(
            @RequestParam int count,
            @RequestParam String imageType,
            @RequestParam(defaultValue = "jpg") String fileExtension,
            Authentication authentication
    ) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        List<PresignedUrlResponseDto> urls;

        if ("PROFILE".equalsIgnoreCase(imageType)) {
            // 프로필 이미지는 1개만
            PresignedUrlResponseDto url = imageService.requestProfileImageUploadUrl(
                    fileExtension,
                    user.getId()
            );
            urls = List.of(url);
        } else {
            System.out.println(fileExtension);
            // 게시글 이미지는 여러 개 가능
            urls = imageService.requestPostImageUploadUrls(
                    count,
                    fileExtension,
                    user.getId()
            );
        }

        return ResponseEntity.ok(ApiResponseDto.success(urls));
    }
}