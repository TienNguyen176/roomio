package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.RoomsAdapter
import com.tdc.nhom6.roomio.databinding.RoomFloorsHotelLayoutBinding
import com.tdc.nhom6.roomio.models.Room

class RoomHotelActivity : AppCompatActivity() {

    private lateinit var binding: RoomFloorsHotelLayoutBinding
    private lateinit var roomsAdapter: RoomsAdapter
    private val db = FirebaseFirestore.getInstance()
    private var hotelId: String? = null
    private var roomsListener: ListenerRegistration? = null

    private var allRooms: List<Room> = emptyList()
    private var selectedFloor: Long? = null

    private val roomStatuses = listOf("room_available", "room_occupied", "room_pending", "room_fixed")
    private val roomStatusDisplayNames = listOf("Available", "Occupied", "Pending", "Fixed")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RoomFloorsHotelLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hotelId = intent.getStringExtra("hotelId")
        if (hotelId.isNullOrEmpty()) {
            Toast.makeText(this, "Lỗi: Không tìm thấy ID khách sạn để tải phòng.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupRecyclerView()
        setupListeners()
        loadRoomData()
    }

    private fun setupRecyclerView() {
        roomsAdapter = RoomsAdapter { room ->
            showStatusUpdateDialog(room)
        }
        binding.rvRooms.apply {
            layoutManager = GridLayoutManager(this@RoomHotelActivity, 4)
            adapter = roomsAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.rgFloors.setOnCheckedChangeListener { _, checkedId ->
            val checkedRadioButton = findViewById<RadioButton>(checkedId)
            if (checkedRadioButton != null) {
                selectedFloor = checkedRadioButton.tag as? Long
                filterRoomsByFloor()
            }
        }

        binding.btnConfirm.setOnClickListener {
            Toast.makeText(this, "Chức năng xác nhận chưa được implement.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun filterRoomsByFloor() {
        val filteredList = if (selectedFloor == null || selectedFloor == 0L) {
            allRooms
        } else {
            allRooms.filter { it.floor == selectedFloor }
        }
        roomsAdapter.submitList(filteredList)
    }

    /**
     * Mở dialog cho phép người dùng chọn trạng thái mới cho phòng.
     */
    private fun showStatusUpdateDialog(room: Room) {
        val selectedIndex = roomStatuses.indexOf(room.status_id).takeIf { it != -1 } ?: 0

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cập nhật trạng thái phòng ${room.displayCode}")
            .setSingleChoiceItems(roomStatusDisplayNames.toTypedArray(), selectedIndex) { dialog, which ->
                val newStatusId = roomStatuses[which]

                updateRoomStatus(room.room_id, newStatusId)

                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /**
     * Cập nhật trạng thái phòng trong Firebase và tải lại dữ liệu.
     */
    private fun updateRoomStatus(roomId: String, newStatusId: String) {
        val currentHotelId = hotelId!!

        val roomRef = db.collection("hotels")
            .document(currentHotelId)
            .collection("rooms")
            .document(roomId)

        roomRef.update("status_id", newStatusId)
            .addOnSuccessListener {
                Toast.makeText(this, "Đã cập nhật $roomId thành $newStatusId", Toast.LENGTH_SHORT).show()
                loadRoomData()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi cập nhật: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("RoomUpdate", "Lỗi cập nhật phòng $roomId", e)
            }
    }


    private fun loadRoomData() {
        val currentHotelId = hotelId!!

        // Hủy listener cũ (nếu có) để tránh trùng lặp
        roomsListener?.remove()

        val roomsCollectionRef = db.collection("hotels")
            .document(currentHotelId)
            .collection("rooms")

        roomsListener = roomsCollectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "Lỗi khi lắng nghe dữ liệu: ${error.message}", Toast.LENGTH_LONG).show()
                Log.e("RoomHotelActivity", "Firestore listener error", error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val roomsList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Room::class.java)
                }.sortedWith(compareBy<Room> { it.floor }.thenBy { it.room_number })

                allRooms = roomsList
                roomsAdapter.submitList(allRooms)
                createFloorRadioButtons(roomsList)
                filterRoomsByFloor()

                Log.d("RoomHotelActivity", "Realtime update: ${roomsList.size} phòng được cập nhật.")
            }
        }
    }


    private fun createFloorRadioButtons(rooms: List<Room>) {
        val floors = rooms.map { it.floor }.distinct().sorted()
        binding.rgFloors.removeAllViews()

        val allButton = createFloorButton("Tất cả", 0L)
        binding.rgFloors.addView(allButton)

        floors.forEach { floor ->
            val floorButton = createFloorButton("Tầng $floor", floor)
            binding.rgFloors.addView(floorButton)
        }

        binding.rgFloors.check(allButton.id)
    }

    private fun createFloorButton(text: String, floorTag: Long): RadioButton {
        val button = RadioButton(this).apply {
            id = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                android.view.View.generateViewId()
            } else {
                1000 + floorTag.toInt()
            }

            this.text = text
            tag = floorTag

            layoutParams = RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.margin_small)
            }

            setBackgroundResource(R.drawable.bg_radio_floor_selector)
            buttonDrawable = null
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            setTextColor(ContextCompat.getColorStateList(context, R.color.text_radio_floor_selector))
        }
        return button
    }
}