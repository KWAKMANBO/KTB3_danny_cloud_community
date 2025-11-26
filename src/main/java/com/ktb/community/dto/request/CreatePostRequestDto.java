package com.ktb.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
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

    List<String> images = new ArrayList<>();

    @JsonCreator
    public CreatePostRequestDto(
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("images") List<String> images) {
        this.title = title;
        this.content = content;
        this.images = images != null ? images : new ArrayList<>();
    }
}
