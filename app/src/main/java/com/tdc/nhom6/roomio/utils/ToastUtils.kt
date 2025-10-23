package com.tdc.nhom6.roomio.utils

import android.content.Context
import android.widget.Toast

object ToastUtils {
    private var currentToast: Toast? = null

    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        // Cancel any currently showing toast to prevent queuing
        currentToast?.cancel()
        currentToast = Toast.makeText(context.applicationContext, message, duration)
        currentToast?.show()
    }
}




