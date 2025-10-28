package com.tdc.nhom6.roomio.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.adapters.PaymentMethodAdapter
import com.tdc.nhom6.roomio.databinding.ActivityGuestDetailBinding
import com.tdc.nhom6.roomio.models.Booking
import com.tdc.nhom6.roomio.models.Discount
import com.tdc.nhom6.roomio.models.FacilityAdapter
import com.tdc.nhom6.roomio.models.PaymentMethod
import com.tdc.nhom6.roomio.models.RoomType
import java.math.BigDecimal
import java.math.RoundingMode

class GuestDetailActivity : AppCompatActivity() {
    private lateinit var binding:ActivityGuestDetailBinding
    private lateinit var booking: Booking
    private lateinit var roomType: RoomType
    private lateinit var hotel: Hotel
    private lateinit var discount: Discount

    private lateinit var paymentMethodAdapter: PaymentMethodAdapter
    private var listPaymentMethod: MutableList<PaymentMethod> = mutableListOf()
    val db= FirebaseFirestore.getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityGuestDetailBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        loadRoomType(booking.roomTypeId)
        loadHotel(booking.hotelId)
        loadDiscount(booking.discountId)
        loadPaymentMethod()
        initializeLayout()
    }

    @SuppressLint("SetTextI18n")
    private fun initializeLayout() {
        binding.tvHotelName.text=hotel.hotelName
        binding.tvHotelName.text=hotel.address
        binding.ratingBar.rating=hotel.averageRating
        binding.tvReviews.text=hotel.totalReviews
        binding.tvRoomType.text=roomType.typeName
        binding.tvCheckIn.text=booking.checkInDate.toString()
        binding.tvCheckOut.text=booking.checkInDate.toString()
        binding.tvGuest.text="${booking.numberGuest} people"
        binding.tvTotalOrigin.text=booking.totalOrigin.toString()
        binding.tvTotalAfter.text=booking.totalFinal.toString()
        discount.discountValue?.let { discountPercent ->
            val totalOriginBD = booking.totalOrigin
            val discountPercentBD = discountPercent.toBigDecimal()

            val discountAmount = (totalOriginBD * discountPercentBD).divide(
                BigDecimal(100),
                2,
            )

            binding.tvDiscountPrice.text = discountAmount.toString()
            binding.tvTotalAfter.text=(booking.totalOrigin-discountAmount).toString()
        }
        discount.discountName?.let{discountName ->
            binding.tvDiscountName.text= discountName
        }
        paymentMethodAdapter= PaymentMethodAdapter(listPaymentMethod)
        binding.recyclerPaymentMethod.adapter=paymentMethodAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadPaymentMethod(){
        db.collection("paymentMethods")
            .get()
            .addOnSuccessListener { result->
                listPaymentMethod.clear()
                for (document in result){
                    try {
                        val paymentMethod = document.toObject(PaymentMethod::class.java)
                        listPaymentMethod.add(paymentMethod)
                    }catch (ex: Exception){
                        Log.d("Firebase","Lỗi chuyển đổi dữ liệu cho PaymentMethods: ${document.id}",ex)
                    }
                }
                paymentMethodAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Lỗi khi tải PaymentMethods: ", exception)
            }
    }
    private fun loadRoomType(roomTypeId:Int ){
        db.collection("roomTypes/${roomTypeId}")
            .get()
            .addOnSuccessListener { result->
                for (document in result){
                    try {
                        roomType = document.toObject(RoomType::class.java)
                    }catch (ex: Exception){
                        Log.d("Firebase","Lỗi chuyển đổi dữ liệu cho RoomType: ${document.id}",ex)
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Lỗi khi tải RoomType: ", exception)
            }
    }

    private fun loadHotel(hotelId:Int ){
        db.collection("hotels/${hotelId}")
            .get()
            .addOnSuccessListener { result->
                for (document in result){
                    try {
                        hotel = document.toObject(Hotel::class.java)
                    }catch (ex: Exception){
                        Log.d("Firebase","Lỗi chuyển đổi dữ liệu cho Hotel: ${document.id}",ex)
                    }
                }
                paymentMethodAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Lỗi khi tải Hotel: ", exception)
            }
    }

    private fun loadDiscount(discountId: String ){
        db.collection("discounts/${discountId}")
            .get()
            .addOnSuccessListener { result->
                for (document in result){
                    try {
                        discount = document.toObject(Discount::class.java)
                    }catch (ex: Exception){
                        Log.d("Firebase","Lỗi chuyển đổi dữ liệu cho Discount: ${document.id}",ex)
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Lỗi khi tải Discount: ", exception)
            }
    }

}