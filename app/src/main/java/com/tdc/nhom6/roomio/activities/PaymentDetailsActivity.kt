package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.tdc.nhom6.roomio.R

class PaymentDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_details)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val guestName = intent.getStringExtra("GUEST_NAME") ?: ""
        val guestPhone = intent.getStringExtra("GUEST_PHONE") ?: ""
        val guestEmail = intent.getStringExtra("GUEST_EMAIL") ?: ""

        val resId = intent.getStringExtra("RESERVATION_ID") ?: ""
        val checkIn = intent.getStringExtra("CHECK_IN") ?: ""
        val checkOut = intent.getStringExtra("CHECK_OUT") ?: ""
        val roomType = intent.getStringExtra("ROOM_TYPE") ?: ""
        val nightPeople = intent.getStringExtra("NIGHT_PEOPLE") ?: ""

        val roomPrice = intent.getDoubleExtra("ROOM_PRICE", 0.0)
        val extraFee = intent.getDoubleExtra("EXTRA_FEE", 0.0)
        val cleaningFee = intent.getDoubleExtra("CLEANING_FEE", 0.0)
        val taxFee = intent.getDoubleExtra("TAX_FEE", 0.0)
        val discount = intent.getStringExtra("DISCOUNT_TEXT") ?: "-"

        val totalAmount = roomPrice + extraFee + cleaningFee + taxFee

        findViewById<TextView>(R.id.tvGuestName).text = "Guest name: $guestName"
        findViewById<TextView>(R.id.tvGuestPhone).text = if (guestPhone.isNotEmpty()) "Phone: $guestPhone" else ""
        findViewById<TextView>(R.id.tvGuestEmail).text = if (guestEmail.isNotEmpty()) "Email: $guestEmail" else ""

        findViewById<TextView>(R.id.tvResId).text = "Reservation ID: $resId"
        findViewById<TextView>(R.id.tvCheckIn).text = "Check-in: $checkIn"
        findViewById<TextView>(R.id.tvCheckOut).text = "Check-out: $checkOut"
        findViewById<TextView>(R.id.tvRoomType).text = if (roomType.isNotEmpty()) "Room type: $roomType" else ""
        findViewById<TextView>(R.id.tvNightPeople).text = nightPeople

        findViewById<TextView>(R.id.tvRoomPrice).text = String.format("Room price/night: %,.0fVND", roomPrice)
        findViewById<TextView>(R.id.tvExtraFee).text = String.format("Extra fee: %,.0fVND", extraFee)
        findViewById<TextView>(R.id.tvTaxFee).text = String.format("Tax & service fee: %,.0fVND", taxFee)
        findViewById<TextView>(R.id.tvCleaningFee).text = String.format("Cleaning fee: %,.0fVND", cleaningFee)
        findViewById<TextView>(R.id.tvDiscount).text = "Discount/ voucher: $discount"
        findViewById<TextView>(R.id.tvTotalAmount).text = String.format("Total amount: %,.0fVND", totalAmount)
        findViewById<TextView>(R.id.tvGrandTotal).text = String.format("Total amount: %,.0fVND", totalAmount)

        findViewById<MaterialButton>(R.id.btnConfirmPayment).setOnClickListener {
            val view = layoutInflater.inflate(R.layout.dialog_confirm, null, false)
            val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
            val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)
            val btnOk = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOk)
            val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

            tvTitle.text = "Notification"
            tvSubtitle.text = "Confirm payment?"

            val alert = androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(view)
                .create()

            btnOk.setOnClickListener {
                // TODO: invoke payment logic or API here
                alert.dismiss()
                finish()
            }
            btnCancel.setOnClickListener { alert.dismiss() }

            alert.show()
        }
    }
}


