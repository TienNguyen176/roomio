package com.tdc.nhom6.roomio.activities.booking

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.activities.hotel.HotelDetailActivity.Companion.db
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter
import com.tdc.nhom6.roomio.databinding.ActivityPaymentBinding
import com.tdc.nhom6.roomio.models.BankInfo
import com.tdc.nhom6.roomio.models.Booking
import com.tdc.nhom6.roomio.models.HotelModel
import com.tdc.nhom6.roomio.models.Invoice
import com.tdc.nhom6.roomio.models.RoomType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.google.firebase.firestore.Query
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.MainActivity
import com.tdc.nhom6.roomio.activities.receptionist.ReceptionActivity

class PaymentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentBinding
    private lateinit var bookingId: String
    private lateinit var invoiceId: String
    private lateinit var countDownTimer: CountDownTimer

    private var currentHotel: HotelModel? = null
    private var currentBooking: Booking?= null
    private var currentRoomType: RoomType? = null
    private var currentOwnerBankInfo: BankInfo? = null

    private var bookingListener: ListenerRegistration? = null
    private var invoiceDetailListener: ListenerRegistration? = null
    private var roomTypeListener: ListenerRegistration? = null
    private var hotelListener: ListenerRegistration? = null
    private var bookingDocId: String? = null
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val DURATION_24_HOURS_MS = 86400000L
    private val INTERVAL_MS = 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookingDocId = intent.getStringExtra("BOOKING_ID")?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra("RESERVATION_ID")?.takeIf { it.isNotBlank() }
        binding=ActivityPaymentBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets

        }

        initial()
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
                val docId = bookingDocId?.takeIf { it.isNotBlank() }
                    ?: intent.getStringExtra("RESERVATION_ID")?.takeIf { it.isNotBlank() }

                if (docId.isNullOrBlank()) {
                    Toast.makeText(this, "Missing booking reference", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                firestore.collection("invoices")
                    .whereEqualTo("bookingId", docId)
                    .get()
                    .addOnSuccessListener { invoiceSnapshots ->
                        val invoiceCount = invoiceSnapshots.size()

                        firestore.collection("bookings").document(docId)
                            .update("status", "completed")
                            .addOnSuccessListener {

                                invoiceSnapshots.documents.forEach {
                                    it.reference.update("paymentStatus", "paid")
                                }

                                // Navigate based on invoice count
                                val intent = if (invoiceCount > 1) {
                                    Intent(this, ReceptionActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                } else {
                                    Intent(this, BookingDetailActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        putExtra("BOOKING_ID", docId)
                                    }
                                }
                                startActivity(intent)
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to update booking status", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to check invoices", Toast.LENGTH_SHORT).show()
                    }
            }
//            findViewById<MaterialButton>(R.id.btnDone)?.setOnClickListener {
//
//                val docId = bookingDocId?.takeIf { it.isNotBlank() }
//                    ?: intent.getStringExtra("RESERVATION_ID")?.takeIf { it.isNotBlank() }
//
//                if (docId.isNullOrBlank()) {
//                    Toast.makeText(this, "Missing booking reference", Toast.LENGTH_SHORT).show()
//                    return@setOnClickListener
//                }
//
//
//                firestore.collection("invoices")
//                    .whereEqualTo("bookingId", docId)
//                    .get()
//                    .addOnSuccessListener { invoiceSnapshots ->
//                        val invoiceCount = invoiceSnapshots.size()
//
//
//                        firestore.collection("bookings").document(docId)
//                            .update("status", "completed")
//                            .addOnSuccessListener {
//
//                                invoiceSnapshots.documents.forEach {
//                                    it.reference.update("paymentStatus", "paid")
//                                }
//
//
//                                val intent = if (invoiceCount > 1) {
//                                    Intent(this, ReceptionActivity::class.java).apply {
//                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//                                    }
//                                } else {
//                                    Intent(this, BookingDetailActivity::class.java).apply {
//                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//                                        putExtra("BOOKING_ID", docId)
//                                    }
//                                }
//                                startActivity(intent)
//                            }
//                            .addOnFailureListener { e ->
//                                Toast.makeText(this, "Failed to update booking status: ${e.message}", Toast.LENGTH_LONG).show()
//                                Log.e("PaymentDetails", "Failed to update booking status", e)
//                            }
//                    }
//                    .addOnFailureListener { e ->
//                        Toast.makeText(this, "Failed to check invoices: ${e.message}", Toast.LENGTH_SHORT).show()
//                        Log.e("PaymentDetails", "Failed to check invoices", e)
//                    }
//            }
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
            binding.tvAmountPayment.text = "Payment: ${RoomTypeAdapter.Format.formatCurrency(
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

    fun convertTimestampToString(timestamp: Timestamp): String {
        val date: Date = timestamp.toDate()

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return dateFormat.format(date)
    }
}