package com.tdc.nhom6.roomio.apis

import com.tdc.nhom6.roomio.models.FCMNotification
import com.tdc.nhom6.roomio.models.FCMResponseModel
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface FCMApi {

    @POST("register_token.php")
    suspend fun registerToken(
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @POST("send_notification.php")
    suspend fun sendNotification(
        @Body body: FCMNotification
    ): Response<FCMResponseModel>
}
