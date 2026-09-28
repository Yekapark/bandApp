package com.yeka.bandapp.plan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 스토어 인앱결제 설정. {@code app.plan.billing.*}.
 *
 * @param gateway               {@code noop}(기본, 로컬·CI) 또는 {@code google}(실제 Play 검증)
 * @param webhookSecret         RTDN(Pub/Sub) push 엔드포인트의 공유 시크릿. Pub/Sub 구독에 {@code ?token=…}
 *                              로 붙여 등록하고, 이 값과 다르면 웹훅을 거부한다. <b>비어 있으면 웹훅을 통째로
 *                              막는다</b>(fail-closed). 슬라이스 3에서 Pub/Sub OIDC 토큰 검증으로 보강한다.
 * @param googlePackageName     앱 패키지명(Play Developer API 호출 대상). 예: {@code com.yeka.bandule}
 * @param googleProductIds      우리가 파는 구독 상품 id 들(Play Console 의 제품 ID). 스토어가 돌려준 구매의
 *                              상품이 여기 없으면 PREMIUM 을 주지 않는다 — 나중에 더 싼 상품을 하나라도
 *                              추가하면, 그 토큰으로 PREMIUM 을 받는 길이 열리기 때문이다.
 *                              <b>왜 여러 개인가</b> — Google 계정 하나는 같은 구독 상품을 동시에 하나만 가질 수
 *                              있다. 구독은 밴드 단위라 밴드 두 개의 밴드장이 둘 다 결제하려면 상품이 달라야 한다.
 *                              그래서 값·기간이 같은 상품을 여러 개 두고, 앱이 아직 안 산 것을 골라 결제한다
 *                              (LAUNCH_REVIEW B4). 기본 {@code premium_yearly, premium_yearly_2 … _5}.
 * @param googleCredentialsPath Play Developer API 권한을 가진 서비스 계정 JSON 키 파일 경로.
 *                              {@code gateway=google} 인데 비어 있으면 기동에 실패한다(FCM 키와 같은 방식).
 * @param googleApplicationName    API 클라이언트 User-Agent 에 들어가는 이름. 아무 값이나 되며 기본 {@code bandule}.
 * @param googlePubsubAudience     RTDN push 구독의 OIDC 토큰 audience. 설정하면 웹훅이 Bearer 토큰을
 *                                 Google 공개키로 검증한다. 비면 {@code ?token=} 공유 시크릿으로만 인증.
 * @param googlePubsubServiceAccount OIDC 토큰의 {@code email} 이 이 값과 같아야 한다. Pub/Sub 구독에
 *                                 지정한 인증 서비스 계정 주소. <b>audience 와 함께 필수</b> — 이 값이
 *                                 없으면 OIDC 경로를 아예 열지 않는다({@code WebhookAuthenticator}).
 */
@ConfigurationProperties(prefix = "app.plan.billing")
public record StoreBillingProperties(String gateway, String webhookSecret,
                                     String googlePackageName, List<String> googleProductIds,
                                     String googleCredentialsPath,
                                     String googleApplicationName, String googlePubsubAudience,
                                     String googlePubsubServiceAccount) {

    /** 값·기간이 같은 PREMIUM 연 구독 상품들. 앱 {@code IapService.productIds} 와 같아야 한다. */
    public static final List<String> DEFAULT_GOOGLE_PRODUCT_IDS = List.of(
            "premium_yearly", "premium_yearly_2", "premium_yearly_3", "premium_yearly_4", "premium_yearly_5");

    /** 우리가 파는 PREMIUM 상품인가. */
    public boolean sellsGoogleProduct(String productId) {
        return productId != null && googleProductIds.contains(productId);
    }

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
        googleProductIds = googleProductIds == null ? List.of() : googleProductIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .toList();
        if (googleProductIds.isEmpty()) {
            googleProductIds = DEFAULT_GOOGLE_PRODUCT_IDS;
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
