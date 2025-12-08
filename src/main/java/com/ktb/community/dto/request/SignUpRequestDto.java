package com.ktb.community.dto.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.validator.constraints.Length;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignUpRequestDto {
    @Email(message = "올바은 이메일형식을 입력해주세요.")
    @NotBlank(message = "이메일을 입력해주세요.")
    @Length(min = 10, max= 50, message = "이메일 길이는 15~20자만 가능합니다.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Length(min = 8, max= 30)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=\\-{};:,.<>?]).{8,30}$", message = "비밀번호는 소문자, 숫자, 특수문자를 모두 포함해야 합니다.")
    private String password;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String passwordConfirm;

    @NotBlank(message =  "닉네임을 입력해주세요.")
    @Length(min = 2,  max =15, message = "닉네임은 2~15자 사이를 입력해주세요.")
    @Pattern(regexp = "^[a-zA-Z가-힣0-9_]+$", message = "닉네임은 한글, 영문, 숫자, 언더바만 사용 가능합니다.")
    private String nickname;

    @Nullable
    private String profileImage;


}
