package com.tdc.nhom6.roomio.apis

import com.tdc.nhom6.roomio.models.VietQRBankResponse
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET


object VietQRClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.vietqr.io/") // base URL VietQR
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: VietQRApi = retrofit.create(VietQRApi::class.java)
}

interface VietQRApi {
    @GET("v2/banks")
    fun getBanks(): Call<VietQRBankResponse>
}