package com.ktb.community.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponseDto {
    private Long id;
    private String author;
    private String content;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("is_mine")
    private boolean isMine;
}
