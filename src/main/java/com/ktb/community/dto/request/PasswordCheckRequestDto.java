package com.ktb.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PasswordCheckRequestDto {
    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;

    @JsonCreator
    public PasswordCheckRequestDto(@JsonProperty("password") String password) {
        this.password = password;
    }
}
