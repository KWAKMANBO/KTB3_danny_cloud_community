package com.ktb.community.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PresignedUrlResponseDto {
    private String presignedUrl;
    private String imageKey;
    private String expiresAt;
}