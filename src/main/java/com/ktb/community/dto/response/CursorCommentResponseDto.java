package com.ktb.community.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CursorCommentResponseDto<T> {
    private List<T> comments;
    @JsonProperty("next_cursor")
    private Long nextCursor;
    @JsonProperty("has_next")
    private Boolean hasNext;
}
