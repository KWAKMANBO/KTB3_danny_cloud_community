package com.ktb.community.util;

import jakarta.servlet.http.HttpServletRequest;

public class JwtRequestUtil {

    private static final String USER_ID_ATTRIBUTE = "userId";
    private static final String EMAIL_ATTRIBUTE = "email";
    private static final String TOKEN_ATTRIBUTE = "token";

    public static Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(USER_ID_ATTRIBUTE);
    }

    public static String getEmail(HttpServletRequest request) {
        return (String) request.getAttribute(EMAIL_ATTRIBUTE);
    }

    public static String getToken(HttpServletRequest request) {
        return (String) request.getAttribute(TOKEN_ATTRIBUTE);
    }
}