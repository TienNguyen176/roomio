package com.tdc.nhom6.roomio.apis

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object VietQRClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.vietqr.io/") // base URL VietQR
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: VietQRApi = retrofit.create(VietQRApi::class.java)
}