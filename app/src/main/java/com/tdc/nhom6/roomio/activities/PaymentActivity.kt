package com.tdc.nhom6.roomio.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.activities.HotelDetailActivity.Companion.db
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter
import com.tdc.nhom6.roomio.databinding.ActivityPaymentBinding
import com.tdc.nhom6.roomio.models.BankInfo
import com.tdc.nhom6.roomio.models.Booking
import com.tdc.nhom6.roomio.models.HotelModel
import com.tdc.nhom6.roomio.models.Invoice
import com.tdc.nhom6.roomio.models.RoomType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class PaymentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentBinding
    private lateinit var bookingId: String
    private var currentHotel: HotelModel? = null
    private var currentBooking: Booking?= null
    private val invoices: MutableList<Invoice> = mutableListOf()
    private var currentRoomType: RoomType? = null
    private var bookingListener: ListenerRegistration? = null
    private var invoicesListener: ListenerRegistration? = null
    private var roomTypeListener: ListenerRegistration? = null
    private var hotelListener: ListenerRegistration? = null
    private var currentOwnerBankInfo: BankInfo? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    @SuppressLint("SetTextI18n")
    private fun initial() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Payment"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bookingId = intent.getStringExtra("BOOKING_ID").toString()

        loadBooking(bookingId) {
            loadInvoice(bookingId) {
                val loadedBooking = currentBooking
                if (loadedBooking == null) {
                    Log.e("PaymentActivity", "Booking data was null after listener finished.")
                    return@loadInvoice
                }
                val startDate = loadedBooking.checkInDate?.toDate()?.time ?: 0L
                val endDate = loadedBooking.checkOutDate?.toDate()?.time ?: 0L
                val diff = endDate - startDate
                val numberOfNights = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)

                binding.tvHotelName.text = currentHotel?.hotelName ?: ""
                binding.ratingBar.rating = (currentHotel?.averageRating ?: 0).toFloat()

                val roomTypeName = currentRoomType?.typeName
                val guestCount = loadedBooking.numberGuest
                binding.tvBookingDetail.text =  "${roomTypeName} (${numberOfNights} night, ${guestCount} people )"

                binding.tvTotalAfter.text ="Total: ${RoomTypeAdapter.Format.formatCurrency(loadedBooking.totalFinal)}"

                if (invoices.isNotEmpty()) {
                    val invoice:Invoice = invoices.first()
                    binding.tvInvoiceId.text = "#ID: ${invoice.invoiceId}"
                    binding.tvCreateAt.text = convertTimestampToString(invoice.createdAt)

                    val bookingTotal = loadedBooking.totalFinal
                    val invoiceAmount = invoice.totalAmount

                    val percentagePaid = if (bookingTotal > 0) {
                        (invoiceAmount?.div(bookingTotal))!! * 100.0
                    } else 0.0

                    val formattedPercent = String.format("%.2f", percentagePaid)
                    binding.tvAmountPayment.text = "Payment: ${RoomTypeAdapter.Format.formatCurrency(
                        invoice.totalAmount!!
                    )} (${formattedPercent}%)"

                    generateQRPayment(invoice)
                } else {
                    Log.w("PaymentActivity", "No invoices found for Booking ID: $bookingId.")
                    binding.tvInvoiceId.text = "N/A"
                    binding.tvAmountPayment.text = "Payment required"
                }
            }
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
                        Log.e("Firebase", "Lỗi lắng nghe Bank Info: ", error)
                        return@addSnapshotListener
                    }

                    // CHỈ XỬ LÝ KHI CÓ DỮ LIỆU (snapShot không rỗng)
                    if (snapShot != null && !snapShot.isEmpty) {

                        // Lấy DocumentSnapshot đầu tiên (tài khoản mặc định) một cách an toàn
                        val documentSnapshot = snapShot.documents.firstOrNull()

                        try {
                            currentOwnerBankInfo = documentSnapshot?.toObject(BankInfo::class.java)

                            currentOwnerBankInfo?.let { info ->
                                val QR_URL =
                                    "https://img.vietqr.io/image/${currentOwnerBankInfo!!.bank_code}-${currentOwnerBankInfo!!.account_number}-compact2.png?amount=${invoice.totalAmount}&addInfo=Roomio_${invoice.invoiceId}"
                                Glide.with(this).load(QR_URL).into(binding.ivQrCode)
                                binding.tvTimer
                                Log.d("QR code", "Hien thi thanh cong ${QR_URL}")
                            }
                        } catch (ex: Exception) {
                            Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho BankInfo", ex)
                        }
                    } else {
                        Log.w("Firebase", "BankInfo không tồn tại.")
                    }
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
}