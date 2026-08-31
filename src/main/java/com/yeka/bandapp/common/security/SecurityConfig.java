package com.yeka.bandapp.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * 무상태 Bearer JWT 인증. 세션·CSRF·formLogin·httpBasic을 모두 끄고 JWT 필터로만 인증한다.
 *
 * <p>권한(authorities)은 비워 둔다 — 이 서비스의 권한은 전역 롤이 아니라 밴드별 LEADER/MEMBER이고,
 * 그 검증은 Phase 2 이후 서비스 레이어에서 한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            RestAuthenticationEntryPoint authenticationEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider, AccessTokenBlocklist blocklist) {
        return new JwtAuthenticationFilter(tokenProvider, blocklist);
    }

    @Bean
    RestAuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new RestAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    RestAccessDeniedHandler restAccessDeniedHandler(ObjectMapper objectMapper) {
        return new RestAccessDeniedHandler(objectMapper);
    }

    /**
     * 저장 해시에 {@code {bcrypt}} 접두가 붙어, 이후 알고리즘 교체 시 무중단 마이그레이션이 가능하다.
     * (접두 포함 길이 ~68자 → {@code users.password_hash VARCHAR(100)})
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
