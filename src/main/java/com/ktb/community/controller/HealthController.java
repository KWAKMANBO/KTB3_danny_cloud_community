package com.ktb.community.controller;

import com.ktb.community.dto.response.ApiResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponseDto<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponseDto.success("OK"));
    }
}
