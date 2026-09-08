/**
 * 요금제 도메인 (FREE/PREMIUM, 미디어 보관기간, 스토어 인앱결제). {@code StoreBillingGateway} 로 스토어
 * 구독을 조회하고, RTDN 웹훅으로 갱신·해지·환불을 반영한다. 다른 도메인은 이 패키지의 서비스 레이어를
 * 통해서만 참조한다.
 */
package com.yeka.bandapp.plan;
