package com.ktb.community.service;

import com.ktb.community.session.SessionData;
import com.ktb.community.session.SessionUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    private final SessionUtil sessionUtil;

    public SessionService(SessionUtil sessionUtil) {
        this.sessionUtil = sessionUtil;
    }

    // Cookie에서 세션 ID 추출
    public String getSessionIdFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("SID".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    // 현재 요청의 세션 데이터 가져오기
    public SessionData getCurrentSession(HttpServletRequest request) {
        String sessionId = getSessionIdFromCookie(request);
        if (sessionId == null) {
            return null;
        }
        return sessionUtil.getSession(sessionId);
    }

    // 세션 생성 + 쿠키 설정
    public String createSessionWithCookie(HttpServletResponse response, Long userId, String nickname) {
        String sessionId = sessionUtil.createSession(userId, nickname);

        Cookie cookie = new Cookie("SID", sessionId);
        cookie.setHttpOnly(true);  // XSS 방어
        cookie.setPath("/");
        cookie.setMaxAge(3600);  // 1시간
        response.addCookie(cookie);

        return sessionId;
    }

    // 세션 삭제 + 쿠키 삭제
    public boolean removeSessionWithCookie(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = getSessionIdFromCookie(request);
        if (sessionId == null) {
            return false;
        }

        // Redis에서 세션 삭제
        boolean result = sessionUtil.removeSession(sessionId);

        // 쿠키 삭제
        Cookie cookie = new Cookie("SID", null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);

        return result;
    }
}
