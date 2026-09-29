# 카카오맵 SDK (kakao_map_sdk) — 네이티브 벡터맵 클래스는 난독화하면 로드에 실패한다.
-keep class com.kakao.vectormap.** { *; }
-keep interface com.kakao.vectormap.**

# 카카오 로그인 SDK (kakao_flutter_sdk_user) — Gson 으로 API 응답을 역직렬화하므로
# 모델 필드가 난독화되면 로그인 응답 파싱이 깨진다.
-keep class com.kakao.sdk.**.model.* { <fields>; }
-keep class com.kakao.sdk.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class com.google.gson.stream.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions

# 영상 압축(v_video_compressor)은 AndroidX Media3 를 쓰고, Media3 가 자기 소비자 규칙을 싣고 온다 — 따로 둘 것 없음.
# (예전 video_compress 의 트랜스코더 keep 규칙은 2026-09-29 교체 때 뺐다.)
