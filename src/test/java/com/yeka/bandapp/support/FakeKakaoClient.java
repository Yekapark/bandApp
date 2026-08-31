package com.yeka.bandapp.support;

import com.yeka.bandapp.user.kakao.KakaoClient;
import com.yeka.bandapp.user.kakao.KakaoTokenInfo;
import com.yeka.bandapp.user.kakao.KakaoUserInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 프로그래머블 카카오 스텁. 카카오 응답의 도메인 규칙("이메일이 없을 수 있음" 등)을 한곳에 응집시켜
 * 여러 테스트가 재사용하고, 이 클래스 자체가 카카오 계약 문서 역할을 한다.
 * {@code app.kakao.app-id=999999}({@link IntegrationTestSupport})와 맞춘 기본 appId를 돌려준다.
 */
public class FakeKakaoClient implements KakaoClient {

    private String nextId = "kakao-000001";
    private String nextEmail = null;
    private String nextNickname = "카카오테스터";
    private Long nextAppId = 999999L;
    private RuntimeException fetchFailure;
    private RuntimeException unlinkFailure;
    private final List<String> unlinkedIds = new ArrayList<>();

    public void reset() {
        nextId = "kakao-000001";
        nextEmail = null;
        nextNickname = "카카오테스터";
        nextAppId = 999999L;
        fetchFailure = null;
        unlinkFailure = null;
        unlinkedIds.clear();
    }

    public void willReturnUser(String id, String email, String nickname) {
        this.nextId = id;
        this.nextEmail = email;
        this.nextNickname = nickname;
    }

    public void willReturnAppId(Long appId) {
        this.nextAppId = appId;
    }

    public void willFailFetchWith(RuntimeException e) {
        this.fetchFailure = e;
    }

    public void willFailUnlinkWith(RuntimeException e) {
        this.unlinkFailure = e;
    }

    public List<String> unlinkedIds() {
        return unlinkedIds;
    }

    @Override
    public KakaoTokenInfo fetchTokenInfo(String kakaoAccessToken) {
        if (fetchFailure != null) {
            throw fetchFailure;
        }
        return new KakaoTokenInfo(nextId, nextAppId);
    }

    @Override
    public KakaoUserInfo fetchUserInfo(String kakaoAccessToken) {
        if (fetchFailure != null) {
            throw fetchFailure;
        }
        return new KakaoUserInfo(nextId, nextEmail, nextNickname);
    }

    @Override
    public void unlink(String socialId) {
        if (unlinkFailure != null) {
            throw unlinkFailure;
        }
        unlinkedIds.add(socialId);
    }
}
