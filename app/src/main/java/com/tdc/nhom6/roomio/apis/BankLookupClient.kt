// File: app/src/main/java/com/tdc/nhom6/roomio/apis/BankLookupClient.kt
package com.tdc.nhom6.roomio.apis

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object BankLookupClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.banklookup.net/") // Base URL thật của BankLookup API
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: BankLookupApi = retrofit.create(BankLookupApi::class.java)
}
