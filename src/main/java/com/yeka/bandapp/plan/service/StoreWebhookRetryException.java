package com.yeka.bandapp.plan.service;

/**
 * RTDN 웹훅을 지금은 처리할 수 없으니 Pub/Sub 가 <b>재전송</b>해 달라는 신호.
 * 예: 갱신 알림이 왔는데 스토어 조회가 일시적으로 실패 — 그냥 삼키면 만료일이 연장되지 않아
 * 결제한 밴드가 만료 배치에 강등된다. 이 예외를 던지면 웹훅 컨트롤러가 5xx 를 주고 Pub/Sub 가
 * 재시도한다. 멱등 기록({@code processed_store_events})은 이때 남기지 않는다.
 */
public class StoreWebhookRetryException extends RuntimeException {
    public StoreWebhookRetryException(String message) {
        super(message);
    }
}
