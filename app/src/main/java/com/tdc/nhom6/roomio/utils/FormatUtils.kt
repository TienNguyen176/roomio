package com.tdc.nhom6.roomio.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

object FormatUtils {
    /**
     * Formats currency value to Vietnamese format (e.g., 1000000 -> "1,000,000VND")
     */
    fun formatCurrency(value: Double): String = String.format("%,.0fVND", value)

    /**
     * Formats currency with dots as thousands separators (Vietnamese format)
     * Example: 1300000 -> "1.300.000"
     */
    fun formatCurrencyWithDots(value: Double): String {
        val priceLong = value.toLong()
        return String.format("%,d", priceLong).replace(',', '.')
    }

    /**
     * Formats price range: lowest - highest
     * If highest is null or same as lowest, shows only lowest price
     */
    fun formatPriceRange(lowestPrice: Double, highestPrice: Double?): String {
        val lowestFormatted = formatCurrencyWithDots(lowestPrice)
        return if (highestPrice != null && highestPrice > lowestPrice) {
            "$lowestFormatted - ${formatCurrencyWithDots(highestPrice)}"
        } else {
            lowestFormatted
        }
    }

    /**
     * Formats date/time from milliseconds
     */
    fun formatDateTime(millis: Long): String {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(millis))
        } catch (_: Exception) {
            Date(millis).toString()
        }
    }

    /**
     * Formats date/time or returns empty string if invalid
     */
    fun formatDateTimeOrEmpty(millis: Long): String {
        if (millis <= 0L) return ""
        return formatDateTime(millis)
    }

    /**
     * Computes number of nights between check-in and check-out
     */
    fun computeNights(checkInMillis: Long, checkOutMillis: Long): Int {
        if (checkInMillis <= 0L || checkOutMillis <= 0L) return 0
        val diff = checkOutMillis - checkInMillis
        val dayMillis = TimeUnit.DAYS.toMillis(1)
        return if (diff <= 0) 1 else max(1, ((diff + dayMillis - 1) / dayMillis).toInt())
    }
}

