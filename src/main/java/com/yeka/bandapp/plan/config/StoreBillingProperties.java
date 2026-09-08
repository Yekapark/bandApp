package com.yeka.bandapp.plan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 스토어 인앱결제 설정. {@code app.plan.billing.*}.
 *
 * @param gateway               {@code noop}(기본, 로컬·CI) 또는 {@code google}(실제 Play 검증)
 * @param webhookSecret         RTDN(Pub/Sub) push 엔드포인트의 공유 시크릿. Pub/Sub 구독에 {@code ?token=…}
 *                              로 붙여 등록하고, 이 값과 다르면 웹훅을 거부한다. <b>비어 있으면 웹훅을 통째로
 *                              막는다</b>(fail-closed). 슬라이스 3에서 Pub/Sub OIDC 토큰 검증으로 보강한다.
 * @param googlePackageName     앱 패키지명(Play Developer API 호출 대상). 예: {@code com.yeka.bandule}
 * @param googleCredentialsPath Play Developer API 권한을 가진 서비스 계정 JSON 키 파일 경로.
 *                              {@code gateway=google} 인데 비어 있으면 기동에 실패한다(FCM 키와 같은 방식).
 * @param googleApplicationName    API 클라이언트 User-Agent 에 들어가는 이름. 아무 값이나 되며 기본 {@code bandule}.
 * @param googlePubsubAudience     RTDN push 구독의 OIDC 토큰 audience. 설정하면 웹훅이 Bearer 토큰을
 *                                 Google 공개키로 검증한다. 비면 {@code ?token=} 공유 시크릿으로만 인증.
 * @param googlePubsubServiceAccount OIDC 토큰의 {@code email} 이 이 값과 같아야 한다(선택). Pub/Sub
 *                                 구독에 지정한 인증 서비스 계정 주소.
 */
@ConfigurationProperties(prefix = "app.plan.billing")
public record StoreBillingProperties(String gateway, String webhookSecret,
                                     String googlePackageName, String googleCredentialsPath,
                                     String googleApplicationName, String googlePubsubAudience,
                                     String googlePubsubServiceAccount) {

    public StoreBillingProperties {
        if (gateway == null || gateway.isBlank()) {
            gateway = "noop";
        }
        if (webhookSecret != null && webhookSecret.isBlank()) {
            webhookSecret = null;
        }
        if (googlePackageName != null && googlePackageName.isBlank()) {
            googlePackageName = null;
        }
        if (googleCredentialsPath != null && googleCredentialsPath.isBlank()) {
            googleCredentialsPath = null;
        }
        if (googleApplicationName == null || googleApplicationName.isBlank()) {
            googleApplicationName = "bandule";
        }
        if (googlePubsubAudience != null && googlePubsubAudience.isBlank()) {
            googlePubsubAudience = null;
        }
        if (googlePubsubServiceAccount != null && googlePubsubServiceAccount.isBlank()) {
            googlePubsubServiceAccount = null;
        }
    }
}
