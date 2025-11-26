package com.ktb.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModifyPostRequestDto {
    @Nullable
    public String title;
    @Nullable
    public String content;
    @Nullable
    List<String> images = new ArrayList<>();

    @JsonCreator
    public ModifyPostRequestDto(
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("images") List<String> images) {
        this.title = title;
        this.content = content;
        this.images = images != null ? images : new ArrayList<>();
    }
}
