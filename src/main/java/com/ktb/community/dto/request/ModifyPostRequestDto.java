package com.ktb.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Size;
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
    @Size(max = 10, message = "이미지는 최대 10개까지 업로드 가능합니다.")
    List<String> imageKeys = new ArrayList<>();

    @JsonCreator
    public ModifyPostRequestDto(
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("imageKeys") List<String> imageKeys) {
        this.title = title;
        this.content = content;
        this.imageKeys = imageKeys != null ? imageKeys : new ArrayList<>();
    }
}
