package com.tdc.nhom6.roomio.repositories

import android.content.Context
import com.tdc.nhom6.roomio.apis.RetrofitClient
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

    // Gửi notification tới 1 token
    suspend fun sendNotification(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): FCMResponseModel? =
        withContext(Dispatchers.IO) {
            val payload = mutableMapOf<String, Any>(
                "token" to token,
                "title" to title,
                "body" to body,
                "data" to data
            )
            val response = api.sendNotification(payload)
            if (response.isSuccessful) response.body() else null
        }
}
