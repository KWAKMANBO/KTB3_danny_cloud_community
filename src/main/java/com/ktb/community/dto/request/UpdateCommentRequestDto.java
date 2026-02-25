package com.ktb.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Getter
@NoArgsConstructor
public class UpdateCommentRequestDto {
    @NotNull(message =  "댓글ID를 입력해주세요.")
    @JsonProperty("comment_id")
    Long commentId;
    @Length(min = 1, max= 500, message = "댓글은 1~500자 사이만 가능합니다.")
    String content;

    @JsonCreator
    public UpdateCommentRequestDto(
            @JsonProperty("comment_id") Long commentId,
            @JsonProperty("content") String content) {
        this.commentId = commentId;
        this.content = content;
    }
}
