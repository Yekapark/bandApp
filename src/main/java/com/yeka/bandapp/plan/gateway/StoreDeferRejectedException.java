package com.yeka.bandapp.plan.gateway;

/**
 * 스토어가 결제일 연기(defer)를 <b>확정적으로</b> 거절했다 — 구독 상태가 연기할 수 없는 상태이거나(만료·환불 등)
 * 요청이 규칙에 맞지 않을 때. 다시 해도 소용없으므로 {@link StoreBillingUnavailableException}(일시 장애)과 구분한다.
 */
public class StoreDeferRejectedException extends RuntimeException {

    public StoreDeferRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
