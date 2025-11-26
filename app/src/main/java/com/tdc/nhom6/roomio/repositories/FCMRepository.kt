package com.tdc.nhom6.roomio.repositories

import android.content.Context
import android.util.Log
import com.tdc.nhom6.roomio.apis.FCMApi
import com.tdc.nhom6.roomio.apis.RetrofitClient
import com.tdc.nhom6.roomio.models.FCMNotification
import com.tdc.nhom6.roomio.models.FCMResponseModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FCMRepository(context: Context) {

    private val api = RetrofitClient.createFCMClient()

    // Gửi token lên server
    suspend fun registerToken(token: String, userId: String? = null, platform: String = "android"): Boolean =
        withContext(Dispatchers.IO) {
            val body = mutableMapOf<String, String>()
            body["token"] = token
            body["platform"] = platform
            userId?.let { body["user_id"] = it }

            val response = api.registerToken(body)
            response.isSuccessful
        }

    suspend fun notifyUserViaFCM(
        fcmApi: FCMApi,
        userId: String,
        title: String,
        message: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        return try {
            val body = mapOf(
                "user_id" to userId,
                "title" to title,
                "message" to message,
                "data" to data
            )

            val response = fcmApi.sendNotification(FCMNotification(userId, title, message, data))
            if (response.isSuccessful) {
                Log.d("NotifyServer", "Success: ${response.body()}")
                true
            } else {
                Log.e("NotifyServer", "Error: ${response.code()} ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e("NotifyServer", "Exception: ${e.message}")
            false
        }
    }
}
