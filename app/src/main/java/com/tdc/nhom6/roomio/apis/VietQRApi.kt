package com.tdc.nhom6.roomio.apis

import com.tdc.nhom6.roomio.models.VietQRBankResponse
import retrofit2.Call
import retrofit2.http.GET

interface VietQRApi {
    @GET("v2/banks")
    fun getBanks(): Call<VietQRBankResponse>
}