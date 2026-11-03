package com.ktb.community.controller;


import com.ktb.community.dto.response.CursorPageResponseDto;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.service.CommentService;
import com.ktb.community.service.LikeService;
import com.ktb.community.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.*;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PostController size 검증 테스트")
public class PostControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PostService postService;

    @MockitoBean
    CommentService commentService;

    @MockitoBean
    LikeService likeService;

    @MockitoBean
    JwtUtil jwtUtil;

    @MockitoBean
    UserDetailsService userDetailsService;

    @Test
    @DisplayName("size 0이면 400 상태 코드를 반환")
    void size_zero_rejected() throws Exception {
        mockMvc.perform(get("/posts").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("size 21이면 400 상태 코드를 반환")
    void size_over_max_rejected() throws Exception {
        mockMvc.perform(get("/posts").param("size", "21")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("size 20이면 200 상태 코드 반환")
    void size_valid_ok() throws Exception {
        when(postService.getPostList(any(), eq(20), anyString())).thenReturn(new CursorPageResponseDto<>(List.of(), null, false));

        mockMvc.perform(get("/posts").param("size", "20").principal(new UsernamePasswordAuthenticationToken("test@example.com", null, List.of()))).andExpect(status().isOk());
    }


}
