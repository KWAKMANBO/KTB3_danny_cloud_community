package com.ktb.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenDataDto {
    private String refreshToken;
    private Long userId;
    private String email;
}
