package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.tdc.nhom6.roomio.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

class PaymentDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_details)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val guestName = intent.getStringExtra("GUEST_NAME") ?: ""
        val guestPhone = intent.getStringExtra("GUEST_PHONE") ?: ""
        val guestEmail = intent.getStringExtra("GUEST_EMAIL") ?: ""

        val resId = intent.getStringExtra("RESERVATION_ID") ?: ""
        val rawCheckIn = intent.getStringExtra("CHECK_IN_TEXT") ?: intent.getStringExtra("CHECK_IN") ?: ""
        val rawCheckOut = intent.getStringExtra("CHECK_OUT_TEXT") ?: intent.getStringExtra("CHECK_OUT") ?: ""
        val checkInMillis = intent.getLongExtra("CHECK_IN_MILLIS", -1L)
        val checkOutMillis = intent.getLongExtra("CHECK_OUT_MILLIS", -1L)
        val roomType = intent.getStringExtra("ROOM_TYPE") ?: ""
        val nightsExtra = intent.getIntExtra("NIGHTS_COUNT", -1)
        val guestsCount = intent.getIntExtra("GUESTS_COUNT", 0)
        val discount = intent.getStringExtra("DISCOUNT_TEXT") ?: "-"

        val roomPrice = intent.getDoubleExtra("ROOM_PRICE", 0.0)
        val extraFee = intent.getDoubleExtra("EXTRA_FEE", 0.0)
        val cleaningFee = intent.getDoubleExtra("CLEANING_FEE", 0.0)
        val taxFee = intent.getDoubleExtra("TAX_FEE", 0.0)

        val checkInText = if (rawCheckIn.isNotBlank()) rawCheckIn else formatDateTimeOrEmpty(checkInMillis)
        val checkOutText = if (rawCheckOut.isNotBlank()) rawCheckOut else formatDateTimeOrEmpty(checkOutMillis)
        val nights = when {
            nightsExtra > 0 -> nightsExtra
            checkInMillis > 0 && checkOutMillis > 0 -> computeNights(checkInMillis, checkOutMillis)
            else -> 0
        }

        val totalAmount = roomPrice + extraFee + cleaningFee + taxFee

        findViewById<TextView>(R.id.tvGuestName).text = "Guest name: $guestName"
        findViewById<TextView>(R.id.tvGuestPhone).text = if (guestPhone.isNotEmpty()) "Phone: $guestPhone" else ""
        findViewById<TextView>(R.id.tvGuestEmail).text = if (guestEmail.isNotEmpty()) "Email: $guestEmail" else ""

        findViewById<TextView>(R.id.tvResId).text = "Reservation ID: $resId"
        findViewById<TextView>(R.id.tvCheckIn).text = if (checkInText.isNotEmpty()) "Check-in: $checkInText" else "Check-in: -"
        findViewById<TextView>(R.id.tvCheckOut).text = if (checkOutText.isNotEmpty()) "Check-out: $checkOutText" else "Check-out: -"
        findViewById<TextView>(R.id.tvRoomType).text = if (roomType.isNotEmpty()) "Room type: $roomType" else "Room type: -"

        val nightLabel = when {
            nights <= 0 -> "-"
            nights == 1 -> "1 night"
            else -> "$nights nights"
        }
        val guestLabel = if (guestsCount > 0) "$guestsCount people" else "-"
        findViewById<TextView>(R.id.tvNightPeople).text = "Night: $nightLabel - $guestLabel"

        findViewById<TextView>(R.id.tvRoomPrice).text = "Room price/night: ${formatCurrency(roomPrice)}"
        findViewById<TextView>(R.id.tvExtraFee).text = "Extra fee: ${formatCurrency(extraFee)}"
        findViewById<TextView>(R.id.tvTaxFee).text = "Tax & service fee: ${formatCurrency(taxFee)}"
        findViewById<TextView>(R.id.tvCleaningFee).text = "Cleaning fee: ${formatCurrency(cleaningFee)}"
        findViewById<TextView>(R.id.tvDiscount).text = "Discount/ voucher: $discount"
        findViewById<TextView>(R.id.tvGuestPay).text = "Guest pay: ${formatCurrency(totalAmount)}"
        findViewById<TextView>(R.id.tvTotalAmount).text = "Total amount: ${formatCurrency(totalAmount)}"
        findViewById<TextView>(R.id.tvGrandTotal).text = "Total amount: ${formatCurrency(totalAmount)}"

        val paymentGroup = findViewById<RadioGroup>(R.id.paymentMethods)
        findViewById<MaterialButton>(R.id.btnConfirmPayment).setOnClickListener {
            val selectedId = paymentGroup.checkedRadioButtonId
            if (selectedId == -1) {
                Toast.makeText(this, "Please select a payment method", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val method = when (selectedId) {
                R.id.rbCash -> "Cash"
                R.id.rbBankTransfer -> "Bank transfer"
                R.id.rbTravelWallet -> "Travel wallet"
                else -> "Unknown"
            }
            Toast.makeText(this, "Payment method: $method", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun formatDateTimeOrEmpty(millis: Long): String {
        if (millis <= 0L) return ""
        return try {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
        } catch (_: Exception) {
            Date(millis).toString()
        }
    }

    private fun computeNights(checkInMillis: Long, checkOutMillis: Long): Int {
        val diff = checkOutMillis - checkInMillis
        val dayMillis = TimeUnit.DAYS.toMillis(1)
        return if (diff <= 0) 1 else max(1, ((diff + dayMillis - 1) / dayMillis).toInt())
    }

    private fun formatCurrency(value: Double): String = String.format("%,.0fVND", value)
}


