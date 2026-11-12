package com.hanhyo.shuttle.parent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hanhyo.shuttle.net.Api
import org.json.JSONObject
import java.util.concurrent.Executors

object Push {
    private val worker = Executors.newSingleThreadExecutor()
    private val lock = Any()
    fun prefs(context: Context) = context.getSharedPreferences("guardian-account", 0)
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("dropoff", "하원 출발 · 하차 알림", NotificationManager.IMPORTANCE_HIGH))
    }
    fun register(context: Context) {
        val app = context.applicationContext
        val prefs = prefs(app)
        if (!prefs.contains("token")) return
        if (FirebaseApp.getApps(app).isEmpty()) {
            prefs.edit().putString("pushStatus", "Firebase 미설정 · 앱에서 기록을 조회할 수 있습니다.").apply()
            return
        }
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        FirebaseMessaging.getInstance().token.addOnSuccessListener { upload(app, it) }
            .addOnFailureListener { prefs.edit().putString("pushStatus", "푸시 등록 실패 · 인터넷 연결 후 새로고침해 주세요.").apply() }
    }
    fun upload(context: Context, pushToken: String) {
        worker.execute {
            synchronized(lock) {
                val prefs = prefs(context)
                val token = prefs.getString("token", null) ?: return@synchronized
                try {
                    val result = Api.request(prefs.getString("base", "")!!, "/api/v1/me/push-devices", token,
                        JSONObject().put("platform", "ANDROID").put("fcmToken", pushToken).put("appVersion", "0.2").put("osPermission", permission(context)))
                    prefs.edit().putString("pushToken", pushToken).putString("pushDeviceId", result.optString("id")).putString("pushStatus", "푸시 등록 완료").commit()
                } catch (_: Exception) {
                    prefs.edit().putString("pushStatus", "푸시 등록 대기 · 서버 연결 후 새로고침해 주세요.").commit()
                }
            }
        }
    }
    private fun permission(context: Context) = if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
    fun logout(context: Context) = synchronized(lock) {
        val prefs = prefs(context)
        val base = prefs.getString("base", "")!!
        val deviceId = prefs.getString("pushDeviceId", "")
        if (!deviceId.isNullOrBlank()) runCatching { Api.request(base, "/api/v1/me/push-devices/$deviceId", prefs.getString("token", "")!!, method = "DELETE") }
        Api.request(base, "/api/v1/auth/logout", data = JSONObject().put("refreshToken", prefs.getString("refreshToken", "")))
        check(prefs.edit().clear().putString("base", base).commit())
        context.getSystemService(NotificationManager::class.java).cancelAll()
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseMessaging.getInstance().isAutoInitEnabled = false
    }
}

class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Push.upload(applicationContext, token)
    override fun onMessageReceived(message: RemoteMessage) {
        if (!Push.prefs(this).contains("token")) return
        Push.channel(this)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = message.data["notificationId"] ?: message.data["eventId"] ?: message.messageId ?: return
        val pending = PendingIntent.getActivity(this, id.hashCode(), Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).notify(id, 1,
            NotificationCompat.Builder(this, "dropoff").setSmallIcon(R.drawable.ic_shuttle)
                .setContentTitle(message.notification?.title ?: "통학메이트")
                .setContentText(message.notification?.body ?: "새 운행 기록이 있습니다.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.notification?.body ?: "새 운행 기록이 있습니다."))
                .setAutoCancel(true).setOnlyAlertOnce(true).setContentIntent(pending).build())
    }
}
