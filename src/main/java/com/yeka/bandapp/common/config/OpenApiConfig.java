package com.yeka.bandapp.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bandappOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("밴드 합주 관리 앱 API")
                .description("밴드 단위 합주 일정 기록·참석·정산 서비스")
                .version("v0.0.1"));
    }
}
