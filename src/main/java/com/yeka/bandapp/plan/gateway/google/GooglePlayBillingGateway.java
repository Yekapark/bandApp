package com.yeka.bandapp.plan.gateway.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.CancelSubscriptionPurchaseRequest;
import com.google.api.services.androidpublisher.model.CancellationContext;
import com.google.api.services.androidpublisher.model.DeferSubscriptionPurchaseRequest;
import com.google.api.services.androidpublisher.model.DeferSubscriptionPurchaseResponse;
import com.google.api.services.androidpublisher.model.DeferralContext;
import com.google.api.services.androidpublisher.model.ItemExpiryTimeDetails;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.yeka.bandapp.plan.config.StoreBillingProperties;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway;
import com.yeka.bandapp.plan.gateway.StoreBillingUnavailableException;
import com.yeka.bandapp.plan.gateway.StoreDeferRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 실제 Google Play Developer API 로 구독 구매를 검증하는 게이트웨이. {@code app.plan.billing.gateway=google}
 * 일 때만 뜬다(그 전에는 {@code NoOpStoreBillingGateway}).
 *
 * <ul>
 *   <li>{@link #fetch} — {@code purchases.subscriptionsv2.get} 로 구독의 현재 상태·만료일을 읽는다.
 *       토큰이 없거나 더는 유효하지 않으면(404/410, invalid-token 400) {@code Optional.empty()}.
 *       일시적 실패(네트워크·5xx)는 {@link StoreBillingUnavailableException}.
 *   <li>{@link #acknowledge} — {@code purchases.subscriptions.acknowledge}. 이미 확인된 구매의 400 은 삼킨다.
 *   <li>{@link #defer} — {@code purchases.subscriptionsv2.defer}. 최신 {@code etag} 를 get 으로 받아 함께 보낸다
 *       (그 사이 상태가 바뀌었으면 Google 이 거절한다). 쿠폰 기간을 결제 기간에 쌓을 때 쓴다(B7).
 * </ul>
 *
 * <p>서비스 계정 키 파일이 없으면 <b>기동에 실패</b>한다(FCM 자격증명과 같은 방식) — {@code google}
 * 로 켜 놓고 키를 안 넣는 실수를 첫 결제까지 미루지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "app.plan.billing", name = "gateway", havingValue = "google")
public class GooglePlayBillingGateway implements StoreBillingGateway {

    private static final Logger log = LoggerFactory.getLogger(GooglePlayBillingGateway.class);

    private final AndroidPublisher publisher;
    private final String packageName;

    @Autowired   // 테스트용 생성자가 따로 있어 Spring 이 고를 것을 지정한다 — 빠지면 운영(gateway=google) 기동 실패
    public GooglePlayBillingGateway(StoreBillingProperties properties) {
        this.packageName = require(properties.googlePackageName(), "app.plan.billing.google-package-name");
        String credentialsPath = require(properties.googleCredentialsPath(), "app.plan.billing.google-credentials-path");
        this.publisher = buildPublisher(credentialsPath, properties.googleApplicationName());
        log.info("Google Play 결제 검증 활성화 package={}", packageName);
    }

    /** 테스트용 — 자격증명 파일 없이 Play 응답을 흉내 낸 클라이언트를 넣는다. */
    GooglePlayBillingGateway(AndroidPublisher publisher, String packageName) {
        this.publisher = publisher;
        this.packageName = packageName;
    }

    @Override
    public Optional<StoreSubscription> fetch(Store store, String purchaseToken) {
        try {
            SubscriptionPurchaseV2 purchase = publisher.purchases().subscriptionsv2()
                    .get(packageName, purchaseToken).execute();
            return Optional.of(GooglePlaySubscriptionMapper.toStoreSubscription(purchaseToken, purchase));
        } catch (GoogleJsonResponseException e) {
            if (isDefinitelyInvalid(e)) {
                log.info("Play 구매 토큰 무효 status={} — empty 로 반환", e.getStatusCode());
                return Optional.empty();
            }
            throw new StoreBillingUnavailableException("Play 구독 조회 실패 status=" + e.getStatusCode(), e);
        } catch (IOException e) {
            throw new StoreBillingUnavailableException("Play 구독 조회 중 네트워크 오류", e);
        }
    }

    @Override
    public void acknowledge(Store store, String productId, String purchaseToken) {
        try {
            publisher.purchases().subscriptions()
                    .acknowledge(packageName, productId, purchaseToken, new SubscriptionPurchasesAcknowledgeRequest())
                    .execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 400 && messageOf(e).contains("acknowledg")) {
                log.debug("Play 구매가 이미 acknowledge 됨 — 무시");
                return;
            }
            throw new StoreBillingUnavailableException("Play acknowledge 실패 status=" + e.getStatusCode(), e);
        } catch (IOException e) {
            throw new StoreBillingUnavailableException("Play acknowledge 중 네트워크 오류", e);
        }
    }

    @Override
    public Instant defer(Store store, String purchaseToken, Duration by) {
        try {
            SubscriptionPurchaseV2 current = publisher.purchases().subscriptionsv2()
                    .get(packageName, purchaseToken).execute();
            DeferSubscriptionPurchaseRequest request = new DeferSubscriptionPurchaseRequest()
                    .setDeferralContext(new DeferralContext()
                            .setEtag(current.getEtag())
                            .setDeferDuration(by.toSeconds() + "s"));
            DeferSubscriptionPurchaseResponse response = publisher.purchases().subscriptionsv2()
                    .defer(packageName, purchaseToken, request).execute();
            Instant newExpiry = response.getItemExpiryTimeDetails() == null ? null
                    : response.getItemExpiryTimeDetails().stream()
                            .map(ItemExpiryTimeDetails::getExpiryTime)
                            .filter(t -> t != null && !t.isBlank())
                            .map(t -> OffsetDateTime.parse(t).toInstant())
                            .max(Instant::compareTo)
                            .orElse(null);
            if (newExpiry == null) {
                // 응답에 없으면 다시 읽는다 — 연기는 이미 반영됐다.
                newExpiry = fetch(store, purchaseToken)
                        .map(StoreSubscription::expiryTime)
                        .orElseThrow(() -> new StoreDeferRejectedException("연기 뒤 구독을 다시 읽지 못했다", null));
            }
            log.info("Play 구독 결제일 연기 by={} newExpiry={}", by, newExpiry);
            return newExpiry;
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() >= 500 || e.getStatusCode() == 429 || e.getStatusCode() == 409) {
                // 409 = etag 불일치(그 사이 상태가 바뀜) — 다시 하면 된다.
                throw new StoreBillingUnavailableException("Play 결제일 연기 일시 실패 status=" + e.getStatusCode(), e);
            }
            throw new StoreDeferRejectedException("Play 가 결제일 연기를 거절 status=" + e.getStatusCode()
                    + " " + messageOf(e), e);
        } catch (IOException e) {
            throw new StoreBillingUnavailableException("Play 결제일 연기 중 네트워크 오류", e);
        }
    }

    /**
     * {@code purchases.subscriptionsv2.cancel} — 다음 결제를 멈춘다(결제한 기간은 유지, 환불 없음).
     * {@code DEVELOPER_REQUESTED_STOP_PAYMENTS}: 결제자가 탈퇴해서 개발자가 멈추는 것이라 Play 스토어에서 "복원" 을 막는다
     * (다시 쓰려면 앱에서 새로 결제).
     *
     * <p>Play 가 4xx 로 거절하면 <b>구독을 다시 읽어 정말 끝났는지 확인한 뒤에만</b> 성공으로 본다(LAUNCH_REVIEW B14).
     * 예전에는 5xx·429 가 아닌 4xx 를 전부 "이미 해지·만료" 로 삼켰다 — 서비스 계정 권한이 없는 403, 인증 401 까지
     * "해지 완료" 로 끝나 탈퇴자에게 다음 해에도 청구됐다. 끝난 상태(해지·만료·환불, 너무 오래돼 조회 불가 410)가
     * 아니면 {@link IllegalStateException} 으로 실패를 알린다.
     */
    @Override
    public void cancelRenewal(Store store, String purchaseToken) {
        try {
            CancelSubscriptionPurchaseRequest request = new CancelSubscriptionPurchaseRequest()
                    .setCancellationContext(new CancellationContext()
                            .setCancellationType("DEVELOPER_REQUESTED_STOP_PAYMENTS"));
            publisher.purchases().subscriptionsv2().cancel(packageName, purchaseToken, request).execute();
            log.info("Play 구독 자동 갱신 해지");
        } catch (GoogleJsonResponseException e) {
            int code = e.getStatusCode();
            if (code >= 500 || code == 429) {
                throw new StoreBillingUnavailableException("Play 구독 해지 일시 실패 status=" + code, e);
            }
            if (code == 410 || alreadyEnded(purchaseToken)) {
                log.info("Play 가 해지를 거절했지만 구독은 이미 끝났다 status={} — 할 일 없음", code);
                return;
            }
            throw new IllegalStateException("Play 가 구독 해지를 거절 status=" + code + " " + messageOf(e), e);
        } catch (IOException e) {
            throw new StoreBillingUnavailableException("Play 구독 해지 중 네트워크 오류", e);
        }
    }

    /**
     * 해지 거절 뒤 확인: 구독이 이미 해지·만료·환불됐거나 너무 오래돼 조회되지 않으면(410, "no longer valid" 400) true.
     * 404(토큰을 모름 — 패키지명·자격증명이 어긋났을 수도)나 조회 실패는 "모른다" 라 false.
     */
    private boolean alreadyEnded(String purchaseToken) {
        try {
            SubscriptionPurchaseV2 purchase = publisher.purchases().subscriptionsv2()
                    .get(packageName, purchaseToken).execute();
            // 원문 상태로 본다 — mapState 는 모르는 값을 EXPIRED 로 접어서 "끝났다" 로 오인할 수 있다.
            String state = purchase.getSubscriptionState();
            return "SUBSCRIPTION_STATE_CANCELED".equals(state) || "SUBSCRIPTION_STATE_EXPIRED".equals(state);
        } catch (GoogleJsonResponseException e) {
            return isDefinitelyInvalid(e) && e.getStatusCode() != 404;
        } catch (IOException e) {
            return false;
        }
    }

    /** 404(없음)·410(사라짐), 또는 토큰이 더는 유효하지 않다는 400 → 재시도해도 소용없는 확정 무효. */
    private static boolean isDefinitelyInvalid(GoogleJsonResponseException e) {
        int code = e.getStatusCode();
        if (code == 404 || code == 410) {
            return true;
        }
        return code == 400 && messageOf(e).contains("no longer valid");
    }

    private static String messageOf(GoogleJsonResponseException e) {
        String m = e.getDetails() != null && e.getDetails().getMessage() != null
                ? e.getDetails().getMessage() : e.getMessage();
        return m == null ? "" : m.toLowerCase(Locale.ROOT);
    }

    private static AndroidPublisher buildPublisher(String credentialsPath, String applicationName) {
        try (InputStream keyStream = Files.newInputStream(Path.of(credentialsPath))) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(keyStream)
                    .createScoped(List.of(AndroidPublisherScopes.ANDROIDPUBLISHER));
            return new AndroidPublisher.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(applicationName)
                    .build();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Google Play 결제 검증 자격증명을 읽지 못했습니다: " + credentialsPath, e);
        }
    }

    private static String require(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 가 필요합니다 (app.plan.billing.gateway=google)");
        }
        return value;
    }
}
