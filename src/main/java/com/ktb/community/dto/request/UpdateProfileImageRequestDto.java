package com.ktb.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProfileImageRequestDto {

    @NotBlank(message = "이미지 키는 필수입니다.")
    @Pattern(regexp = "^images/(posts|profiles)/\\d+/.*\\.(jpg|jpeg|png|gif|webp)$",
             message = "잘못된 이미지 키 형식입니다.")
    private String imageKey;
}