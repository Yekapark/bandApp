package com.yeka.bandapp.plan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 스토어 인앱결제 설정. {@code app.plan.billing.*}.
 *
 * @param gateway       {@code noop}(기본, 로컬·CI) 또는 {@code google}(실제 Play 검증 — 슬라이스 2에서 켠다)
 * @param webhookSecret RTDN(Pub/Sub) push 엔드포인트의 공유 시크릿. Pub/Sub 구독에 {@code ?token=…} 로
 *                      붙여 등록하고, 이 값과 다르면 웹훅을 거부한다. <b>비어 있으면 웹훅을 통째로 막는다</b>
 *                      (fail-closed) — 설정 전까지 아무나 가짜 이벤트를 넣지 못하게. 슬라이스 3에서
 *                      Pub/Sub OIDC 토큰 검증으로 대체·보강한다.
 */
@ConfigurationProperties(prefix = "app.plan.billing")
public record StoreBillingProperties(String gateway, String webhookSecret) {

    public StoreBillingProperties {
        if (gateway == null || gateway.isBlank()) {
            gateway = "noop";
        }
        if (webhookSecret != null && webhookSecret.isBlank()) {
            webhookSecret = null;
        }
    }
}
