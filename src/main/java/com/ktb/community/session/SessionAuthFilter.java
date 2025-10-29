package com.ktb.community.session;


import com.ktb.community.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class SessionAuthFilter extends OncePerRequestFilter {
    private final SessionUtil sessionUtil;
    private final SessionService sessionService;

    // 필터 제외 경로 목록
    private static final String[] EXCLUDED_PATHS = {
            "/auth", "/auth/login", "/users/check-email", "/users/password"
    };

    // 필터 제외 경로 설정


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return Arrays.stream(EXCLUDED_PATHS).anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Cookie에서 세션 ID 추출
        String sessionId = sessionService.getSessionIdFromCookie(request);

        // 세션 ID가 없으면 인증 실패
        if (sessionId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"인증이 필요합니다.\"}");
            return;
        }

        // Redis에서 세션 데이터 조회
        SessionData sessionData = sessionUtil.getSession(sessionId);

        // 세션이 없거나 만료되었으면 인증 실패
        if (sessionData == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"세션이 만료되었습니다.\"}");
            return;
        }

        // 세션 데이터를 request에 저장
        request.setAttribute("userId", sessionData.getUserId());
        request.setAttribute("nickname", sessionData.getNickname());
        request.setAttribute("sessionData", sessionData);

        // 다음 필터로 진행
        filterChain.doFilter(request, response);
    }
}
