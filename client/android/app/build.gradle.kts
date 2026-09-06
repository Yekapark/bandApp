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
// 릴리스 서명 키 정보. 없으면 null 이고, 그때는 디버그 키로 서명한다(아래 signingConfigs).
// 이 파일과 .jks 는 저장소에 절대 넣지 않는다 — 잃어버리면 그 앱은 영원히 업데이트할 수 없고,
// 새어 나가면 남이 우리 앱 행세를 할 수 있다.
val keystoreProperties: Properties? = rootProject.file("key.properties").let { f ->
    if (f.exists()) Properties().apply { f.inputStream().use { load(it) } } else null
}

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

        // AndroidManifest 의 카카오 로그인 리다이렉트 스킴에 꽂힌다.
        manifestPlaceholders["kakaoAppKey"] = kakaoAppKey
    }

    signingConfigs {
        // 릴리스 서명 키. `android/key.properties` 가 있으면 그것으로 서명하고, 없으면
        // 디버그 키로 넘어간다 — 키가 없는 PC 에서도 빌드는 그대로 되게.
        //
        // key.properties 도 .jks 파일도 **커밋하지 않는다**(android/.gitignore).
        // 만드는 법은 docs/LAUNCH_CHECKLIST.md 9단계.
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 키가 없으면 디버그 키로 서명한다. 그 빌드는 **스토어에 못 올린다** —
            // 테스터 배포(App Distribution)와 `flutter run --release` 에는 문제없다.
            signingConfig = if (keystoreProperties != null) {
                signingConfigs.getByName("release")
            } else {
                logger.lifecycle(
                    "[bandule] android/key.properties 가 없다 — 디버그 키로 서명한다. " +
                        "스토어 제출용이 아니면 그대로 두면 된다(docs/LAUNCH_CHECKLIST.md 9단계).",
                )
                signingConfigs.getByName("debug")
            }
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
