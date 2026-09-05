package com.yeka.bandapp.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청의 클라이언트 IP. 레이트리밋 버킷 키로만 쓴다(인가 판단에는 쓰지 않는다).
 *
 * <p>소켓 주소만 본다. {@code X-Forwarded-For}를 직접 읽지 않는 것이 핵심이다 —
 * 그 헤더는 클라이언트가 마음대로 넣을 수 있어서, 헤더 값을 신뢰하면 요청마다 다른 IP를
 * 위장해 모든 레이트리밋(로그인 브루트포스·초대코드 대입·업로드 스팸)을 무력화할 수 있다.
 *
 * <p>프록시 뒤에서의 실제 클라이언트 IP 복원은 톰캣 {@code RemoteIpValve}가 담당한다
 * ({@code server.forward-headers-strategy: NATIVE}, {@code application-prod.yml}).
 * 밸브는 <b>신뢰 프록시(내부 대역)에서 들어온</b> 요청의 XFF만 해석해
 * {@code getRemoteAddr()} 자체를 실제 클라이언트 IP로 바꿔 준다. 외부에서 직접 온 요청의
 * 위조 XFF는 무시된다. Nginx도 XFF를 이어붙이지 않고 실제 피어 주소로 덮어쓴다
 * ({@code deploy/nginx/templates/app.conf.template}).
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
