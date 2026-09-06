import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// 카카오 네이티브 앱 키. 로그인 리다이렉트 스킴(kakao{키}://oauth)이 매니페스트에 박혀야 해서
// 빌드 시점에 필요하다 — 저장소에 키를 커밋하지 않도록 local.properties 에서만 읽는다.
// android/local.properties 에 `kakao.appKey=...` 한 줄. 없으면 빈 값이라 카카오계정 로그인만 안 된다.
val kakaoAppKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("kakao.appKey") ?: ""

// FCM 은 google-services.json 이 있어야 동작한다. 이 파일은 Firebase 콘솔에서 받아
// android/app/ 에 넣으며, 저장소에는 커밋하지 않는다(개발용·운영용 프로젝트가 다르다 —
// android/.gitignore 참조). 없으면 플러그인을 아예 적용하지 않아서 빌드는 그대로 되고
// 푸시만 조용히 비활성화된다 — 카카오 키가 없을 때와 같은 방식(PushService 주석 참조).
val googleServicesJson = file("google-services.json")
if (googleServicesJson.exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle(
        "[bandule] android/app/google-services.json 이 없다 — FCM 푸시 비활성화 상태로 빌드한다. " +
            "켜려면 docs/LAUNCH_CHECKLIST.md 7-B 단계 참조.",
    )
}

android {
    namespace = "com.yeka.bandule"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // 스토어에 올라가는 최종 앱 ID. 한 번 게시하면 바꿀 수 없다(바꾸면 다른 앱이 된다).
        applicationId = "com.yeka.bandule"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        // 카카오맵 SDK 요건: Android 6.0(API 23) 이상.
        minSdk = maxOf(flutter.minSdkVersion, 23)
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        // firebase_app_distribution 플러그인의 안드로이드 모듈은 두 갈래로 갈라져 있다.
        // 우리 앱에는 그런 구분이 없어서, 지정해 주지 않으면 Gradle 이 어느 쪽인지 못 고르고
        // 빌드가 멈춘다.
        //
        // **이름과 내용이 반대라 헷갈린다** (플러그인 android/build.gradle:51-63):
        //   production → firebase-appdistribution-api  = 아무것도 안 하는 껍데기
        //   staging    → firebase-appdistribution      = 진짜 SDK
        //
        // 구글이 "스토어에 올리는 빌드에는 껍데기를 넣어라" 는 뜻으로 지은 이름이다.
        // 우리는 테스터 빌드를 만들고 있으므로 `staging` 이 맞다. `production` 으로 두면
        // 오류도 없이 조용히 아무 일도 일어나지 않는다 — 실제로 한 번 그렇게 나갔다.
        //
        // 스토어로 갈 때는 이 의존성 자체를 걷어낸다.
        missingDimensionStrategy("default", "staging")

        // AndroidManifest 의 카카오 로그인 리다이렉트 스킴에 꽂힌다.
        manifestPlaceholders["kakaoAppKey"] = kakaoAppKey
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
