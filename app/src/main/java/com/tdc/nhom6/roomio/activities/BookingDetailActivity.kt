package com.tdc.nhom6.roomio.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter.Format
import com.tdc.nhom6.roomio.databinding.ActivityBookingDetailBinding
import com.tdc.nhom6.roomio.databinding.DialogPaymentConfirmBinding
import com.tdc.nhom6.roomio.models.Booking
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

                binding.tvBookingDetail.text =  currentRoomType?.typeName
                binding.tvNumberGuest.text= "Guest: ${loadedBooking.numberGuest} people"

                binding.tvCheckInDate.text= currentBooking?.checkInDate?.let { convertTimestampToString(it) }
                binding.tvCheckOutDate.text= currentBooking?.checkOutDate?.let { convertTimestampToString(it) }

                binding.tvTotalAfter.text =RoomTypeAdapter.Format.formatCurrency(loadedBooking.totalFinal)

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
                            binding.progressBar.visibility = View.VISIBLE
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
                        binding.tvBookingStatus.text = "Status Unknown" // Set a default/error text
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

                } else {
                    Log.w("PaymentActivity", "No invoices found for Booking ID: $bookingId.")
                    binding.tvFundAmount.text = "N/A"
                }
            }
        }
    }

    private fun cancelTransaction(customerId: String, ownerId: String) {
        val amount = invoices.first().totalAmount

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
            transaction.update(bookingRef, "status", "cancelled")

            null
        }
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Log.d("Payment", "Transaction success: Refund processed and booking cancelled.")
            }.addOnFailureListener { e ->
                Log.w("Payment", "Transaction failure.", e)
            }
    }

    private fun openDialogCancelConfirm(customerId: String, ownerId: String) {

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Are you sure you want to cancel your reservation?")

            .setPositiveButton("sure") { dialog, which ->
                dialog.dismiss()
                cancelTransaction(customerId,ownerId)
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

    private fun loadBooking(bookingId: String, onComplete: () -> Unit) { // MODIFIED: onComplete instead of onLoaded(Booking)
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
                    onComplete()
                }
            }
    }

    private fun loadRoomType(roomTypeId: String, onComplete: () -> Unit) { // ADDED CALLBACK
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

                            // *** PASS THE CALLBACK TO THE NEXT STEP ***
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

    private fun loadHotel(hotelId: String, onComplete: () -> Unit) { // ADDED CALLBACK
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

                            // *** DATA LOAD COMPLETE: EXECUTE THE FINAL CALLBACK ***
                            onComplete()
                        }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho Hotel: ${dataSnapshot.id}", ex)
                    }
                } else {
                    Log.w("Firebase", "Hotel ID: $hotelId không tồn tại.")
                }
            }
    }

    fun convertTimestampToString(timestamp: Timestamp): String {
        // 1. Chuyển Timestamp thành đối tượng Date
        val date: Date = timestamp.toDate()

        // 2. Tạo đối tượng SimpleDateFormat với định dạng và Locale (ngôn ngữ) mong muốn
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        // 3. Định dạng Date thành String
        return dateFormat.format(date)
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