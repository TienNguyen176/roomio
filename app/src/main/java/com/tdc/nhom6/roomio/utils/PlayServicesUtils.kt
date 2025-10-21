package com.tdc.nhom6.roomio.utils

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

object PlayServicesUtils {
    fun isAvailable(context: Context): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val result = apiAvailability.isGooglePlayServicesAvailable(context)
        return result == ConnectionResult.SUCCESS
    }
}



