package com.ktb.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EmailCheckRequestDto {
    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "유효하지 않은 이메일형식입니다.")
    private String email;

    @JsonCreator
    public EmailCheckRequestDto(@JsonProperty("email") String email) {
        this.email = email;
    }
}
