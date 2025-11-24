package com.tdc.nhom6.roomio.apis

import com.tdc.nhom6.roomio.models.BankLookupRequest
import com.tdc.nhom6.roomio.models.BankLookupResponse
import com.tdc.nhom6.roomio.models.VietQRBankResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface BankLookupApi {
    // GET danh sách ngân hàng
    @GET("api/banks")
    fun getBanks(): Call<VietQRBankResponse>


    // POST lookup tài khoản
    @POST("api/bank/id-lookup-prod")
    fun lookupAccount(
        @Header("x-api-key") apiKey: String,
        @Header("x-api-secret") apiSecret: String,
        @Body body: BankLookupRequest
    ): Call<BankLookupResponse>
}