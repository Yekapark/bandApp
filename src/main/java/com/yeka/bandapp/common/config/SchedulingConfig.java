package com.yeka.bandapp.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 배치 활성화 (탈퇴 계정 개인정보 파기 등).
 * 단일 인스턴스 운영(단일 VM + Compose)을 전제로 한다. 다중 인스턴스로 확장 시 분산 락이 필요하다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
