package com.yeka.bandapp.plan.gateway.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.yeka.bandapp.plan.config.StoreBillingProperties;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway;
import com.yeka.bandapp.plan.gateway.StoreBillingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
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

    public GooglePlayBillingGateway(StoreBillingProperties properties) {
        this.packageName = require(properties.googlePackageName(), "app.plan.billing.google-package-name");
        String credentialsPath = require(properties.googleCredentialsPath(), "app.plan.billing.google-credentials-path");
        this.publisher = buildPublisher(credentialsPath, properties.googleApplicationName());
        log.info("Google Play 결제 검증 활성화 package={}", packageName);
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
