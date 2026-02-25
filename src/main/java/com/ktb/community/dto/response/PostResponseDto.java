package com.ktb.community.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.JoinColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponseDto {
    private Long id;
    private String title;
    private String content;
    private String author;
    private Long views;
    private Long comments;
    private Long likes;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("profile_image")
    private String profileImage;
    @JsonProperty("is_liked")
    private boolean isLiked;
}
