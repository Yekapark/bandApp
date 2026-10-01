package com.yeka.bandule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
    }

    /**
     * `bandule/system` 채널 — 앱(Dart)이 휴대폰의 이 앱 알림 설정 화면을 열 때 쓴다(`SystemSettings`).
     * 알림 권한을 두 번 거절하면 권한 창이 더 뜨지 않아서, 켜려면 설정 화면으로 보내야 한다.
     */
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "bandule/system")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "openNotificationSettings" -> result.success(openNotificationSettings())
                    else -> result.notImplemented()
                }
            }
    }

    private fun openNotificationSettings(): Boolean = try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    /**
     * 알림 채널을 만든다.
     *
     * 이게 없으면 안드로이드가 FCM 기본 채널(`fcm_fallback_notification_channel`)로 알림을
     * 띄우고, 휴대폰 설정의 앱 알림 목록에 **"기타"** 라는 이름으로 나온다. 사용자가 무슨
     * 알림인지 알 수 없고, 끄고 싶어도 무엇을 끄는지 모른다.
     *
     * 채널을 **하나만** 둔다. 일정·정산·공지로 나누면 휴대폰 설정에 토글이 여러 개 생기는데,
     * 앱 안의 알림 설정 화면이 이미 "무엇을 보낼지" 를 정하고 있어서 같은 선택이 두 군데로
     * 갈린다. 나중에 정말 필요해지면 그때 나눈다(서버도 종류별 channel_id 를 실어야 한다).
     *
     * 채널 id 는 매니페스트의 `default_notification_channel_id` 와 같아야 한다 — 그래야
     * 앱이 꺼져 있을 때 온 알림도 이 채널로 뜬다.
     */
    private fun createNotificationChannel() {
        // 채널은 Android 8(Oreo)부터다. 그 아래는 알림 설정이 앱 단위라 할 일이 없다.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "합주 알림",
            // 소리와 함께 뜬다. 합주 시작 리마인더를 놓치면 알림의 의미가 없다.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "합주 일정, 참석 요청, 정산 안내를 알려드려요."
        }
        // 이미 있으면 덮어쓰지 않는다 — 사용자가 바꾼 설정(소리 끄기 등)을 유지한다.
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "bandule_default"
    }
}
