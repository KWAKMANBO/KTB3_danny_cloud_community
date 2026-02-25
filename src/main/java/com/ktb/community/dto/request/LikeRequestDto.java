package com.ktb.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LikeRequestDto {
    @JsonProperty("post_id")
    private Long postId;

    @JsonCreator
    public LikeRequestDto(@JsonProperty("post_id") Long postId) {
        this.postId = postId;
    }
}
