package com.tdc.nhom6.roomio.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.RadioButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.adapters.PaymentMethodAdapter
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter.Format
import com.tdc.nhom6.roomio.databinding.ActivityGuestDetailBinding
import com.tdc.nhom6.roomio.models.Booking
import com.tdc.nhom6.roomio.models.Discount
import com.tdc.nhom6.roomio.models.HotelModel
import com.tdc.nhom6.roomio.models.Invoice
import com.tdc.nhom6.roomio.models.PaymentMethod
import com.tdc.nhom6.roomio.models.RoomType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GuestDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGuestDetailBinding

    private val booking: Booking? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("BOOKING_DATA", Booking::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("BOOKING_DATA")
        }
    }

    private lateinit var paymentMethodAdapter: PaymentMethodAdapter
    private var listPaymentMethod: MutableList<PaymentMethod> = mutableListOf()
    val db = FirebaseFirestore.getInstance()

    private var currentRoomType: RoomType? = null
    private var currentHotel: HotelModel? = null
    private var currentDiscount: Discount? = null
    private var userWalletBalance: Double = 0.0
    private var requiredAmount: Double = 0.0

    private var walletListener: ListenerRegistration? = null
    private var paymentMethodsListener: ListenerRegistration? = null
    private var roomTypeListener: ListenerRegistration? = null
    private var hotelListener: ListenerRegistration? = null
    private var discountListener: ListenerRegistration? = null
    private var selectedMethodId:String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuestDetailBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Guest Detail"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        booking?.let { safeBooking ->
            startDataLoading(safeBooking)
            initial()
        } ?: run {
            Log.e("GuestDetail", "Lỗi: Không nhận được đối tượng Booking.")
        }
    }

    private fun initial() {
        paymentMethodAdapter = PaymentMethodAdapter(
            listPaymentMethod,
            requiredAmount,
            userWalletBalance
        )
        binding.recyclerPaymentMethod.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.recyclerPaymentMethod.adapter = paymentMethodAdapter

        paymentMethodAdapter.onPaymentMethodSelected = { selectedMethod ->
            booking?.let { safeBooking ->
                selectedMethodId = selectedMethod.paymentMethodId
                handleTravelWalletDiscount(safeBooking, selectedMethod.paymentMethodName)
            }
        }

        binding.btnPayment.setOnClickListener{
            addBookingAndPayment(booking)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        walletListener?.remove()
        paymentMethodsListener?.remove()
        roomTypeListener?.remove()
        hotelListener?.remove()
        discountListener?.remove()
    }

    private fun startDataLoading(booking: Booking) {
        loadRoomType(booking.roomTypeId, booking)

        loadUserWalletBalance(booking.customerId) { balance ->
            userWalletBalance = balance
            loadPaymentMethod(booking)
        }

        binding.groupFundAmount.setOnCheckedChangeListener { _, _ ->
            loadPaymentMethod(booking)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(booking: Booking) {

        currentHotel?.let {
            binding.tvHotelName.text = it.hotelName
            binding.address.text = it.hotelAddress
            binding.ratingBar.rating = it.averageRating.toFloat()
            binding.tvReviews.text = "(${it.totalReviews})"
        }

        binding.tvRoomType.text = currentRoomType?.typeName ?: "N/A"

        binding.tvCheckIn.text = booking.checkInDate?.let { convertTimestampToString(it) }
        binding.tvCheckOut.text = booking.checkOutDate?.let { convertTimestampToString(it) } ?: "N/A"

        binding.tvGuest.text = "${booking.numberGuest} people"

        updateTotalAmount(currentDiscount)
    }

    private fun addBookingAndPayment(booking: Booking?) {
        booking?.let { bookingData ->

            val checkedId = binding.groupFundAmount.checkedRadioButtonId

            val radioButton = findViewById<RadioButton>(checkedId)

            if (radioButton == null) {
                Log.e("Firebase", "Lỗi: Không tìm thấy RadioButton đã chọn.")
                return@let
            }

            val rawText = radioButton.text.toString()

            val cleanedForSplit = rawText.replace("[^\\d\\.,\\s]".toRegex(), "")

            val amountString = cleanedForSplit
                .split(" ")
                .firstOrNull { it.isNotEmpty() && it.first().isDigit() } ?: ""

            val valueWithoutSeparators = amountString.replace(".", "")
            val totalAmountValue = valueWithoutSeparators.toDoubleOrNull() ?: 0.0

            val safePaymentMethodId = selectedMethodId
            if (safePaymentMethodId == null) {
                Log.e("Firebase", "Lỗi: Phương thức thanh toán chưa được chọn.")
                return@let
            }

            db.collection("bookings")
                .add(bookingData)
                .addOnSuccessListener { documentReference ->
                    val newBookingId = documentReference.id
                    Log.d("Firebase","Thêm booking thành công. ID: $newBookingId")

                    val invoice = Invoice(
                        bookingId = newBookingId,
                        totalAmount = totalAmountValue,
                        paymentMethodId = safePaymentMethodId
                    )

                    db.collection("invoices")
                        .add(invoice)
                        .addOnSuccessListener { invoiceDocumentReference ->
                            Log.d("Firebase", "Thêm invoice thành công. ID: ${invoiceDocumentReference.id}")
                            val intent=Intent(this, PaymentActivity::class.java)
                            intent.putExtra("BOOKING_ID",newBookingId)
                            startActivity(intent)
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firebase", "Lỗi khi thêm invoice", e)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Lỗi khi thêm booking", e)
                }
        }
    }
    private fun updateTotalAmount(discount: Discount?) {
        val safeBooking = booking ?: return

        binding.tvTotalOrigin.text = Format.formatCurrency(safeBooking.totalOrigin)

        var finalPrice = safeBooking.totalOrigin
        var discountAmount = 0.0
        var hasDiscount = false

        binding.tvDiscountName.text = "Không có"
        binding.tvDiscountPrice.text = Format.formatCurrency(0.0)

        discount?.let { safeDiscount ->
            safeDiscount.discountValue?.let { discountPercent ->
                discountAmount = (safeBooking.totalOrigin * discountPercent) / 100.0
                finalPrice = safeBooking.totalOrigin - discountAmount
                hasDiscount = true

                binding.tvDiscountPrice.text = "- "+Format.formatCurrency(discountAmount)
                binding.tvDiscountName.text = safeDiscount.discountName
            }
        }

        binding.tvTotalAfter.text = Format.formatCurrency(finalPrice)
        safeBooking.totalFinal = finalPrice

        if (hasDiscount && finalPrice != safeBooking.totalOrigin) {
            binding.layoutTotalFinal.isVisible = true
        } else {
            binding.layoutTotalFinal.isVisible = false
        }

        binding.radFund10.text = Format.formatCurrency((finalPrice * 10.0) / 100.0) + " (10%)"
        binding.radFund100.text = Format.formatCurrency(finalPrice) + " (Full)"
    }

    private fun handleTravelWalletDiscount(booking: Booking, selectedMethodName: String) {
        var targetDiscountId = booking.discountId

        if (selectedMethodName == "Travel wallet") {
            db.collection("bookings")
                .whereEqualTo("customerId", booking.customerId)
                .limit(1)
                .get()
                .addOnSuccessListener { bookingsResult ->
                    if (bookingsResult.isEmpty) {
                        targetDiscountId = "0"
                        loadFinalDiscountAndRefreshUI(targetDiscountId)
                    } else {
                        db.collection("invoices")
                            .whereEqualTo("paymentMethodId", "Travel wallet")
                            .limit(1)
                            .get()
                            .addOnSuccessListener { invoicesResult ->
                                if (invoicesResult.isEmpty) {
                                    targetDiscountId = "0"
                                }
                                loadFinalDiscountAndRefreshUI(targetDiscountId)
                            }
                            .addOnFailureListener { Log.e("Firestore", "Lỗi kiểm tra Invoices", it) }
                    }
                }
                .addOnFailureListener { Log.e("Firestore", "Lỗi kiểm tra Bookings", it) }
        } else {
            loadFinalDiscountAndRefreshUI(targetDiscountId)
        }
    }

    private fun loadFinalDiscountAndRefreshUI(discountId: String?) {
        if (discountId != null) {
            loadDiscount(discountId) {}
        } else {
            discountListener?.remove()
            currentDiscount = null
            updateTotalAmount(null)
            loadPaymentMethod(booking!!)
        }
    }

    private fun loadPaymentMethod(booking: Booking) {
        requiredAmount = if (binding.radFund10.isChecked) {
            (booking.totalFinal * 10.0) / 100.0
        } else {
            booking.totalFinal
        }

        if (!::paymentMethodAdapter.isInitialized) {
            paymentMethodAdapter = PaymentMethodAdapter(
                listPaymentMethod,
                requiredAmount,
                userWalletBalance
            )
            binding.recyclerPaymentMethod.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            binding.recyclerPaymentMethod.adapter = paymentMethodAdapter

            paymentMethodAdapter.onPaymentMethodSelected = { selectedMethod ->
                booking?.let { safeBooking ->
                    handleTravelWalletDiscount(safeBooking, selectedMethod.paymentMethodName)
                }
            }
        } else {
            paymentMethodAdapter.updateRequiredAmount(requiredAmount)
            paymentMethodAdapter.updateWalletBalance(userWalletBalance)
        }

        paymentMethodsListener?.remove()
        paymentMethodsListener = db.collection("paymentMethods")
            .addSnapshotListener { result, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Lỗi khi lắng nghe PaymentMethods: ", exception)
                    return@addSnapshotListener
                }

                if (result != null) {
                    listPaymentMethod.clear()

                    for (document in result) {
                        try {
                            val paymentMethod = document.toObject(PaymentMethod::class.java)
                            listPaymentMethod.add(paymentMethod)
                        } catch (ex: Exception) {
                            Log.e("PaymentError", "FAILURE converting document ID: ${document.id}", ex)
                        }
                    }

                    paymentMethodAdapter.notifyDataSetChanged()
                }
            }
    }

    private fun loadUserWalletBalance(customerId: String, onSuccess: (Double) -> Unit) {
        walletListener?.remove()
        walletListener = db.collection("users")
            .document(customerId)
            .addSnapshotListener { snapShot, error ->
                if (error != null) {
                    Log.e("Firestore", "Lỗi lắng nghe số dư ví người dùng", error)
                    onSuccess(0.0)
                    return@addSnapshotListener
                }

                if (snapShot != null && snapShot.exists()) {
                    val balance = snapShot.getDouble("walletBalance") ?: 0.0
                    onSuccess(balance)
                } else {
                    onSuccess(0.0)
                }
            }
    }

    private fun loadRoomType(roomTypeId: String, booking: Booking) {
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
                            loadHotel(roomType.hotelId, booking)
                        }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho RoomType: ${dataSnapshot.id}", ex)
                    }
                } else {
                    Log.w("Firebase", "RoomType ID: $roomTypeId không tồn tại.")
                }
            }
    }

    private fun loadHotel(hotelId: String, booking: Booking) {
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
                            if (booking.discountId != null) {
                                loadDiscount(booking.discountId!!) {}
                            } else {
                                currentDiscount = null
                                updateUI(booking)
                            }
                        }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho Hotel: ${dataSnapshot.id}", ex)
                    }
                } else {
                    Log.w("Firebase", "Hotel ID: $hotelId không tồn tại.")
                }
            }
    }

    fun loadDiscount(discountId: String, onSuccess: (Discount?) -> Unit) {
        discountListener?.remove()

        discountListener = db.collection("discounts")
            .document(discountId)
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
                        booking?.let { updateUI(it) }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho Discount: ${dataSnapshot.id}", ex)
                        onSuccess(null)
                    }
                } else {
                    Log.w("Firebase", "Discount ID: $discountId không tồn tại.")
                    currentDiscount = null
                    onSuccess(null)
                    booking?.let { updateUI(it) }
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