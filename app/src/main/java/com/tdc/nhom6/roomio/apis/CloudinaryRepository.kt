package com.tdc.nhom6.roomio.apis

import android.content.Context
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.models.CloudinaryModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class CloudinaryRepository(context: Context) {

    private val cloudName = context.getString(R.string.cloudinary_cloud_name)
    private val uploadPreset = context.getString(R.string.cloudinary_upload_preset)
    private val api = RetrofitClient.createCloudinaryClient(cloudName)

    suspend fun uploadSingleImage(file: File, folderName: String): CloudinaryModel? =
        withContext(Dispatchers.IO) {
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val uploadPresetBody = uploadPreset.toRequestBody("text/plain".toMediaTypeOrNull())
            val folderBody = folderName.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.uploadImage(body, uploadPresetBody, folderBody)
            if (response.isSuccessful) response.body() else null
        }

    suspend fun uploadMultipleImages(files: List<File>, folderName: String): List<CloudinaryModel> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<CloudinaryModel>()
            for (file in files) {
                val result = uploadSingleImage(file, folderName)
                result?.let { results.add(it) }
            }
            results
        }
}