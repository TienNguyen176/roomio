package com.tdc.nhom6.roomio.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.apis.FCMRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseService : FirebaseMessagingService() {

    private lateinit var fcmRepo: FCMRepository

    override fun onCreate() {
        super.onCreate()
        fcmRepo = FCMRepository(this)
    }

    // Gọi khi Firebase refresh token
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "New token: $token")

        // Lấy userId từ SharedPreferences
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("uid", null)

        if (userId != null) {
            // Gửi token mới lên server
            CoroutineScope(Dispatchers.IO).launch {
                val success = fcmRepo.registerToken(token, userId)
                Log.d("FCM_SERVICE", if (success) "Token sent" else "Token send failed")
            }
        }
    }

    // Gọi khi nhận notification từ server
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_SERVICE", "Message received: ${remoteMessage.data}")

        // Nếu notification có phần title/body
        val title = remoteMessage.notification?.title ?: "Thông báo"
        val body = remoteMessage.notification?.body ?: ""

        showNotification(title, body)
    }

    // Hiển thị notification
    private fun showNotification(title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "app_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notifications_none_24)
            .setAutoCancel(true)

        nm.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
    }
}
