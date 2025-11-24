package com.tdc.nhom6.roomio.utils

import android.app.Activity
import android.content.Context
import android.content.Intent

fun Context.navigateTo(destination: Class<*>, finishCurrent: Boolean = false,flag:Boolean) {
    val intent = Intent(this, destination)
    if (flag){
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    startActivity(intent)
    if (this is Activity && finishCurrent) {
       finish()
    }
}
