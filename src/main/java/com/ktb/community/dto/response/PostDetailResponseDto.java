package com.ktb.community.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDetailResponseDto {
    private Long id;
    private String title;
    private String content;
    private String author;
    @JsonProperty("is_mine")
    private boolean isMine;
    @JsonProperty("is_liked")
    private boolean isLiked;
    private Long views;
    private Long comments;
    private Long likes;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    private List<String> images;
}
