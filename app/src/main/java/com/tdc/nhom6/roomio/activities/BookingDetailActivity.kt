package com.tdc.nhom6.roomio.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter.Format
import com.tdc.nhom6.roomio.databinding.ActivityBookingDetailBinding
import com.tdc.nhom6.roomio.models.Booking
import com.tdc.nhom6.roomio.models.Discount
import com.tdc.nhom6.roomio.models.DiscountPaymentMethod
import com.tdc.nhom6.roomio.models.HotelModel
import com.tdc.nhom6.roomio.models.Invoice
import com.tdc.nhom6.roomio.models.PaymentMethod
import com.tdc.nhom6.roomio.models.RoomType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookingDetailActivity : AppCompatActivity() {
    private lateinit var binding:ActivityBookingDetailBinding
    private lateinit var bookingId: String
    private var currentHotel: HotelModel? = null
    private var currentBooking: Booking?= null
    private val invoices: MutableList<Invoice> = mutableListOf()
    private var currentRoomType: RoomType? = null
    private var bookingListener: ListenerRegistration? = null
    private var invoicesListener: ListenerRegistration? = null
    private var roomTypeListener: ListenerRegistration? = null
    private var hotelListener: ListenerRegistration? = null
    private var paymentMethodListener: ListenerRegistration? = null
    private var currentDiscountPM: DiscountPaymentMethod? = null
    private var currentDiscount: Discount?=null
    private var discountPMListener: ListenerRegistration? = null
    private var discountListener: ListenerRegistration? = null
    val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBookingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initial()

    }

    private fun initial() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Booking details"
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

        loadBooking(bookingId) {
            loadInvoice(bookingId) {
                val loadedBooking = currentBooking
                if (loadedBooking == null) {
                    Log.e("PaymentActivity", "Booking data was null after listener finished.")
                    return@loadInvoice
                }

                binding.tvHotelName.text = currentHotel?.hotelName ?: ""
                binding.tvAddress.text=currentHotel?.hotelAddress
                binding.ratingBar.rating = (currentHotel?.averageRating ?: 0).toFloat()
                binding.tvReviews.text = "${currentHotel?.totalReviews} reviews"
                binding.tvSpecialRequest.text=loadedBooking.note

                binding.tvBookingDetail.text =  currentRoomType?.typeName
                binding.tvNumberGuest.text= "Guest: ${loadedBooking.numberGuest} people"

                binding.tvCheckInDate.text= currentBooking?.checkInDate?.let { convertTimestampToString(it) }
                binding.tvCheckOutDate.text= currentBooking?.checkOutDate?.let { convertTimestampToString(it) }

                val ownerId = currentHotel?.ownerId

                if (ownerId != null) {
                    db.collection("users").document(ownerId).get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                val ownerName = document.getString("username")
                                val ownerPhone = document.getString("phone")

                                binding.tvOwnerName.text = ownerName ?: "N/A"

                                binding.tvOwnerPhone.text = ownerPhone ?: "Contact not available"
                            } else {
                                binding.tvOwnerName.text = "Owner not found"
                                binding.tvOwnerPhone.text = "N/A"
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("BookingDetail", "Error fetching owner details", exception)
                            binding.tvOwnerName.text = "Error loading data"
                            binding.tvOwnerPhone.text = "N/A"
                        }
                }

                binding.tvCancelPolicy.setOnClickListener {
                    val hotelId = currentHotel?.hotelId

                    if (hotelId != null) {
                        showCancellationPolicyDialog(hotelId)
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Notice")
                            .setMessage("Unable to load cancellation policy information at this time.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

                updateTotalAmount(loadedBooking)

                Log.d("status",loadedBooking?.status.toString())
                when(loadedBooking?.status){
                    "pending"-> {
                        binding.tvBookingStatus.text = "Reservation is pending confirmation"
                        binding.tvBookingStatus.setBackgroundColor(getColor(R.color.yellow))
                        binding.btnAction.text="Payment to complete"
                        binding.btnAction.setTextColor(getColor(R.color.white))
                        binding.btnAction.setBackgroundColor(getColor(R.color.blue))
                        binding.tvDescriptionStatus.visibility= View.VISIBLE
                        binding.btnAction.setOnClickListener{
                            val intent= Intent(this,PaymentActivity::class.java).apply {
                                flags= Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            intent.putExtra("BOOKING_ID",bookingId)
                            startActivity(intent)
                        }
                    }
                    "confirmed"-> {
                        binding.tvBookingStatus.text = "Reservation is comfirmed"
                        binding.tvBookingStatus.setBackgroundColor(getColor(R.color.green))
                        binding.btnAction.text="Cancellation and refund"
                        binding.btnAction.setTextColor(getColor(R.color.text_dark_gray))
                        binding.btnAction.background=getDrawable(R.drawable.shape_foreground_radius)
                        binding.btnAction.backgroundTintList = ColorStateList.valueOf(getColor(R.color.text_dark_gray))
                        binding.btnAction.setOnClickListener {
                            binding.progressBar.visibility = View.GONE
                            currentHotel?.let { hotel ->
                                currentBooking?.let { booking ->
                                    openDialogCancelConfirm(booking.customerId, hotel.ownerId)
                                }
                            }
                        }
                    }

                    "completed"-> {
                        binding.tvBookingStatus.text = "Reservation is completed"
                        binding.tvBookingStatus.setBackgroundColor(getColor(R.color.blue))
                        binding.btnAction.text="Booking review"
                        binding.btnAction.setTextColor(getColor(R.color.yellow))
                        binding.btnAction.background=getDrawable(R.drawable.shape_foreground_radius)
                        binding.btnAction.backgroundTintList = ColorStateList.valueOf(getColor(R.color.yellow))
                        binding.btnAction.setOnClickListener {
                            val intent = Intent(this, ReviewActivity::class.java)
                            intent.putExtra("HOTEL_ID", currentRoomType?.hotelId)
                            intent.putExtra("BOOKING_ID", bookingId)
                            startActivity(intent)
                        }

                    }
                    "expired"-> {
                        binding.tvBookingStatus.text = "Since you have paid 100% deposit for the booking value, you will be refunded 50% of the amount paid."
                        binding.tvBookingStatus.setBackgroundColor(getColor(R.color.red))
                        binding.btnAction.text="Refund request"
                        binding.btnAction.setTextColor(getColor(R.color.white))
                        binding.btnAction.setBackgroundColor(getColor(R.color.text_dark_gray))
                    }
                    "cancelled"-> {
                        binding.tvBookingStatus.text = "Reservation is cancelled"
                        binding.tvBookingStatus.setBackgroundColor(getColor(R.color.text_dark_gray))
                        binding.btnAction.text="Rebook"
                        binding.btnAction.setTextColor(getColor(R.color.text_dark_gray))
                        binding.btnAction.background=getDrawable(R.drawable.shape_foreground_radius)
                        binding.btnAction.backgroundTintList = ColorStateList.valueOf(getColor(R.color.text_dark_gray))
                        binding.btnAction.setOnClickListener{
                            val intent= Intent(this,HotelDetailActivity::class.java).apply {
                                flags= Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            intent.putExtra("HOTEL_ID", currentRoomType?.hotelId)
                            startActivity(intent)
                        }
                    }
                    else -> {
                        Log.e("PaymentError", "Unhandled status or status is null: ${loadedBooking?.status}")
                        binding.tvBookingStatus.text = "Status Unknown"
                    }
                }

                if (invoices.isNotEmpty()) {
                    val invoice:Invoice = invoices.first()
                    binding.tvBookingId.text = loadedBooking.bookingId
                    binding.tvCreateAt.text = convertTimestampToString(loadedBooking.createdAt)

                    val bookingTotal = loadedBooking.totalFinal
                    val invoiceAmount = invoice.totalAmount

                    val percentagePaid = if (bookingTotal > 0) {
                        (invoiceAmount?.div(bookingTotal))!! * 100.0
                    } else 0.0

                    binding.tvFundAmount.text = "${
                        RoomTypeAdapter.Format.formatCurrency(
                            invoice.totalAmount!!
                        )} (${percentagePaid.toInt()}%)"

                    paymentMethodListener = getPaymentMethodName(invoice.paymentMethodId){ name ->
                        binding.tvPaymentMethod.text=name
                    }
                    var status = if (invoice.paymentStatus == "payment_pending") "not yet paid" else "paid"
                    binding.tvPaymentStatus.text = status

                    if (status == "paid") {
                        binding.tvPaymentStatus.setTextColor(ContextCompat.getColor(this, R.color.green))
                    } else {
                        binding.tvPaymentStatus.setTextColor(ContextCompat.getColor(this, R.color.yellow))
                    }

                } else {
                    Log.w("PaymentActivity", "No invoices found for Booking ID: $bookingId.")
                    binding.tvFundAmount.text = "N/A"
                }
            }
        }
    }

    private fun updateTotalAmount(booking: Booking) {
        val safeBooking = booking ?: return

        binding.tvTotalOrigin.text = Format.formatCurrency(safeBooking.totalOrigin)

        var priceAfterHotelDiscount = safeBooking.totalOrigin
        var finalPrice = safeBooking.totalOrigin
        var totalDiscountAmount = 0.0

        binding.tvDiscountName.text = "Không có"
        binding.tvDiscountPrice.text = Format.formatCurrency(0.0)

        var discountNameList = mutableListOf<String>()

        currentDiscount?.let { hotelDiscount ->
            hotelDiscount.discountPercent?.let { discountPercent ->
                // Áp dụng giảm giá Khách sạn trực tiếp
                var calculatedDiscountAmount = (safeBooking.totalOrigin * discountPercent) / 100.0

                priceAfterHotelDiscount = safeBooking.totalOrigin - calculatedDiscountAmount
                totalDiscountAmount += calculatedDiscountAmount

                discountNameList.add(hotelDiscount.discountName ?: "Giảm giá Khách sạn")
            }
        }

        finalPrice = priceAfterHotelDiscount

        currentDiscountPM?.let { pmDiscount ->
            pmDiscount.discountPercent?.let { discountPercent ->
                // Giảm giá Phương thức thanh toán áp dụng trên giá sau giảm Khách sạn
                val amount = (priceAfterHotelDiscount * discountPercent) / 100.0
                finalPrice -= amount
                totalDiscountAmount += amount

                discountNameList.add(pmDiscount.discountName ?: "Giảm giá Thanh toán")
            }
        }

        // --- HIỂN THỊ KẾT QUẢ CUỐI CÙNG TRÊN UI ---
        if (totalDiscountAmount > 0) {
            binding.tvDiscountName.text = discountNameList.joinToString("\n")
            binding.tvDiscountPrice.text = "- " + Format.formatCurrency(totalDiscountAmount)
        } else {
            binding.tvDiscountName.text = "Không có"
            binding.tvDiscountPrice.text = Format.formatCurrency(0.0)
        }

        binding.tvTotalAfter.text = Format.formatCurrency(finalPrice)

        safeBooking.totalFinal = finalPrice

        if (totalDiscountAmount > 0 && finalPrice != safeBooking.totalOrigin) {
            binding.layoutTotalFinal.isVisible = true
        } else {
            binding.layoutTotalFinal.isVisible = false
        }
    }

    private fun cancelTransaction(customerId: String, ownerId: String, amountToRefund: Double) {
        if (amountToRefund <= 0) {
            Log.w("Payment", "Transaction skipped: Refund amount is zero or less.")
            updateBookingStatusToCancelled()
            return
        }

        val amount = amountToRefund

        db.runTransaction { transaction ->
            val userRef = db.collection("users").document(customerId)
            val ownerRef = db.collection("users").document(ownerId)
            val bookingRef = db.collection("bookings").document(bookingId)

            val userSnapshot = transaction.get(userRef)
            val ownerSnapshot = transaction.get(ownerRef)

            val currentCustomerBalance = userSnapshot.getDouble("walletBalance")
            val currentOwnerBalance = ownerSnapshot.getDouble("walletBalance")

            if (currentCustomerBalance == null || currentOwnerBalance == null || amount == null) {
                throw IllegalStateException("Missing balance or amount data for refund.")
            }

            if (currentOwnerBalance < amount) {
                throw IllegalStateException("Owner has insufficient balance to process refund.")
            }

            val newCustomerBalance = currentCustomerBalance.plus(amount)
            val newOwnerBalance = currentOwnerBalance.minus(amount)

            transaction.update(userRef, "walletBalance", newCustomerBalance)
            transaction.update(ownerRef, "walletBalance", newOwnerBalance)
            null
        }
            .addOnSuccessListener {
                updateBookingStatusToCancelled()
            }.addOnFailureListener { e ->
                Log.w("Payment", "Transaction failure.", e)
            }
    }

    private fun updateBookingStatusToCancelled() {
        val roomId = currentBooking?.roomId
        val hotelId = currentHotel?.hotelId

        if (roomId.isNullOrEmpty() || hotelId.isNullOrEmpty()) {
            Log.e("Payment", "Cannot cancel: Missing Room ID or Hotel ID.")
            binding.progressBar.visibility = View.GONE
            return
        }

        val batch = db.batch()

        val bookingRef = db.collection("bookings").document(bookingId)
        val roomRef = db.collection("hotels").document(hotelId).collection("rooms").document(roomId)

        batch.update(bookingRef, "status", "cancelled")

        batch.update(roomRef, "status_id", "room_available")

        batch.commit()
            .addOnSuccessListener {
                Log.d("Payment", "Booking cancelled and Room is available.")
            }
            .addOnFailureListener { e ->
                Log.w("Payment", "Data is consistent", e)
            }
            .addOnCompleteListener {
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun calculateRefundAmount(): Double {
        val booking = currentBooking ?: return 0.0
        val totalPaid = invoices.firstOrNull()?.totalAmount ?: 0.0
        val checkInTime = booking.checkInDate?.toDate()?.time
        val currentTime = System.currentTimeMillis()

        val timeDifference = checkInTime?.minus(currentTime)
        val twentyFourHoursInMillis = 24 * 60 * 60 * 1000L

        if (timeDifference != null) {
            return if (timeDifference > twentyFourHoursInMillis) {
                totalPaid
            } else {
                0.0
            }
        }
        return 0.0
    }

    private fun openDialogCancelConfirm(customerId: String, ownerId: String) {
        val refundAmount = calculateRefundAmount()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Are you sure you want to cancel your reservation?")

            .setPositiveButton("sure") { dialog, which ->
                dialog.dismiss()
                cancelTransaction(customerId,ownerId,refundAmount)
            }

            .setNegativeButton("cancel") { dialog, which ->
                dialog.dismiss()
            }
            .show()
    }

    private fun getPaymentMethodName(methodId: String, onResult: (String?) -> Unit): ListenerRegistration {
        return db.collection("paymentMethods")
            .whereEqualTo("paymentMethodId", methodId)
            .addSnapshotListener { querySnapshot, exception ->

                if (exception != null) {
                    Log.e("PaymentError", "Listener failed:", exception)
                    onResult(null)
                    return@addSnapshotListener
                }

                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    val document = querySnapshot.documents.first()
                    try {
                        val paymentMethod = document.toObject(PaymentMethod::class.java)
                        onResult(paymentMethod?.paymentMethodName)
                    } catch (ex: Exception) {
                        Log.e("PaymentError", "FAILURE converting document ID: ${document.id}", ex)
                        onResult(null)
                    }
                } else {
                    Log.w("PaymentInfo", "No document found for methodId: $methodId")
                    onResult(null)
                }
            }
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
                        Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho Booking: ${dataSnapshot.id}", ex)
                    }
                } else {
                    Log.w("Firebase", "Booking ID: $bookingId không tồn tại.")
                }
            }
    }
    private fun loadInvoice(bookingId: String, onComplete: () -> Unit) {
        invoicesListener?.remove()

        invoicesListener = db.collection("invoices")
            .whereEqualTo("bookingId",bookingId)
            .addSnapshotListener { result, exception ->

                if (result != null) {
                    invoices.clear()
                    for (document in result) {
                        try {
                            val invoice = document.toObject(Invoice::class.java)
                            invoices.add(invoice)
                        } catch (ex: Exception) {
                            Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho Invoice: ${document.id}", ex)
                        }
                    }
                    val shouldShowExtraFields = invoices.size > 1

                    binding.layoutExtraFeeContainer.isVisible = shouldShowExtraFields
                    binding.layoutTotalPaidContainer.isVisible = shouldShowExtraFields

                    if (shouldShowExtraFields) {
                        updateExtraFeeAndTotalPaid()
                    }
                    onComplete()
                }
            }

    }

    private fun updateExtraFeeAndTotalPaid() {
        val booking = currentBooking ?: return

        var totalPaidAmount = 0.0
        for (invoice in invoices) {
            if (invoice.paymentStatus == "paid") {
                totalPaidAmount += invoice.totalAmount
            }
        }

        val fundAmount = invoices.firstOrNull()?.totalAmount ?: 0.0

        val totalAfterDiscount = booking.totalFinal

        val extraFee = totalPaidAmount + fundAmount - totalAfterDiscount

        binding.tvExtraFee.text = Format.formatCurrency(extraFee)
        binding.tvTotalPaidAmount.text = Format.formatCurrency(totalPaidAmount)
    }

    private fun loadRoomType(roomTypeId: String, onComplete: () -> Unit) {
        roomTypeListener?.remove()

        roomTypeListener = db.collection("roomTypes")
            .document(roomTypeId)
            .addSnapshotListener { dataSnapshot, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Lỗi khi lắng nghe RoomType: ", exception)
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
                        Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho RoomType: ${dataSnapshot.id}", ex)
                    }
                } else {
                    Log.w("Firebase", "RoomType ID: $roomTypeId không tồn tại.")
                }
            }
    }

    private fun loadHotel(hotelId: String, onComplete: () -> Unit) {
        hotelListener?.remove()

        hotelListener = db.collection("hotels")
            .document(hotelId)
            .addSnapshotListener { dataSnapshot, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Lỗi khi lắng nghe Hotel: ", exception)
                    return@addSnapshotListener
                }

                if (dataSnapshot != null && dataSnapshot.exists()) {
                    try {
                        val hotel = dataSnapshot.toObject(HotelModel::class.java)
                        if (hotel != null) {
                            currentHotel = hotel

                            loadDiscountsAndRefresh(onComplete)
                        }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho Hotel: ${dataSnapshot.id}", ex)
                    }
                } else {
                    Log.w("Firebase", "Hotel ID: $hotelId không tồn tại.")
                }
            }
    }

    private fun loadDiscountsAndRefresh(onComplete: () -> Unit) {
        val booking = currentBooking
        if (booking == null) {
            onComplete()
            return
        }

        val hotelDiscountId = booking.discountId
        if (!hotelDiscountId.isNullOrEmpty()) {
            loadDiscountHotel(hotelDiscountId) {
            }
        }

        val pmDiscountId = booking.discountPaymentMethodId
        if (!pmDiscountId.isNullOrEmpty()) {
            loadDiscountPM(pmDiscountId) {
            }
        }

        onComplete()
    }

    fun loadDiscountPM(discountPaymentMethodId: String, onSuccess: (DiscountPaymentMethod?) -> Unit) {
        discountPMListener?.remove()

        discountPMListener = db.collection("discountPaymentMethods")
            .document(discountPaymentMethodId)
            .addSnapshotListener { dataSnapshot, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Lỗi khi lắng nghe DiscountPaymentMethod: ", exception)
                    onSuccess(null)
                    return@addSnapshotListener
                }

                if (dataSnapshot != null && dataSnapshot.exists()) {
                    try {
                        val discount = dataSnapshot.toObject(DiscountPaymentMethod::class.java)
                        currentDiscountPM = discount
                        onSuccess(discount)
                        currentBooking?.let { updateTotalAmount(it) }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho DiscountPaymentMethod: ${dataSnapshot.id}", ex)
                        onSuccess(null)
                    }
                } else {
                    Log.w("Firebase", "DiscountPaymentMethod ID: $discountPaymentMethodId không tồn tại.")
                    currentDiscountPM = null
                    onSuccess(null)
                    currentBooking?.let { updateTotalAmount(it) }
                }
            }
    }

    fun loadDiscountHotel(discountHotelId: String, onSuccess: (Discount?) -> Unit) {
        discountListener?.remove()

        val hotel = currentHotel
        if (hotel == null) {
            Log.e("Firebase", "Lỗi: currentHotel là null. Không thể load Discount.")
            onSuccess(null)
            return
        }

        discountListener = db.collection("hotels")
            .document(hotel.hotelId)
            .collection("discounts")
            .document(discountHotelId)
            .addSnapshotListener { dataSnapshot, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Lỗi khi lắng nghe Discount: ", exception)
                    onSuccess(null)
                    return@addSnapshotListener
                }

                if (dataSnapshot != null && dataSnapshot.exists()) {
                    try {
                        val discount = dataSnapshot.toObject(Discount::class.java)
                        currentDiscount = discount
                        onSuccess(discount)
                        currentBooking?.let { updateTotalAmount(it) }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho Discount: ${dataSnapshot.id}", ex)
                        onSuccess(null)
                    }
                } else {
                    Log.w("Firebase", "Discount ID: $discountHotelId không tồn tại trong Subcollection.")
                    currentDiscount = null
                    onSuccess(null)
                    currentBooking?.let { updateTotalAmount(it) }
                }
            }
    }

    private fun showCancellationPolicyDialog(hotelId: String) {
        val htmlPolicyText = """        
        <br><br>
        1. Cancellation <b>more than 24 hours</b> before Check-in: 
        <b><font color="#388E3C">100% refund of deposit.</font></b>
        
        <br><br>
        2. Cancellation <b>within 24 hours</b> before Check-in: 
        <b><font color="#D32F2F">No deposit refund (0%).</font></b>
        
        <br><br>
        3. <b>No-Show</b> (Not arriving): 
        <b><font color="#D32F2F">No deposit refund (0%).</font></b>
        
        <br><br>
        <font color="#808080">Please contact support if you require further details.</font>
    """.trimIndent()

        val formattedText = HtmlCompat.fromHtml(htmlPolicyText, HtmlCompat.FROM_HTML_MODE_LEGACY)

        AlertDialog.Builder(this)
            .setTitle("Cancellation Policy")
            .setMessage(formattedText)
            .setPositiveButton("I Understand", null)
            .show()
    }

    fun convertTimestampToString(timestamp: Timestamp): String {
        val date: Date = timestamp.toDate()

        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        return dateFormatter.format(date)
    }

    override fun onDestroy() {
        super.onDestroy()
        bookingListener?.remove()
        invoicesListener?.remove()
        roomTypeListener?.remove()
        hotelListener?.remove()
        paymentMethodListener?.remove()
    }
}