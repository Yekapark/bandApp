allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

// firebase_app_distribution 은 compileSdk 30 으로 배포됐는데, 그게 끌고 오는
// androidx 라이브러리들이 34 이상을 요구해서 빌드가 멈춘다. 플러그인 쪽 설정 실수라
// 우리가 고칠 수 없으니 여기서 덮어쓴다.
//
// compileSdk 는 "어느 API 까지 쓸 수 있나" 일 뿐이고, 설치 가능 기기(minSdk)나 동작
// 방식(targetSdk)과는 무관하다. 그래서 이걸 올려도 앱이 도는 방식은 달라지지 않는다.
//
// 이 플러그인 하나에만 건다 — 전체 subprojects 에 걸면 다른 플러그인이 낡은 설정으로
// 도는 것을 가려 버린다.
subprojects {
    if (project.name == "firebase_app_distribution_android") {
        afterEvaluate {
            extensions.findByName("android")?.let { android ->
                (android as com.android.build.gradle.BaseExtension)
                    .compileSdkVersion(36)
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
