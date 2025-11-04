package com.tdc.nhom6.roomio.activities

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.RoomsAdapter
import com.tdc.nhom6.roomio.apis.CloudinaryRepository
import com.tdc.nhom6.roomio.databinding.RoomFloorsHotelLayoutBinding
import com.tdc.nhom6.roomio.models.Room
import com.tdc.nhom6.roomio.models.RoomType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class RoomHotelActivity : AppCompatActivity() {

    private lateinit var binding: RoomFloorsHotelLayoutBinding
    private lateinit var roomsAdapter: RoomsAdapter
    private lateinit var cloudinaryRepository: CloudinaryRepository
    private val db = FirebaseFirestore.getInstance()

    private var hotelId: String? = null
    private var roomsListener: ListenerRegistration? = null
    private var roomTypesListener: ListenerRegistration? = null
    private var allRooms: List<Room> = emptyList()

    private var selectedFloor: Int? = null
    private var selectedRoomTypeFilter: String? = null

    private var roomTypes: MutableList<RoomType> = mutableListOf()
    private var selectedRoomType: RoomType? = null
    private val selectedRooms = mutableSetOf<Room>()

    private var selectedRoomTypeUris: MutableList<Uri> = mutableListOf()

    // Chọn ảnh loại phòng
    private val roomTypeImagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) {
                selectedRoomTypeUris.clear()
                selectedRoomTypeUris.addAll(uris)
                Toast.makeText(this, "Đã chọn ${uris.size} ảnh cho loại phòng", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RoomFloorsHotelLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cloudinaryRepository = CloudinaryRepository(this)
        hotelId = intent.getStringExtra("hotelId")

        if (hotelId.isNullOrEmpty()) {
            Toast.makeText(this, "Không tìm thấy hotelId", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        setupListeners()
        listenRoomsRealtime()
        listenRoomTypesRealtime()
    }

    override fun onDestroy() {
        super.onDestroy()
        roomsListener?.remove()
        roomTypesListener?.remove()
    }

    // ==============================================
    // =========== SETUP GIAO DIỆN =================
    // ==============================================

    private fun setupRecyclerView() {
        roomsAdapter = RoomsAdapter { room -> showStatusUpdateDialog(room) }
        binding.rvRooms.apply {
            layoutManager = GridLayoutManager(this@RoomHotelActivity, 4)
            adapter = roomsAdapter
        }
    }

    private fun setupListeners() = with(binding) {
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        btnChooseRoomType.setOnClickListener { showRoomTypeSelector() }
        btnAddRoomType.setOnClickListener { showCreateRoomTypeDialog() }

        rgFloors.setOnCheckedChangeListener { _, checkedId ->
            val checkedRadioButton = rgFloors.findViewById<RadioButton>(checkedId)
            selectedFloor = checkedRadioButton?.tag as? Int
            filterRoomsCombined()
        }
    }

    // ==============================================
    // =========== REALTIME DỮ LIỆU =================
    // ==============================================

    private fun listenRoomsRealtime() {
        val currentHotelId = hotelId!!
        roomsListener?.remove()

        roomsListener = db.collection("hotels")
            .document(currentHotelId)
            .collection("rooms")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("RoomHotelActivity", "Lỗi realtime rooms", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val roomsList = snapshot.documents.mapNotNull { it.toObject(Room::class.java) }
                        .sortedWith(compareBy<Room> { it.floor }.thenBy { it.room_number })

                    allRooms = roomsList
                    roomsAdapter.submitList(allRooms)
                    createFloorRadioButtons(roomsList)
                    filterRoomsCombined()
                }
            }
    }

    private fun listenRoomTypesRealtime() {
        roomTypesListener?.remove()
        roomTypesListener = db.collection("roomTypes")
            .whereEqualTo("hotelId", hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("RoomHotelActivity", "Lỗi realtime roomTypes", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    roomTypes = snapshot.documents.mapNotNull { it.toObject(RoomType::class.java) }.toMutableList()
                    createRoomTypeFilterButtons(roomTypes)
                }
            }
    }

    // ==============================================
    // =========== TẠO NÚT CHỌN TẦNG & LỌC ==========
    // ==============================================

    private fun createFloorRadioButtons(rooms: List<Room>) = with(binding) {
        val floors = rooms.map { it.floor }.distinct().sorted()
        rgFloors.removeAllViews()

        val allButton = createFilterButton("Tất cả tầng", 0)
        rgFloors.addView(allButton)

        floors.forEach { floor ->
            val floorButton = createFilterButton("Tầng $floor", floor)
            rgFloors.addView(floorButton)
        }

        rgFloors.check(allButton.id)
    }

    private fun createFilterButton(text: String, tagValue: Int): RadioButton {
        val button = RadioButton(this)
        button.id = ViewGroup.generateViewId()
        button.text = text
        button.tag = tagValue
        button.layoutParams = RadioGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.margin_small) }

        button.buttonDrawable = null
        button.gravity = Gravity.CENTER
        button.setPadding(30, 15, 30, 15)
        button.setBackgroundResource(R.drawable.bg_radio_floor_selector)
        button.setTextColor(ContextCompat.getColorStateList(this, R.color.text_radio_floor_selector))
        return button
    }

    // ==============================================
    // =========== TẠO NÚT LỌC LOẠI PHÒNG ===========
    // ==============================================

    private fun createRoomTypeFilterButtons(roomTypes: List<RoomType>) = with(binding) {
        rgRoomTypes.removeAllViews()

        val allButton = createRoomTypeButton("Tất cả loại phòng", null)
        rgRoomTypes.addView(allButton)

        roomTypes.forEach { type ->
            val btn = createRoomTypeButton(type.typeName, type.roomTypeId)
            rgRoomTypes.addView(btn)
        }

        rgRoomTypes.check(allButton.id)

        rgRoomTypes.setOnCheckedChangeListener { _, checkedId ->
            val checked = rgRoomTypes.findViewById<RadioButton>(checkedId)
            selectedRoomTypeFilter = checked?.tag as? String
            filterRoomsCombined()
        }
    }

    private fun createRoomTypeButton(text: String, typeId: String?): RadioButton {
        val button = RadioButton(this)
        button.id = ViewGroup.generateViewId()
        button.text = text
        button.tag = typeId
        button.layoutParams = RadioGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.margin_small) }

        button.buttonDrawable = null
        button.gravity = Gravity.CENTER
        button.setPadding(30, 15, 30, 15)
        button.setBackgroundResource(R.drawable.bg_radio_floor_selector)
        button.setTextColor(ContextCompat.getColorStateList(this, R.color.text_radio_floor_selector))
        return button
    }

    // ==============================================
    // =========== LỌC PHÒNG (TẦNG + LOẠI) ==========
    // ==============================================

    private fun filterRoomsCombined() {
        var filtered = allRooms

        selectedFloor?.let { floor ->
            if (floor != 0) filtered = filtered.filter { it.floor == floor }
        }

        selectedRoomTypeFilter?.let { typeId ->
            filtered = filtered.filter { it.room_type_id == typeId }
        }

        roomsAdapter.submitList(filtered)
    }

    // ==============================================
    // =========== CẬP NHẬT TRẠNG THÁI ==============
    // ==============================================

    private fun showStatusUpdateDialog(room: Room) {
        val roomStatuses = listOf("room_available", "room_occupied", "room_pending", "room_fixed")
        val displayNames = listOf("Available", "Occupied", "Pending", "Fixed")
        val currentIndex = roomStatuses.indexOf(room.status_id).takeIf { it != -1 } ?: 0

        AlertDialog.Builder(this)
            .setTitle("Cập nhật trạng thái cho ${room.displayCode}")
            .setSingleChoiceItems(displayNames.toTypedArray(), currentIndex) { dialog, which ->
                val newStatus = roomStatuses[which]
                updateRoomStatus(room.room_id, newStatus)
                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun updateRoomStatus(roomId: String, newStatus: String) {
        if (hotelId.isNullOrEmpty()) return

        db.collection("hotels")
            .document(hotelId!!)
            .collection("rooms")
            .document(roomId)
            .update("status_id", newStatus)
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ==============================================
    // =========== THÊM LOẠI PHÒNG MỚI ==============
    // ==============================================

    private fun showCreateRoomTypeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_room_type_layout, null)
        val edtName = dialogView.findViewById<EditText>(R.id.edtRoomTypeName)
        val edtArea = dialogView.findViewById<EditText>(R.id.edtRoomTypeArea)
        val edtPeople = dialogView.findViewById<EditText>(R.id.edtRoomTypePeople)
        val edtPrice = dialogView.findViewById<EditText>(R.id.edtRoomTypePrice)
        val btnSelectImages = dialogView.findViewById<Button>(R.id.btnSelectRoomImages)
        val tvSelected = dialogView.findViewById<TextView>(R.id.tvSelectedImages)

        btnSelectImages.setOnClickListener {
            roomTypeImagePickerLauncher.launch("image/*")
            tvSelected.text = "Đã chọn ${selectedRoomTypeUris.size} ảnh"
        }

        AlertDialog.Builder(this)
            .setTitle("Thêm loại phòng mới")
            .setView(dialogView)
            .setPositiveButton("Lưu") { dialog, _ ->
                val name = edtName.text.toString().trim()
                val area = edtArea.text.toString().toIntOrNull() ?: 0
                val people = edtPeople.text.toString().toIntOrNull() ?: 1
                val price = edtPrice.text.toString().toLongOrNull() ?: 0

                if (name.isEmpty()) {
                    Toast.makeText(this, "Tên loại phòng không được để trống", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch(Dispatchers.Main) {
                    val uploadedImages = uploadRoomTypeImages()
                    saveRoomTypeToFirestore(name, area, people, price, uploadedImages)
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private suspend fun uploadRoomTypeImages(): List<Map<String, Any>> {
        if (selectedRoomTypeUris.isEmpty()) return emptyList()

        val files = withContext(Dispatchers.IO) {
            selectedRoomTypeUris.mapNotNull { uriToFile(it) }
        }

        val uploaded = withContext(Dispatchers.IO) {
            cloudinaryRepository.uploadMultipleImages(files, "room_type_images")
        }

        files.forEach { it.delete() }

        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date())

        return uploaded.map {
            mapOf(
                "imageUrl" to (it.secure_url ?: ""),
                "thumbnail" to true,
                "uploadedAt" to now
            )
        }
    }

    private fun saveRoomTypeToFirestore(
        name: String,
        area: Int,
        people: Int,
        price: Long,
        roomImages: List<Map<String, Any>>
    ) {
        val newId = db.collection("roomTypes").document().id

        val data = linkedMapOf(
            "area" to area,
            "description" to null,
            "hotelId" to hotelId,
            "maxPeople" to people,
            "pricePerNight" to price,
            "roomImages" to roomImages,
            "roomTypeId" to newId,
            "typeName" to name,
            "viewId" to "0"
        )

        db.collection("roomTypes").document(newId).set(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Đã thêm loại phòng thành công!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi khi lưu loại phòng!", Toast.LENGTH_SHORT).show()
            }
    }

    // ==============================================
    // =========== GÁN LOẠI PHÒNG CHO PHÒNG =========
    // ==============================================

    private fun showRoomTypeSelector() {
        if (roomTypes.isEmpty()) {
            Toast.makeText(this, "Chưa có loại phòng nào. Hãy thêm loại mới!", Toast.LENGTH_SHORT).show()
            return
        }

        val names = roomTypes.map { it.typeName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Chọn loại phòng để gán")
            .setItems(names) { _, which ->
                selectedRoomType = roomTypes[which]
                showRoomSelectionDialog()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showRoomSelectionDialog() {
        val roomNames = allRooms.map { it.displayCode }.toTypedArray()
        val checked = BooleanArray(allRooms.size)

        AlertDialog.Builder(this)
            .setTitle("Chọn phòng để gán '${selectedRoomType?.typeName}'")
            .setMultiChoiceItems(roomNames, checked) { _, which, isChecked ->
                val room = allRooms[which]
                if (isChecked) selectedRooms.add(room) else selectedRooms.remove(room)
            }
            .setPositiveButton("Gán") { _, _ ->
                assignRoomTypeToSelectedRooms()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun assignRoomTypeToSelectedRooms() {
        val type = selectedRoomType ?: return
        val hotel = hotelId ?: return

        val updates = mapOf(
            "room_type_id" to type.roomTypeId,
            "room_type_name" to type.typeName
        )

        selectedRooms.forEach { room ->
            db.collection("hotels")
                .document(hotel)
                .collection("rooms")
                .document(room.room_id)
                .update(updates)
        }

        Toast.makeText(
            this,
            "Đã gán loại '${type.typeName}' cho ${selectedRooms.size} phòng",
            Toast.LENGTH_LONG
        ).show()

        selectedRooms.clear()
    }

    // ==============================================
    // =========== HỖ TRỢ FILE & UPLOAD =============
    // ==============================================

    private fun uriToFile(uri: Uri): File? {
        val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("UriToFile", "Lỗi: ${e.message}")
            null
        }
    }
}
