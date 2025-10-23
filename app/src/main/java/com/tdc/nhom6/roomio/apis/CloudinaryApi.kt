package com.tdc.nhom6.roomio.apis

import com.tdc.nhom6.roomio.models.CloudinaryModel
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*
import retrofit2.Response

interface CloudinaryApi {
    @Multipart
    @POST("image/upload")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Part("upload_preset") uploadPreset: RequestBody,
        @Part("folder") folder: RequestBody
    ): Response<CloudinaryModel>
}