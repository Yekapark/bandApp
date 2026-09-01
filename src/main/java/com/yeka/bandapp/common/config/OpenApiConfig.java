package com.yeka.bandapp.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI bandappOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("밴드 합주 관리 앱 API")
                        .description("""
                                밴드 단위 합주 일정 기록·참석·정산 서비스 (Phase 0~3: 인증 · 밴드 · 초대 · 멤버 · 합주실).

                                **공통 응답 포맷**
                                - 성공: `{"success": true, "data": <결과>, "error": null}`
                                - 실패: `{"success": false, "data": null, "error": {"code": "ERROR_CODE", "message": "...", "fieldErrors": [...]}}`
                                - `error.code`는 원인별 상수(예: `NOT_BAND_LEADER`, `INVITE_EXPIRED`). HTTP 상태코드와 함께 본다.

                                **인증** — `/api/v1/auth/**` 와 초대 딥링크(무인증)를 제외한 모든 API는
                                `Authorization: Bearer <accessToken>` 필요. 로그인/가입 응답의 `tokens.accessToken` 을
                                우측 상단 **Authorize** 에 넣으면 이후 요청에 자동 적용된다. access 토큰 수명 30분,
                                만료 시 `/api/v1/auth/refresh` 로 갱신.

                                **로컬 기본값** — 카카오 키 미설정 시 `/api/v1/auth/kakao` 는 503,
                                네이버 키 미설정 시 합주실 `lat`/`lng` 는 null 로 저장된다(정상).
                                """)
                        .version("v0.0.1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
