package com.yeka.bandapp.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * {@link com.yeka.bandapp.common.entity.BaseTimeEntity}의 {@code @CreatedDate}를 채우기 위한 활성화.
 * 앱 클래스가 아니라 여기에 두어, 슬라이스 테스트에서 감사 기능을 분리할 수 있게 한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
