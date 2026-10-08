package com.yeka.bandapp.plan.gateway.google;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingUnavailableException;
import com.yeka.bandapp.plan.gateway.StoreDeferRejectedException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * QA BILL-13 — Play 가 결제일 연기(defer)를 거절하면 쿠폰 사용을 되돌리는데({@code PlanCouponIntegrationTest}
 * {@code store_rejecting_the_deferral_rolls_the_coupon_back}), 그 분기는 게이트웨이가 Google 오류 응답을
 * {@link StoreDeferRejectedException}/{@link StoreBillingUnavailableException} 로 나눠 주는 데 달려 있다.
 * 어느 쪽이든 쿠폰은 되돌려지지만, 다른 예외로 새면 사용 기록만 남고 기간은 안 붙는다.
 */
class GooglePlayBillingGatewayDeferTest {

    private final AndroidPublisher publisher = mock(AndroidPublisher.class, Answers.RETURNS_DEEP_STUBS);
    private final GooglePlayBillingGateway gateway = new GooglePlayBillingGateway(publisher, "com.example");

    private void deferFails(int status) throws Exception {
        when(publisher.purchases().subscriptionsv2().get(anyString(), anyString()).execute())
                .thenReturn(new SubscriptionPurchaseV2().setEtag("e1"));
        when(publisher.purchases().subscriptionsv2().defer(anyString(), anyString(), any()).execute())
                .thenThrow(new GoogleJsonResponseException(
                        new HttpResponseException.Builder(status, "x", new HttpHeaders()), null));
    }

    /** 만료·환불·보류 등 연기할 수 없는 상태, 권한 없음, 모르는 토큰 — 확정 거절. */
    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 410})
    void 확정_거절은_거절_예외(int status) throws Exception {
        deferFails(status);
        assertThatThrownBy(() -> gateway.defer(Store.GOOGLE_PLAY, "t", Duration.ofDays(365)))
                .isInstanceOf(StoreDeferRejectedException.class);
    }

    /** etag 충돌 409·요청 과다 429·서버 5xx — 다시 하면 되는 일시 실패. */
    @ParameterizedTest
    @ValueSource(ints = {409, 429, 500, 503})
    void 일시_실패는_일시_장애_예외(int status) throws Exception {
        deferFails(status);
        assertThatThrownBy(() -> gateway.defer(Store.GOOGLE_PLAY, "t", Duration.ofDays(365)))
                .isInstanceOf(StoreBillingUnavailableException.class);
    }
}
