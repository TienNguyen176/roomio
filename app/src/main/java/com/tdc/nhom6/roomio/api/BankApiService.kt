package com.tdc.nhom6.roomio.api

import com.tdc.nhom6.roomio.models.BankResponse
import retrofit2.Call
import retrofit2.http.GET

interface BankApiService {
    @GET("v2/banks")
    fun getBanks(): Call<BankResponse>
}
