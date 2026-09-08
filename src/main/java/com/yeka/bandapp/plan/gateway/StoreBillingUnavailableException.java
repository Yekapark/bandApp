package com.yeka.bandapp.plan.gateway;

import com.yeka.bandapp.plan.service.StoreWebhookRetryException;

/**
 * 스토어에 물어봤는데 <b>일시적으로</b> 대답을 못 받았다(네트워크·5xx·인증 갱신 실패 등).
 * "구매가 없다"({@code fetch} 가 {@code Optional.empty()})와는 다르다 — 그건 확정된 무효다.
 *
 * <p>{@link StoreWebhookRetryException} 를 상속하므로 웹훅 컨트롤러의 재시도(503) 처리에 그대로 걸린다.
 * verify 경로에서는 {@code StoreSubscriptionService} 가 잡아 402(잠시 후 재시도)로 바꾼다.
 */
public class StoreBillingUnavailableException extends StoreWebhookRetryException {
    public StoreBillingUnavailableException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
