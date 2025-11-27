package com.tdc.nhom6.roomio.activities.receptionist

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.MainActivity
import com.tdc.nhom6.roomio.activities.booking.BookingDetailActivity
import com.tdc.nhom6.roomio.activities.booking.PaymentActivity
import com.tdc.nhom6.roomio.activities.hotel.HotelDetailActivity.Companion.db
import com.tdc.nhom6.roomio.adapters.PaymentMethodAdapter
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter.Format
import com.tdc.nhom6.roomio.databinding.ActivityPaymentBinding
import com.tdc.nhom6.roomio.databinding.DialogPaymentConfirmBinding
import com.tdc.nhom6.roomio.databinding.DialogPaymentSuccessBinding
import com.tdc.nhom6.roomio.models.BankInfo
import com.tdc.nhom6.roomio.models.Booking
import com.tdc.nhom6.roomio.models.HotelModel
import com.tdc.nhom6.roomio.models.Invoice
import com.tdc.nhom6.roomio.models.PaymentMethod
import com.tdc.nhom6.roomio.models.RoomType
import com.tdc.nhom6.roomio.utils.FormatUtils
import com.tdc.nhom6.roomio.utils.InvoiceQueryUtils
import kotlinx.coroutines.launch
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


    private val DURATION_24_HOURS_MS = 86400000L
    private val INTERVAL_MS = 1000L
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var binding: ActivityPaymentBinding
    private var currentOwnerBankInfo: BankInfo? = null
    private lateinit var bookingId: String
    private lateinit var invoiceId: String

    private var currentHotel: HotelModel? = null
    private var currentBooking: Booking?= null
    private var currentRoomType: RoomType? = null

    private var invoiceDetailListener: ListenerRegistration? = null
    private var roomTypeListener: ListenerRegistration? = null
    private var hotelListener: ListenerRegistration? = null

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_details)
//            binding=ActivityPaymentBinding.inflate(layoutInflater)
//            enableEdgeToEdge()
//            setContentView(binding.root)
//            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
//                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//                insets
//            }
//
//            initial()

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
        totalAmountToPay = roomPrice + extraFee

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
        bookingListener?.remove()
        invoiceDetailListener?.remove()
        roomTypeListener?.remove()
        hotelListener?.remove()
        if (::countDownTimer.isInitialized) {
            countDownTimer.cancel()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initial() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Payment"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAVIGATE_TO_BOOKING", true)
            }
            startActivity(intent)
            finish()
        }

        bookingId = intent.getStringExtra("BOOKING_ID").toString()
        invoiceId = intent.getStringExtra("INVOICE_ID").toString()


        loadBooking(bookingId) {
            val loadedBooking = currentBooking
            if (loadedBooking == null) {
                Log.e("PaymentActivity", "Booking data was null after loading chain finished.")
                return@loadBooking
            }

            updateBookingDetailsUI(loadedBooking)

            lifecycleScope.launch {
                if (!invoiceId.isNullOrEmpty() && invoiceId != "null") {
                    getInvoice(invoiceId) { fetchedInvoice ->
                        handleInvoiceResult(fetchedInvoice, loadedBooking)
                    }
                } else {
                    Log.w("PaymentActivity", "Invoice ID is missing. Finding latest invoice for Booking: $bookingId")
                    getLatestInvoice(bookingId) { fetchedInvoice ->
                        handleInvoiceResult(fetchedInvoice, loadedBooking)
                    }
                }
            }

            binding.btnDone.setOnClickListener {
                val docId = bookingDocId
                if (docId.isNullOrBlank()) {
                    Toast.makeText(this, "Missing booking reference", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                firestore.collection("bookings").document(docId)
                    .update("status", "completed")
                    .addOnSuccessListener {
                        firestore.collection("invoices")
                            .whereEqualTo("bookingId", docId)
                            .get()
                            .addOnSuccessListener { snaps ->
                                snaps.documents.forEach { it.reference.update("paymentStatus", "paid") }
                            }

                        val intent = Intent(this, ReceptionActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to update booking status", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateBookingDetailsUI(loadedBooking: Booking) {
        val startDate = loadedBooking.checkInDate?.toDate()?.time ?: 0L
        val endDate = loadedBooking.checkOutDate?.toDate()?.time ?: 0L
        val diff = endDate - startDate
        val numberOfNights = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)

        binding.tvHotelName.text = currentHotel?.hotelName ?: ""
        binding.ratingBar.rating = (currentHotel?.averageRating ?: 0).toFloat()
        binding.tvReviews.text = "${currentHotel?.totalReviews} reviews"

        val roomTypeName = currentRoomType?.typeName
        val guestCount = loadedBooking.numberGuest
        binding.tvBookingDetail.text =  "${roomTypeName} (${numberOfNights} night, ${guestCount} people )"
        binding.tvTotalAfter.text ="Total: ${RoomTypeAdapter.Format.formatCurrency(loadedBooking.totalFinal)}"
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
                        is DocumentReference -> customer.id
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
        val gross = baseRoomPrice + baseExtraFee
        val remaining = max(0.0, gross - invoicePaidAmount)
        totalAmountToPay = remaining
        requiredAmount = remaining
        Log.d("PaymentDetails", "Payment summary - Gross: $gross, Paid: $invoicePaidAmount, Remaining: $remaining")
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
    @SuppressLint("SetTextI18n")
    private fun handleInvoiceResult(fetchedInvoice: Invoice?, loadedBooking: Booking) {
        if (fetchedInvoice != null) {
            val invoice: Invoice = fetchedInvoice

            binding.tvInvoiceId.text = "#ID: ${invoice.invoiceId}"
            binding.tvCreateAt.text = convertTimestampToString(invoice.createdAt)

            val bookingTotal = loadedBooking.totalFinal
            val invoiceAmount = invoice.totalAmount

            val percentagePaid = if (bookingTotal > 0 && invoiceAmount != null) {
                (invoiceAmount / bookingTotal) * 100.0
            } else 0.0

            val formattedPercent = String.format("%.2f", percentagePaid)
            binding.tvAmountPayment.text = "Payment: ${
                RoomTypeAdapter.Format.formatCurrency(
                invoice.totalAmount ?: 0.0
            )} (${formattedPercent}%)"

            setupTimer(invoice.createdAt)
            generateQRPayment(invoice)
        } else {
            Log.e("PaymentActivity", "Invoice data was null after fallback/direct lookup finished.")
        }
    }
    private fun generateQRPayment(invoice: Invoice) {
        currentHotel?.let {
            db.collection("users")
                .document(it.ownerId)
                .collection("bank_info")
                .whereEqualTo("default",true)
                .addSnapshotListener { snapShot, error ->

                    if (error != null) {
                        Log.e("Firebase", "Error listening to Bank Info: ", error)
                        return@addSnapshotListener
                    }

                    if (snapShot != null && !snapShot.isEmpty) {

                        val documentSnapshot = snapShot.documents.firstOrNull()

                        try {
                            currentOwnerBankInfo = documentSnapshot?.toObject(BankInfo::class.java)

                            currentOwnerBankInfo?.let { info ->
                                val QR_URL =
                                    "https://img.vietqr.io/image/${info.bank_code}-${info.account_number}-compact.png" +
                                            "?amount=${invoice.totalAmount}" +
                                            "&addInfo=Roomio_${invoice.invoiceId}" +
                                            "&accountName=${info.account_holder}"
                                Glide.with(this).load(QR_URL).into(binding.ivQrCode)
                                binding.tvBankName.text = info.bank_name ?: info.bank_code
                                binding.tvAccountNumber.text = info.account_number
                                binding.tvAccountName.text = info.account_holder
                                binding.tvAmount.text = RoomTypeAdapter.Format.formatCurrency(invoice.totalAmount)
                                binding.tvContent.text = "Roomio_${invoice.invoiceId}"
                            }
                        } catch (ex: Exception) {
                            Log.e("Firebase", "Error converting BankInfo data", ex)
                        }
                    } else {
                        Log.w("Firebase", "BankInfo not found.")
                    }
                }
        }

    }

    private fun setupTimer(createdAt: Timestamp) {
        val now = System.currentTimeMillis()
        val createdAtMs = createdAt.toDate().time
        val elapsedTimeMs = DURATION_24_HOURS_MS - (now - createdAtMs)

        if (::countDownTimer.isInitialized) {
            countDownTimer.cancel()
        }

        countDownTimer = object : CountDownTimer(elapsedTimeMs, INTERVAL_MS){
            override fun onTick(millisUntilFinished: Long) {
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)

                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val remainingSeconds = seconds % 60

                val formattedTime = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, remainingSeconds)

                binding.tvTimer.text = formattedTime
            }

            override fun onFinish() {
                binding.tvTimer.text = "Time expired!"
            }

        }
        countDownTimer.start()
    }
    private fun loadBooking(bookingId: String, onComplete: () -> Unit) {
        bookingListener?.remove()

        bookingListener = db.collection("bookings")
            .document(bookingId)
            .addSnapshotListener { dataSnapshot, exception ->

                if (dataSnapshot != null && dataSnapshot.exists()) {
                    try {
                        val booking = dataSnapshot.toObject(Booking::class.java)
                        if (booking != null) {
                            currentBooking = booking

                            loadRoomType(currentBooking!!.roomTypeId, onComplete)
                        }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Error converting data for Booking: ${dataSnapshot.id}", ex)
                    }
                } else {
                    Log.w("Firebase", "Booking ID: $bookingId not found.")
                }
            }
    }

    private fun getLatestInvoice(bookingId: String, onComplete: (invoice: Invoice?) -> Unit) {
        db.collection("invoices")
            .whereEqualTo("bookingId", bookingId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val document = querySnapshot.documents.firstOrNull()
                val latestInvoiceId = document?.id

                if (latestInvoiceId != null) {
                    Log.d("Firebase", "Found latest invoice ID: $latestInvoiceId")
                    getInvoice(latestInvoiceId, onComplete)
                } else {
                    Log.w("Firebase", "No invoice found for Booking ID: $bookingId")
                    onComplete(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error finding latest invoice: ${e.message}", e)
                onComplete(null)
            }
    }

    private fun getInvoice(invoiceId: String, onComplete: (invoice: Invoice?) -> Unit) {
        invoiceDetailListener?.remove()

        invoiceDetailListener = db.collection("invoices")
            .document(invoiceId)
            .addSnapshotListener { documentSnapshot, exception ->

                if (exception != null) {
                    Log.e("Firebase", "Error listening to invoice: ${exception.message}", exception)
                    onComplete(null)
                    return@addSnapshotListener
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    try {
                        val invoice = documentSnapshot.toObject(Invoice::class.java)
                        onComplete(invoice)
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Error converting Invoice data: ${documentSnapshot.id}", ex)
                        onComplete(null)
                    }
                } else {
                    onComplete(null)
                }
            }
    }

    private fun loadRoomType(roomTypeId: String, onComplete: () -> Unit) {
        roomTypeListener?.remove()

        roomTypeListener = db.collection("roomTypes")
            .document(roomTypeId)
            .addSnapshotListener { dataSnapshot, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Error listening to RoomType: ", exception)
                    return@addSnapshotListener
                }

                if (dataSnapshot != null && dataSnapshot.exists()) {
                    try {
                        val roomType = dataSnapshot.toObject(RoomType::class.java)
                        if (roomType != null) {
                            currentRoomType = roomType
                            loadHotel(roomType.hotelId, onComplete)
                        }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Error converting data for RoomType: ${dataSnapshot.id}", ex)
                    }
                } else {
                    Log.w("Firebase", "RoomType ID: $roomTypeId not found.")
                }
            }
    }

    private fun loadHotel(hotelId: String, onComplete: () -> Unit) {
        hotelListener?.remove()

        hotelListener = db.collection("hotels")
            .document(hotelId)
            .addSnapshotListener { dataSnapshot, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Error listening to Hotel: ", exception)
                    return@addSnapshotListener
                }

                if (dataSnapshot != null && dataSnapshot.exists()) {
                    try {
                        val hotel = dataSnapshot.toObject(HotelModel::class.java)
                        if (hotel != null) {
                            currentHotel = hotel
                            onComplete()
                        }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Error converting data for Hotel: ${dataSnapshot.id}", ex)
                    }
                } else {
                    Log.w("Firebase", "Hotel ID: $hotelId not found.")
                }
            }
    }


    private fun addInvoice(method: PaymentMethod, onComplete: (invoiceDocId: String?) -> Unit = {}) {
        val docId = bookingDocId ?: run {
            Toast.makeText(this, "Missing booking reference", Toast.LENGTH_SHORT).show()
            finish()
            onComplete(null)
            return
        }
        val invoices = firestore.collection("invoices")
        val newInvoiceRef = invoices.document()
        invoiceDocId = newInvoiceRef.id
        val invoiceUpdates = mapOf(
            "bookingId" to docId,
            "totalAmount" to totalAmountToPay,
            "paymentMethodId" to method.paymentMethodId
        )
        firestore.runBatch { batch ->
            batch.set(newInvoiceRef, invoiceUpdates, SetOptions.merge())
        }.addOnSuccessListener {
            Toast.makeText(this, "Payment confirmed", Toast.LENGTH_SHORT).show()
            onComplete(newInvoiceRef.id)
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
            val intent=Intent(this, ReceptionActivity::class.java).apply {
                flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
        dialog.setOnCancelListener{
            dialog.dismiss()
            val intent=Intent(this, ReceptionActivity::class.java).apply {
                flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }

        dialog.show()
    }
    fun convertTimestampToString(timestamp: Timestamp): String {
        val date: Date = timestamp.toDate()

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return dateFormat.format(date)
    }
}

