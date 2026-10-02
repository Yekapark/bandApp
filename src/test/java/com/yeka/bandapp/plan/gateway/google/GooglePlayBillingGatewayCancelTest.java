package com.yeka.bandapp.plan.gateway.google;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingUnavailableException;
import org.junit.jupiter.api.Test;
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

    @Test
    void 서버_오류는_일시_실패() throws Exception {
        cancelFails(503);
        assertThatThrownBy(() -> gateway.cancelRenewal(Store.GOOGLE_PLAY, "t"))
                .isInstanceOf(StoreBillingUnavailableException.class);
    }
}
