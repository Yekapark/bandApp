package com.yeka.bandapp.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청의 클라이언트 IP. 운영에서는 Nginx 리버스 프록시 뒤이므로 {@code X-Forwarded-For}의
 * 첫 홉을 우선한다. 헤더가 없으면 소켓 주소를 쓴다.
 *
 * <p>참고: {@code X-Forwarded-For}는 클라이언트가 위조할 수 있다. 신뢰 경계(프록시)가
 * 이 헤더를 재작성한다는 전제이며, 레이트리밋 키 용도로만 쓴다(인가 판단에는 쓰지 않는다).
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
