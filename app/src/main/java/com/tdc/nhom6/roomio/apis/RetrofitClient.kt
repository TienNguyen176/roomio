package com.tdc.nhom6.roomio.apis

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    fun createCloudinaryClient(cloudName: String): CloudinaryApi {
        // Tăng timeout lên 60 giây
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // Thời gian kết nối
            .readTimeout(60, TimeUnit.SECONDS)    // Thời gian đọc phản hồi
            .writeTimeout(60, TimeUnit.SECONDS)   // Thời gian ghi dữ liệu (upload)
            .retryOnConnectionFailure(true)       // Tự động thử lại khi mạng chập chờn
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.cloudinary.com/v1_1/$cloudName/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudinaryApi::class.java)
    }

    fun createFCMClient(): FCMApi {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return Retrofit.Builder()
            .baseUrl("http://langlasky.ddns.net/fcm_roomio_server/") // URL VPS Windows
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FCMApi::class.java)
    }
}