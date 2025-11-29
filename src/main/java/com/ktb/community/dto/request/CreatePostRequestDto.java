package com.ktb.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import java.util.ArrayList;
import java.util.List;


@Getter
@NoArgsConstructor
public class CreatePostRequestDto {
    @NotBlank(message = "제목은 필수 입력 입니다.")
    @Length(max = 30)
    String title;

    @NotBlank(message = "내용은 필수 입력입니다.")
    String content;

    @Size(max = 10, message = "이미지는 최대 10개까지 업로드 가능합니다.")
    List<String> imageKeys = new ArrayList<>();

    @JsonCreator
    public CreatePostRequestDto(
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("imageKeys") List<String> imageKeys) {
        this.title = title;
        this.content = content;
        this.imageKeys = imageKeys != null ? imageKeys : new ArrayList<>();
    }
}
