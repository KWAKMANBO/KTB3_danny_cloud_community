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
public class CursorPageResponseDto<T> {
    private List<T> posts;
    @JsonProperty("next_cursor")
    private Long nextCursor;
    @JsonProperty("has_next")
    private Boolean hasNext;
}
