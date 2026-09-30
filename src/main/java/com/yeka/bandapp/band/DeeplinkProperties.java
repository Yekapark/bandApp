package com.yeka.bandapp.band;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 초대 딥링크 설정. {@code app.deeplink.*}.
 *
 * <p>{@code baseUrl}은 초대 링크와 랜딩 페이지가 서비스되는 공개 주소다.
 * iOS Universal Link / Android App Link 검증 파일에 들어가는 앱 식별자도 여기서 온다
 * (미설정 시 예시값 — 배포 전 실제 값으로 채운다).
 */
@ConfigurationProperties(prefix = "app.deeplink")
public record DeeplinkProperties(
        String baseUrl,
        String scheme,
        String iosAppId,
        String iosAppStoreUrl,
        String androidPackage,
        String androidPlayStoreUrl,
        List<String> androidSha256CertFingerprints
) {
    public DeeplinkProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (scheme == null || scheme.isBlank()) {
            scheme = "bandule";
        }
        if (iosAppId == null) {
            iosAppId = "";
        }
        // iPhone 앱이 아직 없다(2026-09-30). 예전 기본값은 가짜 주소(id0000000000)라 초대 페이지의
        // "App Store에서 앱 받기" 가 없는 앱으로 갔다. 비워 두면 랜딩 페이지가 "준비 중" 안내를 대신 보여 준다.
        if (iosAppStoreUrl == null || iosAppStoreUrl.isBlank() || iosAppStoreUrl.contains("id0000000000")) {
            iosAppStoreUrl = "";
        }
        if (androidPackage == null || androidPackage.isBlank()) {
            androidPackage = "com.yeka.bandule";
        }
        if (androidPlayStoreUrl == null || androidPlayStoreUrl.isBlank()) {
            androidPlayStoreUrl = "https://play.google.com/store/apps/details?id=com.yeka.bandule";
        }
        androidSha256CertFingerprints =
                androidSha256CertFingerprints == null ? List.of() : List.copyOf(androidSha256CertFingerprints);
    }

    /** iPhone 앱이 스토어에 있는가(주소가 설정됐는가). 없으면 초대 페이지가 설치 버튼 대신 안내를 띄운다. */
    public boolean iosAppAvailable() {
        return !iosAppStoreUrl.isBlank();
    }

    /** 공유용 초대 링크. 앱 미설치 시 {@code GET /invite/{code}} 랜딩 페이지가 응답한다. */
    public String inviteLink(String code) {
        return baseUrl + "/invite/" + code;
    }
}
