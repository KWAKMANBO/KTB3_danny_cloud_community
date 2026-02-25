package com.ktb.community.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    // 사용자의 요청에서 token을 추출하고 해당 토큰이 검증 과정을 통과하는 확인하는 역항을 수행하는 필터
    // OncePerRequestFilter는 요청당 1번만 수행됨
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Autowired
    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        // 구현한 customUserDetailService를 spring이 직접 주입해줌
        // 결합도를 낮출 수 있음
        this.userDetailsService = userDetailsService;
    }

    // 요청에서 jwt값을 추출하는 함수
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void handleTokenException(HttpServletResponse response, String message) {
        try {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            Map<String, String> error = Map.of("error", "unauthorized", "message", message);
            // reposnse.getWriter는 서버가 클라이언트에게 직접 응답 내용을 보낼때 사용하는 출력 스트림을 얻는 메서드
            // Spring 필터나 예외처리 구간에서는 return ReponseEntity를 사용할 수 없음
            // 컨트롤러 밖이기 떄문에 Spring MVC의 응답처리 로직이 적용x
            // 직접 응답을 만들어 보낼때에는 HttpServletResponse 객체를 통해 수동으로 설정해줘야함
            response.getWriter().write(new ObjectMapper().writeValueAsString(error));
        } catch (IOException e) {
            log.error("error", e);
        }

    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = this.getJwtFromRequest(request);
            // StringUtils.hasText 메서드는
            // 문자열이 null이 아니고, 길이가 0보다크고, 공백이아닌 문자를 하나라도 갖고있으면 true를 반환
            if (StringUtils.hasText(jwt) && this.jwtUtil.parse(jwt) != null) {
                // jwt가 값을 가지고 있고, 유효성이 검증 됐다면
                String email = this.jwtUtil.getEmailFromToken(jwt);

                UserDetails userDetails = this.userDetailsService.loadUserByUsername(email);
                // UsernamePasswordAuthenticationToken클래스는 사용자 인증을 처리하는 중요한 역할을 수행
                // Spring Security에서 사용자 이름과 비밀번호를 기반으로 인증 요청을 나타내는 클래스
                // 사용자로부터 입력받은 사용자 이름과 비밀번호를 AuthenticationManager에 전달하여 인증을 수행
                // 인자로는 principal, credentials, authorities 를 받음
                // principal은 사용자 이름
                // credentials은 비밀번호
                // authorities는 사용자의 권한
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                // 인증된 사용자의 정보를 담는 Authentication 객체를 직접 생성하는 코드

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // 인증된 객체에 요청 관련 추가 정보를 저장하는 코드
                // WebAuthenticationDetailSource는 HttpServletRequest에서 정보를 추출해
                // WebAuthenticationDetails 객체를 생성함


                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 인증 객체를 SpringSecurity 전역 컨텍스에 등록하는 코드

            }

        } catch (Exception e) {
            handleTokenException(response, "The token is expired please reissue Access Token");
        }
        filterChain.doFilter(request, response);

    }
}
