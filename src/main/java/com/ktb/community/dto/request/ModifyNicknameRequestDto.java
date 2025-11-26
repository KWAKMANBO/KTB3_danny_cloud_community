package com.ktb.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Getter
@NoArgsConstructor
public class ModifyNicknameRequestDto {
    @NotBlank(message =  "닉네임을 입력해주세요.")
    @Length(min = 2,  max =15, message = "닉네임은 2~15자 사이를 입력해주세요.")
    @Pattern(regexp = "^[a-zA-Z가-힣0-9_]+$", message = "닉네임은 한글, 영문, 숫자, 언더바만 사용 가능합니다.")
    private String nickname;

    @JsonCreator
    public ModifyNicknameRequestDto(@JsonProperty("nickname") String nickname) {
        this.nickname = nickname;
    }
}
