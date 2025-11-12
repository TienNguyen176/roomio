package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.ServiceFeeAdapter
import com.tdc.nhom6.roomio.data.CleanerTaskRepository

class ServiceExtraFeeActivity : AppCompatActivity() {
    private lateinit var tvCleaningFee: TextView
    private lateinit var tvExtraServices: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvResId: TextView
    private lateinit var tvGuestName: TextView
    private lateinit var tvCheckIn: TextView
    private lateinit var tvCheckOut: TextView
    private lateinit var tvReservationAmount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_extra_fee)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        tvCleaningFee = findViewById(R.id.tvCleaningFee)
        tvExtraServices = findViewById(R.id.tvExtraServices)
        tvTotalAmount = findViewById(R.id.tvTotalAmount)
        tvResId = findViewById(R.id.tvResId)
        tvGuestName = findViewById(R.id.tvGuestName)
        tvCheckIn = findViewById(R.id.tvCheckIn)
        tvCheckOut = findViewById(R.id.tvCheckOut)
        tvReservationAmount = findViewById(R.id.tvReservationAmount)

        val roomId = intent.getStringExtra("ROOM_ID") ?: ""
        val cleaning = CleanerTaskRepository.getCleaningFee(roomId)
        tvCleaningFee.text = String.format("Total cleaning fee: %,.0fVND", cleaning)

        // Populate guest/reservation info
        val resId = intent.getStringExtra("RESERVATION_ID") ?: ""
        val guestName = intent.getStringExtra("GUEST_NAME") ?: ""
        val checkInTxt = intent.getStringExtra("CHECK_IN") ?: ""
        val checkOutTxt = intent.getStringExtra("CHECK_OUT") ?: ""
        val amount = intent.getDoubleExtra("RESERVATION_AMOUNT", 0.0)

        tvResId.text = if (resId.isNotEmpty()) "Reservation ID: $resId" else ""
        if (guestName.isNotEmpty()) tvGuestName.text = "Guest name: $guestName" else tvGuestName.text = ""
        if (checkInTxt.isNotEmpty()) tvCheckIn.text = "Check-in: $checkInTxt" else tvCheckIn.text = ""
        if (checkOutTxt.isNotEmpty()) tvCheckOut.text = "Check-out: $checkOutTxt" else tvCheckOut.text = ""
        tvReservationAmount.text = String.format("Reservation amount: %,.0fVND", amount)

        val rv = findViewById<RecyclerView>(R.id.rvServices)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = ServiceFeeAdapter(defaultServices().toMutableList()) { updateTotals(cleaning, it) }
        rv.adapter = adapter

        findViewById<MaterialButton>(R.id.btnNext).setOnClickListener {
            val nextIntent = android.content.Intent(this, PaymentDetailsActivity::class.java)
            val incoming = intent
            // forward guest/reservation info if present
            nextIntent.putExtra("GUEST_NAME", incoming.getStringExtra("GUEST_NAME") ?: "")
            nextIntent.putExtra("GUEST_PHONE", incoming.getStringExtra("GUEST_PHONE") ?: "")
            nextIntent.putExtra("GUEST_EMAIL", incoming.getStringExtra("GUEST_EMAIL") ?: "")
            nextIntent.putExtra("RESERVATION_ID", resId)
            nextIntent.putExtra("CHECK_IN", checkInTxt)
            nextIntent.putExtra("CHECK_OUT", checkOutTxt)
            nextIntent.putExtra("ROOM_TYPE", incoming.getStringExtra("ROOM_TYPE") ?: "")
            nextIntent.putExtra("NIGHT_PEOPLE", incoming.getStringExtra("NIGHT_PEOPLE") ?: "")
            nextIntent.putExtra("ROOM_PRICE", amount)
            nextIntent.putExtra("EXTRA_FEE", lastExtraTotal)
            nextIntent.putExtra("CLEANING_FEE", cleaning)
            nextIntent.putExtra("TAX_FEE", 0.0)
            nextIntent.putExtra("DISCOUNT_TEXT", "-")
            startActivity(nextIntent)
        }

        updateTotals(cleaning, 0.0)
    }

    private fun updateTotals(cleaningFee: Double, extra: Double) {
        tvExtraServices.text = String.format("Total additional chargers: %,.0fVND", extra)
        tvTotalAmount.text = String.format("Total amount: %,.0fVND", cleaningFee + extra)
        lastExtraTotal = extra
    }

    private var lastExtraTotal: Double = 0.0

    private fun defaultServices(): List<ServiceFeeAdapter.ServiceItem> = listOf(
        ServiceFeeAdapter.ServiceItem(R.drawable.ic_service_tour, "Tour booking", 100000.0),
        ServiceFeeAdapter.ServiceItem(R.drawable.ic_service_delivery, "Delivery service", 50000.0),
        ServiceFeeAdapter.ServiceItem(R.drawable.ic_service_meeting, "Meeting room", 200000.0),
        ServiceFeeAdapter.ServiceItem(R.drawable.ic_service_airport, "Airport transfer", 150000.0),
        ServiceFeeAdapter.ServiceItem(R.drawable.ic_service_laundry, "Laundry", 80000.0),
        ServiceFeeAdapter.ServiceItem(R.drawable.ic_service_pet, "Pet", 60000.0),
        ServiceFeeAdapter.ServiceItem(R.drawable.ic_service_decoration, "Room Decoration", 120000.0),
        ServiceFeeAdapter.ServiceItem(R.drawable.ic_service_spa, "Spa, gym", 90000.0),
        ServiceFeeAdapter.ServiceItem(R.drawable.ic_service_upgrade, "Room upgrade", 300000.0),
        ServiceFeeAdapter.ServiceItem(R.drawable.ic_service_meal, "Lunch, dinner", 180000.0),
        ServiceFeeAdapter.ServiceItem(R.drawable.ic_service_roomsvc, "Room service", 70000.0)
    )
}


