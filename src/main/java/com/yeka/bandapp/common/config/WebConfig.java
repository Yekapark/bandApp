package com.yeka.bandapp.common.config;

import com.yeka.bandapp.common.ratelimit.AuthRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 부가 설정. 현재는 인증 엔드포인트 레이트리밋 인터셉터 등록만 한다.
 * ({@code @EnableWebMvc}를 쓰지 않으므로 스프링 부트의 MVC 자동설정은 그대로 유지된다.)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthRateLimitInterceptor authRateLimitInterceptor;

    public WebConfig(AuthRateLimitInterceptor authRateLimitInterceptor) {
        this.authRateLimitInterceptor = authRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authRateLimitInterceptor).addPathPatterns("/api/v1/auth/**");
    }
}
