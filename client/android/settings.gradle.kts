pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    // FCM. 실제 적용은 app/build.gradle.kts 에서 google-services.json 이 있을 때만 한다.
    id("com.google.gms.google-services") version "4.4.2" apply false
    // 앱 오류 기록(Crashlytics, LAUNCH_REVIEW U17). 난독화 매핑 파일 업로드와 빌드 ID — 없으면 SDK 가 시작하지 않는다.
    // 3.0.8: AGP 9 지원판. google-services 와 같이, json 이 있을 때만 app 에서 적용한다.
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
}

include(":app")
