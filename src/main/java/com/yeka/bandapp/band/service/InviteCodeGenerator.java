package com.yeka.bandapp.band.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 초대코드 생성기. 8자 대문자 영숫자, 혼동 문자({@code 0/O}, {@code 1/I}) 제외.
 * 사람이 손으로 옮겨 적을 수 있어야 해서 소문자와 헷갈리는 글자를 뺐다.
 *
 * <p>유일성은 DB 유니크 인덱스({@code ux_band_invites_code})가 최종 보장한다.
 * 호출 측이 충돌 시 재시도한다.
 */
@Component
public class InviteCodeGenerator {

    /** A–Z 에서 I, O 를 뺀 24자 + 2–9 의 8자 = 32자. */
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
