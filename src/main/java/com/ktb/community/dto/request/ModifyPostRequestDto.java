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

    @Nullable
    @JsonProperty("delete_image_ids")
    List<Long> deleteImageIds = new ArrayList<>();

    @Nullable
    @JsonProperty("add_image_keys")
    @Size(max = 10, message = "이미지는 최대 10개까지 업로드 가능합니다.")
    List<String> addImageKeys = new ArrayList<>();

    @JsonCreator
    public ModifyPostRequestDto(
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("imageKeys") List<String> imageKeys,
            @JsonProperty("delete_image_ids") List<Long> deleteImageIds,
            @JsonProperty("add_image_keys") List<String> addImageKeys) {
        this.title = title;
        this.content = content;
        this.imageKeys = imageKeys != null ? imageKeys : new ArrayList<>();
        this.deleteImageIds = deleteImageIds != null ? deleteImageIds : new ArrayList<>();
        this.addImageKeys = addImageKeys != null ? addImageKeys : new ArrayList<>();
    }
}
