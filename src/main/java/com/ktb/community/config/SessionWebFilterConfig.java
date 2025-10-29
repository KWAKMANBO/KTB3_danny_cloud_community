package com.ktb.community.config;

import com.ktb.community.session.SessionAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("session")
@RequiredArgsConstructor
public class SessionWebFilterConfig {
    private final SessionAuthFilter sessionAuthFilter;

    @Bean
    public FilterRegistrationBean<SessionAuthFilter> sessionFilter() {
        FilterRegistrationBean<SessionAuthFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(sessionAuthFilter);
        registrationBean.addUrlPatterns("/*");  // 모든 경로에 필터 적용
        registrationBean.setOrder(1);  // 필터 우선순위
        return registrationBean;
    }
}
