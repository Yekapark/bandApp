package com.yeka.bandapp.plan.gateway.google;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 해지 거절(4xx)을 "이미 끝났다" 로 삼키는 것은 스토어에서 끝난 것이 확인될 때뿐이다(LAUNCH_REVIEW B14).
 * 권한 없음 403 등을 성공으로 보면 탈퇴자에게 계속 청구된다.
 */
class GooglePlayBillingGatewayCancelTest {

    private final AndroidPublisher publisher = mock(AndroidPublisher.class, Answers.RETURNS_DEEP_STUBS);
    private final GooglePlayBillingGateway gateway = new GooglePlayBillingGateway(publisher, "com.example");

    private static GoogleJsonResponseException http(int status) {
        return new GoogleJsonResponseException(
                new HttpResponseException.Builder(status, "x", new HttpHeaders()), null);
    }

    private void cancelFails(int status) throws Exception {
        when(publisher.purchases().subscriptionsv2().cancel(anyString(), anyString(), any()).execute())
                .thenThrow(http(status));
    }

    private void storeSays(String state) throws Exception {
        when(publisher.purchases().subscriptionsv2().get(anyString(), anyString()).execute())
                .thenReturn(new SubscriptionPurchaseV2().setSubscriptionState(state));
    }

    @Test
    void 권한_없음_403_인데_구독이_살아_있으면_실패() throws Exception {
        cancelFails(403);
        when(publisher.purchases().subscriptionsv2().get(anyString(), anyString()).execute())
                .thenThrow(http(403));

        assertThatThrownBy(() -> gateway.cancelRenewal(Store.GOOGLE_PLAY, "t"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("403");
    }

    @Test
    void 결제_보류_중인_구독의_거절은_실패() throws Exception {
        cancelFails(400);
        storeSays("SUBSCRIPTION_STATE_ON_HOLD");

        assertThatThrownBy(() -> gateway.cancelRenewal(Store.GOOGLE_PLAY, "t"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 토큰을_모르는_404_는_실패() throws Exception {
        cancelFails(404);
        when(publisher.purchases().subscriptionsv2().get(anyString(), anyString()).execute())
                .thenThrow(http(404));

        assertThatThrownBy(() -> gateway.cancelRenewal(Store.GOOGLE_PLAY, "t"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 이미_해지_만료된_구독의_거절은_성공() throws Exception {
        cancelFails(400);
        storeSays("SUBSCRIPTION_STATE_CANCELED");
        assertThatCode(() -> gateway.cancelRenewal(Store.GOOGLE_PLAY, "t")).doesNotThrowAnyException();

        storeSays("SUBSCRIPTION_STATE_EXPIRED");
        assertThatCode(() -> gateway.cancelRenewal(Store.GOOGLE_PLAY, "t")).doesNotThrowAnyException();
    }

    @Test
    void 너무_오래돼_사라진_410_은_성공() throws Exception {
        cancelFails(410);
        assertThatCode(() -> gateway.cancelRenewal(Store.GOOGLE_PLAY, "t")).doesNotThrowAnyException();
    }

    /** QA BILL-21 — 인증 401·잘못된 요청 400·충돌 409 거절은, 스토어에서 끝난 게 확인되지 않으면 실패(해지 성공으로 보지 않음). */
    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 409})
    void 확인되지_않은_4xx_거절은_구독이_살아_있으면_실패(int status) throws Exception {
        cancelFails(status);
        storeSays("SUBSCRIPTION_STATE_ACTIVE");

        assertThatThrownBy(() -> gateway.cancelRenewal(Store.GOOGLE_PLAY, "t"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(String.valueOf(status));
    }

    /** QA BILL-21 — 4xx 뒤 확인 조회마저 실패하면 "모른다" 라 실패로 남긴다(재시도·점검 대상). */
    @ParameterizedTest
    @ValueSource(ints = {401, 409})
    void 거절_뒤_확인_조회도_실패하면_실패(int status) throws Exception {
        cancelFails(status);
        when(publisher.purchases().subscriptionsv2().get(anyString(), anyString()).execute())
                .thenThrow(new java.io.IOException("network"));

        assertThatThrownBy(() -> gateway.cancelRenewal(Store.GOOGLE_PLAY, "t"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** QA BILL-21 — 요청 과다 429·서버 5xx 는 일시 실패(나중에 다시 시도). */
    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503})
    void 요청_과다와_서버_오류는_일시_실패(int status) throws Exception {
        cancelFails(status);
        assertThatThrownBy(() -> gateway.cancelRenewal(Store.GOOGLE_PLAY, "t"))
                .isInstanceOf(StoreBillingUnavailableException.class);
    }

    @Test
    void 서버_오류는_일시_실패() throws Exception {
        cancelFails(503);
        assertThatThrownBy(() -> gateway.cancelRenewal(Store.GOOGLE_PLAY, "t"))
                .isInstanceOf(StoreBillingUnavailableException.class);
    }
}
