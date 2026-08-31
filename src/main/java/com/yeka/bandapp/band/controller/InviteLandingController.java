package com.yeka.bandapp.band.controller;

import com.yeka.bandapp.band.DeeplinkProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 초대 딥링크의 무인증 웹 표면.
 *
 * <ul>
 *   <li>{@code GET /invite/{code}} — 앱 설치 시 앱을 열고, 미설치 시 스토어로 유도하는 랜딩 페이지</li>
 *   <li>{@code GET /.well-known/apple-app-site-association} — iOS Universal Link 검증</li>
 *   <li>{@code GET /.well-known/assetlinks.json} — Android App Link 검증</li>
 * </ul>
 *
 * <p>실제 링크 라우팅은 OS 가 위 검증 파일을 보고 처리한다. 이 컨트롤러는 파일 제공과,
 * 검증이 안 되는 브라우저(데스크톱 등)를 위한 폴백 페이지만 담당한다.
 */
@RestController
public class InviteLandingController {

    private static final Pattern CODE = Pattern.compile("[A-Z2-9]{8}");

    private final DeeplinkProperties properties;

    public InviteLandingController(DeeplinkProperties properties) {
        this.properties = properties;
    }

    @GetMapping(value = "/invite/{code}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> landing(@PathVariable String code) {
        if (!CODE.matcher(code).matches()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(renderLanding(code));
    }

    @GetMapping(value = "/.well-known/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> appleAppSiteAssociation() {
        return Map.of("applinks", Map.of(
                "apps", List.of(),
                "details", List.of(Map.of(
                        "appID", properties.iosAppId(),
                        "paths", List.of("/invite/*")))));
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> assetLinks() {
        return List.of(Map.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", Map.of(
                        "namespace", "android_app",
                        "package_name", properties.androidPackage(),
                        "sha256_cert_fingerprints", properties.androidSha256CertFingerprints())));
    }

    private String renderLanding(String code) {
        String scheme = properties.scheme();
        String iosStore = properties.iosAppStoreUrl();
        String androidStore = properties.androidPlayStoreUrl();
        // code 는 [A-Z2-9]{8} 로 검증돼 인젝션 여지가 없다. 나머지 값은 서버 설정이라 신뢰한다.
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>밴드 초대</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", sans-serif;
                           max-width: 420px; margin: 12vh auto; padding: 0 24px; text-align: center; color: #1a1a1a; }
                    .code { font-size: 1.6rem; letter-spacing: .18em; font-weight: 700; margin: .5rem 0 1.5rem; }
                    a.btn { display: block; margin: .6rem 0; padding: .9rem 1rem; border-radius: 12px;
                            background: #2b2b2b; color: #fff; text-decoration: none; font-weight: 600; }
                    p.hint { color: #666; font-size: .9rem; margin-top: 2rem; }
                  </style>
                </head>
                <body>
                  <h1>밴드 초대</h1>
                  <p>초대 코드</p>
                  <div class="code">%CODE%</div>
                  <p>앱에서 초대를 여는 중…</p>
                  <a class="btn" id="ios" href="%IOS_STORE%">App Store에서 앱 받기</a>
                  <a class="btn" id="android" href="%ANDROID_STORE%">Google Play에서 앱 받기</a>
                  <p class="hint">앱이 열리지 않으면 위 버튼으로 설치한 뒤 코드를 입력하세요.</p>
                  <script>
                    (function () {
                      var code = "%CODE%";
                      var ua = navigator.userAgent || "";
                      var isIOS = /iPad|iPhone|iPod/.test(ua);
                      var isAndroid = /Android/.test(ua);
                      if (isIOS) { var a = document.getElementById("android"); if (a) a.hidden = true; }
                      if (isAndroid) { var i = document.getElementById("ios"); if (i) i.hidden = true; }
                      var start = Date.now();
                      // 앱이 설치돼 있으면 커스텀 스킴이 앱을 연다. 안 열리면 아래 타이머가 스토어로 보낸다.
                      window.location.href = "%SCHEME%://invite/" + code;
                      setTimeout(function () {
                        if (Date.now() - start < 2000) {
                          if (isAndroid) window.location.href = "%ANDROID_STORE%";
                          else if (isIOS) window.location.href = "%IOS_STORE%";
                        }
                      }, 1200);
                    })();
                  </script>
                </body>
                </html>
                """
                .replace("%CODE%", code)
                .replace("%SCHEME%", scheme)
                .replace("%IOS_STORE%", iosStore)
                .replace("%ANDROID_STORE%", androidStore);
    }
}
