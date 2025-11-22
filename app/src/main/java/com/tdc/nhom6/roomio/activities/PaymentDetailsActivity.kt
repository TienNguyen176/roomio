package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.PaymentMethodAdapter
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter.Format
import com.tdc.nhom6.roomio.databinding.DialogPaymentConfirmBinding
import com.tdc.nhom6.roomio.databinding.DialogPaymentSuccessBinding
import com.tdc.nhom6.roomio.models.Invoice
import com.tdc.nhom6.roomio.models.PaymentMethod
import com.tdc.nhom6.roomio.utils.FormatUtils
import com.tdc.nhom6.roomio.utils.InvoiceQueryUtils
import kotlin.math.max

class PaymentDetailsActivity : AppCompatActivity() {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val paymentMethods = mutableListOf<PaymentMethod>()
    private var paymentAdapter: PaymentMethodAdapter? = null
    private var paymentMethodsListener: ListenerRegistration? = null
    private var bookingListener: ListenerRegistration? = null
    private var invoiceListener: ListenerRegistration? = null
    private var walletListener: ListenerRegistration? = null
    private var requiredAmount: Double = 0.0
    private var walletBalance: Double = 0.0
    private var selectedPaymentMethod: PaymentMethod? = null
    private var bookingDocId: String? = null
    private var customerId: String? = null
    private lateinit var tvPaymentEmpty: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvPaidAmount: TextView
    private var totalAmountToPay: Double = 0.0
    private var invoicePaidAmount: Double = 0.0
    private var baseRoomPrice: Double = 0.0
    private var baseExtraFee: Double = 0.0
    private var baseCleaningFee: Double = 0.0
    private var invoiceDocId: String? = null
    private lateinit var  hotelId: String
    private lateinit var tvDiscount: TextView

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
        hotelId = intent.getStringExtra("HOTEL_ID").toString()
        bookingDocId = intent.getStringExtra("BOOKING_ID")

        val checkInText = if (rawCheckIn.isNotBlank()) rawCheckIn else formatDateTimeOrEmpty(checkInMillis)
        val checkOutText = if (rawCheckOut.isNotBlank()) rawCheckOut else formatDateTimeOrEmpty(checkOutMillis)
        val nights = when {
            nightsExtra > 0 -> nightsExtra
            checkInMillis > 0 && checkOutMillis > 0 -> computeNights(checkInMillis, checkOutMillis)
            else -> 0
        }

        baseRoomPrice = roomPrice
        baseExtraFee = extraFee
        baseCleaningFee = cleaningFee
        totalAmountToPay = roomPrice + extraFee + cleaningFee

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
        tvDiscount = findViewById(R.id.tvDiscount)
        tvDiscount.text = "Discount/ voucher: $discount"
        tvTotalAmount = findViewById(R.id.tvTotalAmount)
        tvPaidAmount = findViewById(R.id.tvPaidAmount)
        updatePaymentSummary()

        requiredAmount = totalAmountToPay
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
        bookingDocId?.let { observeInvoiceTotals(it, resId) }

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

    override fun onPause() {
        super.onPause()
        // Pause listeners to reduce load when activity is not visible
        paymentMethodsListener?.remove()
        bookingListener?.remove()
        invoiceListener?.remove()
        walletListener?.remove()
    }

    override fun onResume() {
        super.onResume()
        // Re-attach listeners when activity resumes
        val currentBookingId = bookingDocId
        if (currentBookingId != null) {
            observeBookingCustomer(currentBookingId)
            observeInvoiceTotals(currentBookingId, intent.getStringExtra("RESERVATION_ID") ?: "")
        }
        observePaymentMethods(hotelId)
    }

    override fun onDestroy() {
        super.onDestroy()
        paymentMethodsListener?.remove()
        bookingListener?.remove()
        invoiceListener?.remove()
        walletListener?.remove()
    }

    private fun formatDateTimeOrEmpty(millis: Long): String = FormatUtils.formatDateTimeOrEmpty(millis)

    private fun computeNights(checkInMillis: Long, checkOutMillis: Long): Int = 
        FormatUtils.computeNights(checkInMillis, checkOutMillis)

    private fun formatCurrency(value: Double): String = FormatUtils.formatCurrency(value)

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
                    
                    // Read discount information from booking
                    val discountId = snapshot.getString("discountId")
                    val discountPaymentMethodId = snapshot.getString("discountPaymentMethodId")
                        ?: snapshot.getString("discountPaymentMethod")
                        ?: snapshot.getString("discount_payment_method_id")
                    
                    if (!discountPaymentMethodId.isNullOrBlank()) {
                        loadDiscountDetails(discountPaymentMethodId)
                    } else if (!discountId.isNullOrBlank()) {
                        // Fallback: try to load from discounts collection
                        loadDiscountFromId(discountId)
                    }
                }
            }
    }

    private fun observeInvoiceTotals(bookingId: String, reservationId: String) {
        invoiceListener?.remove()
        val invoices = firestore.collection("invoices")
        
        // Try direct document lookup first
        invoices.document(bookingId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    attachInvoiceListener(doc.reference)
                } else {
                    // Try queries using utility
                    val queries = InvoiceQueryUtils.createInvoiceQueries(firestore, bookingId, reservationId)
                    var listenerSet = false
                    
                    for (query in queries) {
                        query.limit(1).get().addOnSuccessListener { result ->
                            if (!result.isEmpty && !listenerSet) {
                                listenerSet = true
                                attachInvoiceListener(result.documents.first().reference)
                            }
                        }
                    }
                    
                    // Fallback: listen to all invoices and filter
                    if (!listenerSet) {
                        invoiceListener = invoices.addSnapshotListener { snapshots, error ->
                            if (error != null) {
                                android.util.Log.e("PaymentDetails", "Invoice listener error", error)
                                invoicePaidAmount = 0.0
                                updatePaymentSummary()
                                return@addSnapshotListener
                            }
                            val filtered = InvoiceQueryUtils.filterInvoicesInMemory(
                                snapshots?.documents ?: emptyList(),
                                bookingId,
                                reservationId
                            )
                            invoicePaidAmount = filtered.sumOf { InvoiceQueryUtils.extractTotalAmount(it) }
                            updatePaymentSummary()
                        }
                    }
                }
            }
            .addOnFailureListener {
                invoicePaidAmount = 0.0
                updatePaymentSummary()
            }
    }

    private fun attachInvoiceListener(ref: DocumentReference) {
        invoiceDocId = ref.id
        invoiceListener?.remove()
        invoiceListener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                invoicePaidAmount = 0.0
                updatePaymentSummary()
                return@addSnapshotListener
            }
            invoicePaidAmount = if (snapshot != null && snapshot.exists()) {
                InvoiceQueryUtils.extractTotalAmount(snapshot)
            } else {
                0.0
            }
            updatePaymentSummary()
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

    private fun loadDiscountDetails(discountPaymentMethodId: String) {
        firestore.collection("discountPaymentMethods")
            .document(discountPaymentMethodId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val discountName = doc.getString("discountName") ?: ""
                    val discountDescription = doc.getString("discountDescription") ?: ""
                    val discountPercent = doc.getDouble("discountPercent")
                    val discountAmount = doc.getDouble("discountAmount")
                    
                    val displayText = when {
                        discountName.isNotBlank() -> discountName
                        discountDescription.isNotBlank() -> discountDescription
                        discountPercent != null -> "${discountPercent}% discount"
                        discountAmount != null -> "${formatCurrency(discountAmount)} discount"
                        else -> "-"
                    }
                    tvDiscount.text = "Discount/ voucher: $displayText"
                } else {
                    tvDiscount.text = "Discount/ voucher: -"
                }
            }
            .addOnFailureListener {
                tvDiscount.text = "Discount/ voucher: -"
            }
    }

    private fun loadDiscountFromId(discountId: String) {
        firestore.collection("discounts")
            .document(discountId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val discountName = doc.getString("discountName") ?: ""
                    val discountPercent = doc.getLong("discountPercent")?.toInt() ?: 0
                    val maxDiscount = doc.getLong("maxDiscount") ?: 0L
                    
                    val displayText = when {
                        discountName.isNotBlank() -> discountName
                        discountPercent > 0 -> "${discountPercent}% discount"
                        else -> "-"
                    }
                    tvDiscount.text = "Discount/ voucher: $displayText"
                } else {
                    tvDiscount.text = "Discount/ voucher: -"
                }
            }
            .addOnFailureListener {
                tvDiscount.text = "Discount/ voucher: -"
            }
    }

    private fun updateEmptyState() {
        runOnUiThread {
            try {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                val isEmpty = paymentMethods.isEmpty()
                tvPaymentEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                android.util.Log.e("PaymentDetails", "Error updating empty state: ${e.message}", e)
            }
        }
    }

    private fun updatePaymentSummary() {
        val gross = baseRoomPrice + baseExtraFee + baseCleaningFee
        val remaining = max(0.0, gross - invoicePaidAmount)
        totalAmountToPay = remaining
        requiredAmount = remaining
        android.util.Log.d("PaymentDetails", "Payment summary - Gross: $gross, Paid: $invoicePaidAmount, Remaining: $remaining")
        tvTotalAmount.text = "Total amount: ${formatCurrency(remaining)}"
        tvPaidAmount.text = "Paid amount: ${formatCurrency(invoicePaidAmount)}"
        paymentAdapter?.updateRequiredAmount(remaining)
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
                if (isTransferMethod(method)) {
                    openTransferPaymentScreen(method)
                } else {
                    val ownerId=getOwnerId(hotelId)
                    customerId?.let { openDialogPaymentConfirm(it, ownerId,method) }
                }
            }
            .show()
    }

    private fun getOwnerId(hotelId: String): String {
        var ownerId=""
        firestore.collection("hotels").document(hotelId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    ownerId=document.getString("ownerId").toString()
                }
            }
            .addOnFailureListener { exception ->
                Log.d("PaymentDetailsActivity", "getOwnerId failed with ", exception)
            }
        return ownerId
    }

    private fun isTransferMethod(method: PaymentMethod): Boolean {
        return method.paymentMethodName.equals("Transfer payment", ignoreCase = true)
    }

    private fun openTransferPaymentScreen(method: PaymentMethod) {
        val bookingId = bookingDocId
        if (bookingId.isNullOrBlank()) {
            Toast.makeText(this, "Missing booking reference", Toast.LENGTH_SHORT).show()
            return
        }
        addInvoice(method) { invoiceId ->
            invoiceDocId = invoiceId
            val intent = Intent(this, PaymentActivity::class.java).apply {
                putExtra("BOOKING_ID", bookingId)
                putExtra("INVOICE_ID", bookingId)
            }
            startActivity(intent)
        }
    }

    private fun addInvoice(method: PaymentMethod, onComplete: (invoiceDocId: String?) -> Unit = {}) {
        val docId = bookingDocId
        if (docId.isNullOrBlank()) {
            Toast.makeText(this, "Missing booking reference", Toast.LENGTH_SHORT).show()
            finish()
            onComplete(null)
            return
        }

        val invoices = firestore.collection("invoices")
        val finalInvoiceRef = invoiceDocId?.let { invoices.document(it) } ?: invoices.document(docId)

        if (invoiceDocId == null) {
            invoiceDocId = finalInvoiceRef.id
        }

        val newPaidTotal = invoicePaidAmount + totalAmountToPay
        val invoiceUpdates = mapOf(
            "bookingId" to docId,
            "totalAmount" to newPaidTotal,
            "paymentMethodId" to method.paymentMethodId
        )

        firestore.runBatch { batch ->
            batch.set(finalInvoiceRef, invoiceUpdates, SetOptions.merge())
        }.addOnSuccessListener {
            Toast.makeText(this, "Payment confirmed", Toast.LENGTH_SHORT).show()
            onComplete(finalInvoiceRef.id)
        }.addOnFailureListener { error ->
            Toast.makeText(
                this,
                "Failed to confirm payment: ${error.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_LONG
            ).show()
            onComplete(null)
        }
    }
    private fun openDialogPaymentConfirm(customerId: String, ownerId: String,method: PaymentMethod) {
        val amount=totalAmountToPay
        val viewBinding = DialogPaymentConfirmBinding.inflate(layoutInflater)
        viewBinding.tvAmountPayment.text= Format.formatCurrency(amount)

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(viewBinding.root)
            .create()

        viewBinding.btnYes.setOnClickListener {
            dialog.dismiss()
            addInvoice(method) { invoiceId ->
                invoiceDocId = invoiceId
                firestore.runTransaction { transition ->
                    val userRef=firestore.collection("users").document(customerId)
                    val ownerRef=firestore.collection("users").document(ownerId)
                    val userSnapshot=transition.get(userRef)
                    val ownerSnapshot=transition.get(ownerRef)
                    val currentCustomerBalance=userSnapshot.getDouble("walletBalance")
                    val currentOwnerBalance=ownerSnapshot.getDouble("walletBalance")
                    val newCustomerBalance = currentCustomerBalance?.minus(amount)
                    val newOwnerBalance = currentOwnerBalance?.plus(amount)
                    transition.update(userRef,"walletBalance",newCustomerBalance)
                    transition.update(ownerRef,"walletBalance",newOwnerBalance)
                }
                    .addOnSuccessListener {
                        Log.d("Payment", "Transaction success!")
                        firestore.collection("bookings").document(bookingDocId!!)
                            .update("status","completed")
                        firestore.collection("invoices").document(invoiceId!!)
                            .update("paymentStatus","paid")
                        openDialogPaymentSuccess(amount)
                    }.addOnFailureListener { e ->
                        Log.w("Payment", "Transaction failure.", e)
                    }
            }

        }

        viewBinding.btnNo.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }
    private fun openDialogPaymentSuccess(amount: Double){
        val viewBinding = DialogPaymentSuccessBinding.inflate(layoutInflater)
        viewBinding.tvAmountPayment.text=  Format.formatCurrency(amount)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(viewBinding.root)
            .create()

        viewBinding.btnOK.setOnClickListener{
            dialog.dismiss()
            val intent=Intent(this,ReceptionActivity::class.java).apply {
                flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
        dialog.setOnCancelListener{
            dialog.dismiss()
            val intent=Intent(this,ReceptionActivity::class.java).apply {
                flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }

        dialog.show()
    }
}
