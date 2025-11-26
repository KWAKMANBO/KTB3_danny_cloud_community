package com.ktb.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangePasswordRequestDto {
    @JsonProperty("current_password")
    @NotBlank(message = "현재 비밀번호를 입력해주세요.")
    private String currentPassword;

    @JsonProperty("new_password")
    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    private String newPassword;

    @JsonCreator
    public ChangePasswordRequestDto(
            @JsonProperty("current_password") String currentPassword,
            @JsonProperty("new_password") String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }
}