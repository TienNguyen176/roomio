package com.tdc.nhom6.roomio.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.adapters.PaymentMethodAdapter
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter.Format
import com.tdc.nhom6.roomio.databinding.ActivityGuestDetailBinding
import com.tdc.nhom6.roomio.databinding.DialogPaymentConfirmBinding
import com.tdc.nhom6.roomio.databinding.DialogPaymentSuccessBinding
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
    private var currentDiscountPM: DiscountPaymentMethod? = null
    private var currentDiscount: Discount?=null
    private var userWalletBalance: Double = 0.0
    private var requiredAmount: Double = 0.0

    private var walletListener: ListenerRegistration? = null
    private var paymentMethodsListener: ListenerRegistration? = null
    private var roomTypeListener: ListenerRegistration? = null
    private var hotelListener: ListenerRegistration? = null
    private var discountPMListener: ListenerRegistration? = null
    private var discountListener: ListenerRegistration? = null
    private var selectedMethod:PaymentMethod? = null

    private var newBookingId:String? = null

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
                this.selectedMethod = selectedMethod
                handleTravelWalletDiscount(safeBooking, selectedMethod)

                binding.btnPayment.setOnClickListener{
                    binding.progressBar.visibility = View.VISIBLE
                    addBookingAndPayment(booking)
                }
            }
        }

    }

    private fun updateRoomForBooking(
        booking: Booking,
        onRoomFound: (Booking) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val hotelId = currentHotel?.hotelId
        val roomTypeId = currentRoomType?.roomTypeId

        if (hotelId == null || roomTypeId == null) {
            onFailure(IllegalStateException("Hotel ID or Room Type ID is missing."))
            return
        }

        db.collection("hotels")
            .document(hotelId)
            .collection("rooms")
            .whereEqualTo("room_type_id", roomTypeId)
            .whereEqualTo("status_id", "room_available")
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val availableRoom = querySnapshot.firstOrNull()

                if (availableRoom != null) {
                    booking.roomId = availableRoom.id
                    onRoomFound(booking)
                } else {
                    onFailure(NoSuchElementException("No available room found for this type."))
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        walletListener?.remove()
        paymentMethodsListener?.remove()
        roomTypeListener?.remove()
        hotelListener?.remove()
        discountPMListener?.remove()
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

        updateTotalAmount()
    }

    private fun addBookingAndPayment(booking: Booking?) {
        booking?.let { bookingData ->
            val safePaymentMethod = selectedMethod
            if (safePaymentMethod == null) {
                Log.e("Firebase", "Lỗi: Phương thức thanh toán chưa được chọn.")
                return@let
            }

            if (newBookingId != null) {
                handleNextPaymentStep(newBookingId!!, safePaymentMethod)
                return@let
            }

            binding.progressBar.visibility = View.VISIBLE

            updateRoomForBooking(bookingData,
                onRoomFound = { updatedBooking ->

                    updatedBooking.status = "pending"
                    updatedBooking.discountId= currentDiscount?.id

                    updatedBooking.discountPaymentMethodId= currentDiscountPM?.discountId
                    val hotelDiscountId = updatedBooking.discountId
                    if (hotelDiscountId != null && currentHotel != null) {
                        db.collection("hotels")
                            .document(currentHotel!!.hotelId)
                            .collection("discounts")
                            .document(hotelDiscountId)
                            .update("availableCount", FieldValue.increment(-1))
                            .addOnSuccessListener {
                                Log.d("Firestore", "Discount count decremented successfully.")
                            }
                            .addOnFailureListener { e ->
                                // It is still crucial to log this failure
                                Log.e("Firestore", "ERROR: Failed to decrement discount count.", e)
                            }
                    }
                    db.collection("bookings")
                        .add(updatedBooking)
                        .addOnSuccessListener { documentReference ->
                            newBookingId = documentReference.id
                            Log.d("Firebase", "Thêm booking thành công. ID: $newBookingId")
                            updateRoomStatus(updatedBooking.roomId)
                            addInvoice(updatedBooking, documentReference.id, false)

                            handleNextPaymentStep(documentReference.id, safePaymentMethod)

                        }
                        .addOnFailureListener { e ->
                            binding.progressBar.visibility = View.GONE
                            Log.e("Firebase", "Lỗi khi thêm booking", e)
                        }
                },
                onFailure = { exception ->
                    binding.progressBar.visibility = View.GONE
                    Log.e("Firebase", "Lỗi: Không tìm thấy phòng có sẵn. ${exception.message}")
                }
            )
        }
    }

    private fun updateRoomStatus(roomId: String?) {
        currentHotel?.let { hotel ->
            roomId?.let { id ->
                db.collection("hotels")
                    .document(hotel.hotelId)
                    .collection("rooms")
                    .document(id)
                    .update("status_id", "room_occupied")
                    .addOnSuccessListener {
                        Log.d("Firestore", "Cập nhật trạng thái phòng $id thành công: room_occupied")
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "LỖI: Không thể cập nhật trạng thái phòng $id", e)
                    }
            }
        }
    }

    private fun handleNextPaymentStep(bookingId: String, safePaymentMethod: PaymentMethod) {
        if (safePaymentMethod.paymentMethodName == "Travel wallet") {
            currentHotel?.let { it1 ->
                booking?.let {
                    binding.progressBar.visibility = View.GONE
                    openDialogPaymentConfirm(it.customerId, it1.ownerId, bookingId)
                }
            }
        } else {
            binding.progressBar.visibility = View.GONE
            val intent = Intent(this, PaymentActivity::class.java)
            intent.putExtra("BOOKING_ID", bookingId)
            startActivity(intent)
        }
    }

    private fun addInvoice(booking: Booking, bookingId: String, isConfirmedPayment: Boolean) {
        val totalAmountValue = getAmountPayment()
        val safePaymentMethod = selectedMethod!!

        val paymentStatus = if (isConfirmedPayment) "paid" else "payment_pending"

        val invoice = Invoice(
            bookingId = bookingId,
            totalAmount = totalAmountValue,
            paymentMethodId = safePaymentMethod.paymentMethodId!!,
            paymentStatus = paymentStatus
        )

        db.collection("invoices")
            .add(invoice)
            .addOnSuccessListener { invoiceDocumentReference ->
                Log.d("Firebase", "Thêm invoice thành công. ID: ${invoiceDocumentReference.id}")
                binding.progressBar.visibility = View.GONE

                if (isConfirmedPayment) {
                    openDialogPaymentSuccess(invoice.totalAmount, bookingId)
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e("Firebase", "Lỗi khi thêm invoice", e)
            }
    }

    private fun getAmountPayment(): Double {
        val safeBooking = booking
        if (safeBooking == null || safeBooking.totalFinal == null) {
            Log.e("GuestDetail", "Lỗi: Đối tượng Booking hoặc totalFinal không hợp lệ.")
            return 0.0
        }

        val finalPrice = safeBooking.totalFinal!!
        val checkedId = binding.groupFundAmount.checkedRadioButtonId

        return when (checkedId) {
            binding.radFund10.id -> {
                (finalPrice * 10.0) / 100.0
            }
            binding.radFund100.id -> {
                finalPrice
            }
            else -> {
                Log.e("GuestDetail", "Lỗi: Không tìm thấy RadioButton thanh toán hợp lệ được chọn.")
                0.0
            }
        }
    }

    private fun openDialogPaymentConfirm(customerId: String, ownerId: String, bookingId: String) {
        val amount=getAmountPayment()
        val viewBinding = DialogPaymentConfirmBinding.inflate(layoutInflater)
        viewBinding.tvAmountPayment.text= Format.formatCurrency(amount)

        val dialog = AlertDialog.Builder(this)
            .setView(viewBinding.root)
            .create()
        binding.progressBar.visibility = View.GONE

        viewBinding.btnYes.setOnClickListener {
            dialog.dismiss()
            binding.progressBar.visibility = View.VISIBLE

            val bookingRef = db.collection("bookings").document(bookingId)

            db.runTransaction { transaction ->
                val userRef=db.collection("users").document(customerId)
                val ownerRef=db.collection("users").document(ownerId)

                val userSnapshot=transaction.get(userRef)
                val ownerSnapshot=transaction.get(ownerRef)

                val currentCustomerBalance=userSnapshot.getDouble("walletBalance")
                val currentOwnerBalance=ownerSnapshot.getDouble("walletBalance")

                if (currentCustomerBalance == null || currentOwnerBalance == null || amount == 0.0) {
                    throw IllegalStateException("Missing balance or amount data for payment.")
                }

                if (currentCustomerBalance < amount) {
                    throw IllegalStateException("Customer has insufficient balance for payment.")
                }

                val newCustomerBalance = currentCustomerBalance.minus(amount)
                val newOwnerBalance = currentOwnerBalance.plus(amount)

                transaction.update(userRef,"walletBalance",newCustomerBalance)
                transaction.update(ownerRef,"walletBalance",newOwnerBalance)

                transaction.update(bookingRef, "status", "confirmed")

                null
            }
                .addOnSuccessListener {
                    db.collection("invoices")
                        .whereEqualTo("bookingId", bookingId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { query ->
                            val invoiceDoc = query.firstOrNull()
                            if (invoiceDoc != null) {
                                db.collection("invoices").document(invoiceDoc.id)
                                    .update("paymentStatus", "paid")
                                    .addOnCompleteListener {
                                        Log.d("Payment", "Transaction success! Booking and Invoice updated.")
                                        openDialogPaymentSuccess(amount, bookingId)
                                    }
                            } else {
                                Log.e("Payment", "Invoice not found for booking ID: $bookingId")
                                openDialogPaymentSuccess(amount, bookingId)
                            }
                        }
                }.addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Log.w("Payment", "Transaction failure.", e)
                }
        }

        viewBinding.btnNo.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun openDialogPaymentSuccess(amount: Double, bookingId: String?){
        val viewBinding = DialogPaymentSuccessBinding.inflate(layoutInflater)
        viewBinding.tvAmountPayment.text=  Format.formatCurrency(amount)
        val dialog = AlertDialog.Builder(this)
            .setView(viewBinding.root)
            .create()

        viewBinding.btnOK.setOnClickListener{
            dialog.dismiss()
            val intent=Intent(this,BookingDetailActivity::class.java).apply {
                flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            intent.putExtra("BOOKING_ID",bookingId)
            startActivity(intent)
        }
        dialog.setOnCancelListener{
            dialog.dismiss()
            val intent=Intent(this,BookingDetailActivity::class.java).apply {
                flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            intent.putExtra("BOOKING_ID",bookingId)
            startActivity(intent)
        }

        dialog.show()
    }

    @SuppressLint("SetTextI18n")
    private fun updateTotalAmount() {
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

                // 1. Kiểm tra điều kiện minOrder
                val minOrder = hotelDiscount.minOrder ?: 0.0
                if (safeBooking.totalOrigin >= minOrder.toDouble()) {

                    var calculatedDiscountAmount = (safeBooking.totalOrigin * discountPercent) / 100.0

                    // 2. Kiểm tra giới hạn maxDiscount
                    val maxDiscountLimit = hotelDiscount.maxDiscount ?: Double.MAX_VALUE
                    if (calculatedDiscountAmount > maxDiscountLimit.toDouble()) {
                        calculatedDiscountAmount = maxDiscountLimit.toDouble()
                    }

                    priceAfterHotelDiscount = safeBooking.totalOrigin - calculatedDiscountAmount
                    totalDiscountAmount += calculatedDiscountAmount

                    discountNameList.add(hotelDiscount.discountName ?: "Giảm giá Khách sạn")
                } else {
                    // Nếu không đạt minOrder, không áp dụng giảm giá
                    priceAfterHotelDiscount = safeBooking.totalOrigin
                    Log.d("Discount", "Hotel Discount (${hotelDiscount.discountName}): Không đạt minOrder")
                }
            }
        }

        finalPrice = priceAfterHotelDiscount

        currentDiscountPM?.let { pmDiscount ->
            pmDiscount.discountPercent?.let { discountPercent ->
                // Giảm giá cho Phương thức thanh toán
                val amount = (priceAfterHotelDiscount * discountPercent) / 100.0
                finalPrice -= amount
                totalDiscountAmount += amount

                discountNameList.add(pmDiscount.discountName ?: "Giảm giá Thanh toán")
            }
        }

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

        binding.radFund10.text = Format.formatCurrency((finalPrice * 10.0) / 100.0) + " (10%)"
        binding.radFund100.text = Format.formatCurrency(finalPrice) + " (Full)"

        loadPaymentMethod(safeBooking)
    }

    private fun handleTravelWalletDiscount(booking: Booking, selectedMethod: PaymentMethod) {
        var targetDiscountId = booking.discountPaymentMethodId

        if (selectedMethod.paymentMethodName == "Travel wallet") {
            db.collection("bookings")
                .whereEqualTo("customerId", booking.customerId)
                .limit(1)
                .get()
                .addOnSuccessListener { bookingsResult ->
                    if (bookingsResult.isEmpty) {
                        targetDiscountId = "PM-001"
                        loadFinalDiscountAndRefreshUI(targetDiscountId)
                    } else {
                        db.collection("invoices")
                            .whereEqualTo("paymentMethodId", "Travel wallet")
                            .limit(1)
                            .get()
                            .addOnSuccessListener { invoicesResult ->
                                if (invoicesResult.isEmpty) {
                                    targetDiscountId = "PM-001"
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

    private fun loadFinalDiscountAndRefreshUI(discountPaymentMethodId: String?) {
        if (discountPaymentMethodId != null) {
            loadDiscountPM(discountPaymentMethodId) {}
        } else {
            discountPMListener?.remove()
            currentDiscountPM = null
            updateTotalAmount()
        }
    }

    private fun loadDiscountPMAndRefresh(booking: Booking) {
        if (booking.discountPaymentMethodId != null) {
            loadDiscountPM(booking.discountPaymentMethodId!!) {}
        } else {
            currentDiscountPM = null
            updateUI(booking)
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
                    handleTravelWalletDiscount(safeBooking, selectedMethod)
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

                            db.collection("hotels")
                                .document(hotelId)
                                .collection("discounts")
                                .whereGreaterThan("availableCount", 0)
                                .limit(1)
                                .get()
                                .addOnSuccessListener { discountSnapshot ->
                                    val hotelDiscountId: String? = discountSnapshot.firstOrNull()?.id

                                    if (hotelDiscountId != null) {
                                        loadDiscountHotel(hotelDiscountId) {
                                            loadDiscountPMAndRefresh(booking)
                                        }
                                    } else {
                                        currentDiscount = null
                                        loadDiscountPMAndRefresh(booking)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("Firebase", "Lỗi truy vấn Discount cho Hotel: ${hotel.hotelId}", e)
                                    currentDiscount = null
                                    loadDiscountPMAndRefresh(booking)
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
                        booking?.let { updateUI(it) }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho DiscountPaymentMethod: ${dataSnapshot.id}", ex)
                        onSuccess(null)
                    }
                } else {
                    Log.w("Firebase", "DiscountPaymentMethod ID: $discountPaymentMethodId không tồn tại.")
                    currentDiscountPM = null
                    onSuccess(null)
                    booking?.let { updateUI(it) }
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
                        // Gọi updateUI sau khi load thành công
                        booking?.let { updateUI(it) }
                    } catch (ex: Exception) {
                        Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho Discount: ${dataSnapshot.id}", ex)
                        onSuccess(null)
                    }
                } else {
                    Log.w("Firebase", "Discount ID: $discountHotelId không tồn tại trong Subcollection.")
                    currentDiscount = null
                    onSuccess(null)
                    booking?.let { updateUI(it) }
                }
            }
    }
    fun convertTimestampToString(timestamp: Timestamp): String {
        val date: Date = timestamp.toDate()

        val dateFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())

        return dateFormatter.format(date)
    }

}