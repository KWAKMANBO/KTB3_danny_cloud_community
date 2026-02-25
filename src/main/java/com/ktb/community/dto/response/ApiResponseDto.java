package com.ktb.community.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponseDto<T>(Boolean success, String message, T data) {



    public static <T> ApiResponseDto<T> success(T data) {
        return new ApiResponseDto<>(true, null, data);
    }

    public static <T> ApiResponseDto<T> error(String message) {
        return new ApiResponseDto<>(false, message, null);
    }
}
