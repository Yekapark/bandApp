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
