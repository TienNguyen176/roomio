package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.PaymentMethodAdapter
import com.tdc.nhom6.roomio.models.PaymentMethod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

class PaymentDetailsActivity : AppCompatActivity() {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val paymentMethods = mutableListOf<PaymentMethod>()
    private var paymentAdapter: PaymentMethodAdapter? = null
    private var paymentMethodsListener: ListenerRegistration? = null
    private var bookingListener: ListenerRegistration? = null
    private var walletListener: ListenerRegistration? = null
    private var requiredAmount: Double = 0.0
    private var walletBalance: Double = 0.0
    private var selectedPaymentMethod: PaymentMethod? = null
    private var bookingDocId: String? = null
    private var customerId: String? = null
    private lateinit var tvPaymentEmpty: TextView
    private var totalAmountToPay: Double = 0.0

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
        val hotelId = intent.getStringExtra("HOTEL_ID")
        bookingDocId = intent.getStringExtra("BOOKING_ID")

        val checkInText = if (rawCheckIn.isNotBlank()) rawCheckIn else formatDateTimeOrEmpty(checkInMillis)
        val checkOutText = if (rawCheckOut.isNotBlank()) rawCheckOut else formatDateTimeOrEmpty(checkOutMillis)
        val nights = when {
            nightsExtra > 0 -> nightsExtra
            checkInMillis > 0 && checkOutMillis > 0 -> computeNights(checkInMillis, checkOutMillis)
            else -> 0
        }

        val totalAmount = roomPrice + extraFee + cleaningFee
        totalAmountToPay = totalAmount

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
        findViewById<TextView>(R.id.tvCleaningFee).text = "Cleaning fee: ${formatCurrency(cleaningFee)}"
        findViewById<TextView>(R.id.tvDiscount).text = "Discount/ voucher: $discount"
        findViewById<TextView>(R.id.tvTotalAmount).text = "Total amount: ${formatCurrency(totalAmount)}"
        findViewById<TextView>(R.id.tvGuestPay).text = "Guest pay: ${formatCurrency(totalAmount)}"

        findViewById<TextView>(R.id.tvGrandTotal).text = "Total amount: ${formatCurrency(totalAmount)}"

        requiredAmount = totalAmount
        walletBalance = 0.0
        tvPaymentEmpty = findViewById(R.id.tvPaymentEmpty)

        val rvPayment = findViewById<RecyclerView>(R.id.rvPaymentMethods)
        rvPayment.layoutManager = LinearLayoutManager(this)
        paymentAdapter = PaymentMethodAdapter(paymentMethods, requiredAmount, walletBalance).also { adapter ->
            rvPayment.adapter = adapter
            adapter.onPaymentMethodSelected = { method ->
                selectedPaymentMethod = method
            }
        }

        observePaymentMethods(hotelId)
        observeBookingCustomer(bookingDocId)

        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirmPayment)
        btnConfirm.setOnClickListener {
            val method = selectedPaymentMethod
            if (method == null) {
                Toast.makeText(this, "Please select a payment method", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showConfirmDialog(method)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        paymentMethodsListener?.remove()
        bookingListener?.remove()
        walletListener?.remove()
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

    private fun observePaymentMethods(hotelId: String?) {
        paymentMethodsListener?.remove()
        val collection = firestore.collection("paymentMethods")
        paymentMethodsListener = collection.addSnapshotListener { result, exception ->
            if (exception != null) {
                tvPaymentEmpty.text = "Failed to load payment methods."
                tvPaymentEmpty.visibility = View.VISIBLE
                return@addSnapshotListener
            }

            if (result != null) {
                paymentMethods.clear()
                for (doc in result) {
                    runCatching { doc.toObject(PaymentMethod::class.java) }
                        .onSuccess { paymentMethods.add(it) }
                }
                paymentAdapter?.notifyDataSetChanged()
                updateEmptyState()
            }
        }
    }

    private fun observeBookingCustomer(bookingId: String?) {
        if (bookingId.isNullOrBlank()) {
            paymentAdapter?.updateRequiredAmount(requiredAmount)
            paymentAdapter?.updateWalletBalance(walletBalance)
            return
        }
        bookingListener?.remove()
        bookingListener = firestore.collection("bookings")
            .document(bookingId)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val customer = snapshot.get("customerId")
                        ?: snapshot.get("customerID")
                        ?: snapshot.get("customer_id")
                        ?: snapshot.get("customer")
                    customerId = when (customer) {
                        is String -> customer
                        is Number -> customer.toString()
                        is Map<*, *> -> (customer["id"] as? String)
                            ?: (customer["customerId"] as? String)
                            ?: (customer["customer_id"] as? String)
                        is com.google.firebase.firestore.DocumentReference -> customer.id
                        else -> null
                    }
                    customerId?.let { loadWalletBalance(it) }
                }
            }
    }

    private fun loadWalletBalance(customerId: String) {
        walletListener?.remove()
        walletListener = firestore.collection("users")
            .document(customerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    walletBalance = 0.0
                    paymentAdapter?.updateWalletBalance(walletBalance)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    walletBalance = snapshot.getDouble("walletBalance") ?: 0.0
                } else {
                    walletBalance = 0.0
                }
                paymentAdapter?.updateWalletBalance(walletBalance)
            }
    }

    private fun updateEmptyState() {
        val isEmpty = paymentMethods.isEmpty()
        tvPaymentEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun showConfirmDialog(method: PaymentMethod) {
        AlertDialog.Builder(this)
            .setTitle("Confirm payment")
            .setMessage(
                "Charge ${formatCurrency(totalAmountToPay)} using ${method.paymentMethodName}?"
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { dialog, _ ->
                dialog.dismiss()
                completeBooking(method)
            }
            .show()
    }

    private fun completeBooking(method: PaymentMethod) {
        val docId = bookingDocId
        if (docId.isNullOrBlank()) {
            Toast.makeText(this, "Missing booking reference", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val updates = mutableMapOf<String, Any>(
            "status" to "completed",
            "paymentStatus" to "completed",
            "paymentMethodName" to method.paymentMethodName,
            "paymentCompletedAt" to FieldValue.serverTimestamp(),
            "totalPaidAmount" to totalAmountToPay
        )
        method.paymentMethodId?.let { updates["paymentMethodId"] = it }

        firestore.collection("bookings")
            .document(docId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Payment confirmed", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Failed to confirm payment: ${error.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}
