package com.tdc.nhom6.roomio.adapters

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ItemBookingBinding
import com.tdc.nhom6.roomio.models.Booking
import com.tdc.nhom6.roomio.models.RoomType
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.activities.booking.BookingDetailActivity
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter.Format
import com.tdc.nhom6.roomio.models.HotelModel
import java.text.SimpleDateFormat
import java.util.Locale

class MyBookingAdapter : RecyclerView.Adapter<MyBookingAdapter.BookingViewHolder>() {

    private var bookings: List<Booking> = emptyList()

    private val db = FirebaseFirestore.getInstance()
    private val dateFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())

    fun submitList(newBookings: List<Booking>) {
        bookings = newBookings
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = bookings.size

    fun getItem(position: Int): Booking = bookings[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val binding = ItemBookingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BookingViewHolder(private val binding: ItemBookingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var roomTypeListener: ListenerRegistration? = null
        private var hotelListener: ListenerRegistration? = null

        fun bind(booking: Booking) {
            roomTypeListener?.remove()
            hotelListener?.remove()

            binding.apply {
                textPrice.text = "${Format.formatCurrency(booking.totalFinal)}"
                setStatusTag(booking.status)

                val checkInStr = booking.checkInDate?.let { dateFormatter.format(it.toDate()) } ?: "N/A"
                val checkOutStr = booking.checkOutDate?.let { dateFormatter.format(it.toDate()) } ?: "N/A"
                textDates.text = "$checkInStr - $checkOutStr"

                textHotelName.text = "Đang tải..."
                textLocation.text = "Đang tải..."
                itemView.setOnClickListener{
                    val intent=Intent(itemView.context, BookingDetailActivity::class.java)
                    intent.putExtra("BOOKING_ID",booking.bookingId)
                    itemView.context.startActivity(intent)
                }
                loadRoomType(booking.roomTypeId)
            }
        }

        private fun loadRoomType(roomTypeId: String) {
            roomTypeListener = db.collection("roomTypes")
                .document(roomTypeId)
                .addSnapshotListener { dataSnapshot, exception ->
                    if (exception != null) {
                        Log.e("Firestore", "Lỗi khi lắng nghe RoomType: ", exception)
                        binding.textHotelName.text = "Lỗi tải tên phòng"
                        binding.textLocation.text = "Lỗi tải địa chỉ"
                        return@addSnapshotListener
                    }

                    if (dataSnapshot != null && dataSnapshot.exists()) {
                        val roomType = dataSnapshot.toObject(RoomType::class.java)
                        if (roomType != null) {
                            loadHotel(roomType.hotelId)
                        }
                    } else {
                        binding.textHotelName.text = "Phòng không tồn tại"
                        binding.textLocation.text = "N/A"
                    }
                }
        }

        private fun loadHotel(hotelId: String) {
            hotelListener = db.collection("hotels")
                .document(hotelId)
                .addSnapshotListener { dataSnapshot, exception ->
                    if (exception != null) {
                        Log.e("Firestore", "Lỗi khi lắng nghe Hotel: ", exception)
                        return@addSnapshotListener
                    }

                    if (dataSnapshot != null && dataSnapshot.exists()) {
                        val hotel = dataSnapshot.toObject(HotelModel::class.java)
                        if (hotel != null) {
                            binding.textHotelName.text = hotel.hotelName
                            binding.textLocation.text = hotel.hotelAddress
                        }
                    } else {
                        Log.w("Firebase", "Hotel ID: $hotelId không tồn tại.")
                    }
                }
        }

        private fun setStatusTag(status: String) {
            val displayStatus: String
            val drawableId: Int

            when (status) {
                "pending" -> {
                    displayStatus = "Pending"
                    drawableId = R.drawable.shape_rounded_pending
                }
                "confirmed" -> {
                    displayStatus = "Confirmed"
                    drawableId = R.drawable.shape_rounded_confirmed
                }
                "cancelled" -> {
                    displayStatus = "Cancelled"
                    drawableId = R.drawable.shape_rounded_canceled
                }
                "completed" -> {
                    displayStatus = "Completed"
                    drawableId = R.drawable.shape_rounded_completed
                }
                "expired" -> {
                    displayStatus = "Expired"
                    drawableId = R.drawable.shape_rounded_completed
                }
                else -> {
                    displayStatus = "Active"
                    drawableId = R.drawable.shape_rounded_active
                }
            }

            binding.textStatusTag.text = displayStatus
            val drawable = ContextCompat.getDrawable(binding.root.context, drawableId)
            binding.textStatusTag.background = drawable
        }
    }
}