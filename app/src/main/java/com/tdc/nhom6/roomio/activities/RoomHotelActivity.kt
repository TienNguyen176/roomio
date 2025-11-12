package com.tdc.nhom6.roomio.activities

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.RoomsAdapter
import com.tdc.nhom6.roomio.apis.CloudinaryRepository
import com.tdc.nhom6.roomio.databinding.DialogCreateRoomTypeLayoutBinding
import com.tdc.nhom6.roomio.databinding.RoomFloorsHotelLayoutBinding
import com.tdc.nhom6.roomio.dialogs.FacilityPriceSelectorDialog
import com.tdc.nhom6.roomio.models.DamageLossPrice
import com.tdc.nhom6.roomio.models.FacilityPrice
import com.tdc.nhom6.roomio.models.Room
import com.tdc.nhom6.roomio.models.RoomType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.text.InputFilter
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import java.util.Locale

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
    private val selectedFacilityPrices = mutableListOf<FacilityPrice>()
    private val selectedDamagePrices  = mutableListOf<DamageLossPrice>()

    // Picker ảnh loại phòng
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

        // Load prefix đã lưu cho hotel
        val prefs = getSharedPreferences("roomio_prefs", MODE_PRIVATE)
        val savedPrefix = prefs.getString("room_prefix_${hotelId}", "F") ?: "F"
        Room.prefix = savedPrefix

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

    // ===================== UI =====================

    private fun setupRecyclerView() {
        roomsAdapter = RoomsAdapter { room -> showStatusUpdateDialog(room) }
        binding.rvRooms.apply {
            layoutManager = GridLayoutManager(this@RoomHotelActivity, 4)
            adapter = roomsAdapter
        }
    }

    private fun setupListeners() = with(binding) {
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this@RoomHotelActivity, view)
            popup.menuInflater.inflate(R.menu.menu_room_hotel_layout, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.btnChooseRoomType -> {
                        showRoomTypeSelector()
                        true
                    }
                    R.id.btnAddRoomType -> {
                        showCreateRoomTypeDialog()
                        true
                    }
                    R.id.btnAddFloor -> {
                        showAddFloorDialog()
                        true
                    }
                    R.id.btnDeleteRooms -> {
                        showDeleteRoomsDialog()
                        true
                    }
                    R.id.btnUpdateRoomName -> {
                        showUpdateRoomNameDialog()
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }

        rgFloors.setOnCheckedChangeListener { _, checkedId ->
            val checkedRadioButton = rgFloors.findViewById<RadioButton>(checkedId)
            selectedFloor = checkedRadioButton?.tag as? Int
            filterRoomsCombined()
        }
    }

    // ================== REALTIME ==================

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

    // ========== Radio tầng & loại phòng ==========

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
        button.gravity = android.view.Gravity.CENTER
        button.setPadding(30, 15, 30, 15)
        button.setBackgroundResource(R.drawable.bg_radio_floor_selector)
        button.setTextColor(ContextCompat.getColorStateList(this, R.color.text_radio_floor_selector))
        return button
    }

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
        button.gravity = android.view.Gravity.CENTER
        button.setPadding(30, 15, 30, 15)
        button.setBackgroundResource(R.drawable.bg_radio_floor_selector)
        button.setTextColor(ContextCompat.getColorStateList(this, R.color.text_radio_floor_selector))
        return button
    }

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

    // ========== Cập nhật trạng thái phòng ==========

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

    // ========== Thêm loại phòng mới ==========

    private fun showCreateRoomTypeDialog() {
        val dialogBinding = DialogCreateRoomTypeLayoutBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Thêm loại phòng mới")
            .setView(dialogBinding.root)
            .setPositiveButton("Lưu", null)
            .setNegativeButton("Hủy", null)
            .create()

        dialog.setOnShowListener {
            dialogBinding.btnSelectRoomImages.setOnClickListener {
                roomTypeImagePickerLauncher.launch("image/*")
                dialogBinding.tvSelectedImages.text =
                    "Đã chọn ${selectedRoomTypeUris.size} ảnh"
            }

            dialogBinding.btnSelectFacilities.setOnClickListener {
                FacilityPriceSelectorDialog(
                    this@RoomHotelActivity,
                    lifecycleScope,
                    selectedFacilityPrices
                ) { selectedRates ->
                    selectedFacilityPrices.clear()
                    selectedFacilityPrices.addAll(selectedRates.facilityRates)

                    selectedDamagePrices.clear()
                    selectedDamagePrices.addAll(selectedRates.damageLossRates)

                    dialogBinding.tvSelectedFacilities.text =
                        "Đã chọn ${selectedRates.facilityRates.size} tiện ích"
                }.show()
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.edtRoomTypeName.text.toString().trim()
                val area = dialogBinding.edtRoomTypeArea.text.toString().toIntOrNull() ?: 0
                val people = dialogBinding.edtRoomTypePeople.text.toString().toIntOrNull() ?: 1
                val price = dialogBinding.edtRoomTypePrice.text.toString().toLongOrNull() ?: 0
                val desc = dialogBinding.edtRoomTypeDescription.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, "Tên loại phòng không được để trống", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch(Dispatchers.Main) {
                    val uploadedImages = uploadRoomTypeImages()
                    saveRoomTypeToFirestore(name, area, people, price, desc, uploadedImages)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
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
        val now = com.google.firebase.Timestamp.now()

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
        description: String,
        roomImages: List<Map<String, Any>>
    ) {
        val newId = db.collection("roomTypes").document().id
        val roomTypeRef = db.collection("roomTypes").document(newId)

        val data = mapOf(
            "roomTypeId" to newId,
            "typeName" to name,
            "area" to area,
            "description" to description,
            "hotelId" to hotelId,
            "maxPeople" to people,
            "pricePerNight" to price,
            "roomImages" to roomImages,
            "viewId" to "0"
        )

        roomTypeRef.set(data)
            .addOnSuccessListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    saveFacilityRates(roomTypeRef)
                    saveDamageLossRates(roomTypeRef)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@RoomHotelActivity,
                            "Đã thêm loại phòng thành công!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi khi lưu loại phòng!", Toast.LENGTH_SHORT).show()
            }
    }

    private suspend fun saveFacilityRates(roomTypeRef: DocumentReference) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            .format(Date())

        selectedFacilityPrices.forEach { fp ->
            val doc = mapOf(
                "facilityId" to fp.facilityId,
                "price" to fp.price,
                "updateDate" to now
            )
            roomTypeRef.collection("facilityRates").add(doc).await()
        }
    }

    private suspend fun saveDamageLossRates(roomTypeRef: DocumentReference) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            .format(Date())

        selectedDamagePrices.forEach { dp ->
            val doc = mapOf(
                "facilityId" to dp.facilityId,
                "price" to dp.price,
                "statusId" to (dp.statusId.ifEmpty { "0" }),
                "updateDate" to now
            )
            roomTypeRef.collection("damageLossRates").add(doc).await()
        }
    }

    // ========== Gán loại phòng ==========

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

    // ========== Thêm tầng + phòng ==========

    private fun showAddFloorDialog() {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_floor_layout, null, false)

        val edtFloorNumber = view.findViewById<EditText>(R.id.edtFloorNumber)
        val edtRoomCount  = view.findViewById<EditText>(R.id.edtRoomCount)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Thêm tầng mới")
            .setView(view)
            .setPositiveButton("Tạo", null)
            .setNegativeButton("Hủy", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val floor = edtFloorNumber.text.toString().trim().toIntOrNull()
                val roomCount = edtRoomCount.text.toString().trim().toIntOrNull()

                if (floor == null) {
                    edtFloorNumber.error = "Vui lòng nhập số tầng hợp lệ"
                    return@setOnClickListener
                }

                if (roomCount == null || roomCount <= 0) {
                    edtRoomCount.error = "Vui lòng nhập số phòng > 0"
                    return@setOnClickListener
                }

                createRoomsForNewFloor(floor, roomCount)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // build mã phòng theo prefix hiện tại (F, HTL, ...)
    private fun buildRoomDisplayCode(floor: Int, roomNumber: String, prefix: String = Room.prefix): String {
        val p = prefix.ifBlank { "F" }
        val floorStr = floor.toString().padStart(2, '0')
        val blockChar = ('A'.code + (floor - 1)).toChar()
        val roomStr = roomNumber.padStart(2, '0')
        return "${p}${floorStr}${blockChar}${roomStr}"
    }

    private fun createRoomsForNewFloor(floor: Int, roomCount: Int) {
        val hotel = hotelId ?: return
        val roomsCollection = db.collection("hotels")
            .document(hotel)
            .collection("rooms")

        val existingCodes = allRooms.map { it.displayCode }.toSet()

        val batch = db.batch()
        var createdCount = 0

        for (i in 1..roomCount) {
            val roomNumber = String.format("%02d", i)
            val displayCode = buildRoomDisplayCode(floor, roomNumber)

            if (existingCodes.contains(displayCode)) {
                continue
            }

            val docRef = roomsCollection.document(displayCode)

            val roomData = mapOf(
                "room_id"      to displayCode,
                "floor"        to floor,
                "room_number"  to roomNumber,
                "room_type_id" to "",
                "status_id"    to "room_available"
            )

            batch.set(docRef, roomData)
            createdCount++
        }

        if (createdCount == 0) {
            Toast.makeText(
                this,
                "Tất cả phòng của tầng $floor đã tồn tại, không có phòng mới nào được tạo.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Đã tạo $createdCount phòng cho tầng $floor",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Lỗi khi tạo tầng/phòng: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // ========== Xóa phòng ==========

    private fun deleteRoomsWithValidation(
        roomsToDelete: List<Room>,
        titleForConfirm: String
    ) {
        if (roomsToDelete.isEmpty()) {
            Toast.makeText(this, "Không có phòng nào để xóa", Toast.LENGTH_SHORT).show()
            return
        }

        val blockedStatuses = setOf("room_occupied", "room_pending")

        val blockedRooms = roomsToDelete.filter { it.status_id in blockedStatuses }

        if (blockedRooms.isNotEmpty()) {
            val msg = buildString {
                append("Không thể xóa vì các phòng sau đang được sử dụng/đang được đặt:\n")
                append(blockedRooms.joinToString(", ") { it.displayCode })
            }

            AlertDialog.Builder(this)
                .setTitle("Không thể xóa")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val message = buildString {
            append("Bạn có chắc muốn xóa ")
            append(roomsToDelete.size)
            append(" phòng?\n\n")
            append(roomsToDelete.joinToString(", ") { it.displayCode })
        }

        AlertDialog.Builder(this)
            .setTitle(titleForConfirm)
            .setMessage(message)
            .setPositiveButton("Xóa") { _, _ ->
                performDeleteRooms(roomsToDelete)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun performDeleteRooms(rooms: List<Room>) {
        val hotel = hotelId ?: return
        val roomsCollection = db.collection("hotels")
            .document(hotel)
            .collection("rooms")

        val batch = db.batch()

        rooms.forEach { room ->
            val docRef = roomsCollection.document(room.room_id)
            batch.delete(docRef)
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Đã xóa ${rooms.size} phòng",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Lỗi khi xóa phòng: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showDeleteRoomsDialog() {
        val roomsList = if (selectedFloor != null && selectedFloor != 0) {
            allRooms.filter { it.floor == selectedFloor }
        } else {
            allRooms
        }

        if (roomsList.isEmpty()) {
            Toast.makeText(this, "Không có phòng nào để xóa", Toast.LENGTH_SHORT).show()
            return
        }

        val roomNames = roomsList.map { it.displayCode }.toTypedArray()
        val checked = BooleanArray(roomsList.size)
        val selectedRooms = mutableListOf<Room>()

        AlertDialog.Builder(this)
            .setTitle("Chọn phòng cần xóa")
            .setMultiChoiceItems(roomNames, checked) { _, which, isChecked ->
                val room = roomsList[which]
                if (isChecked) {
                    if (!selectedRooms.contains(room)) {
                        selectedRooms.add(room)
                    }
                } else {
                    selectedRooms.remove(room)
                }
            }
            .setPositiveButton("Xóa") { _, _ ->
                if (selectedRooms.isEmpty()) {
                    Toast.makeText(this, "Bạn chưa chọn phòng nào", Toast.LENGTH_SHORT).show()
                } else {
                    deleteRoomsWithValidation(selectedRooms, "Xóa phòng đã chọn")
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ========== Đổi tiền tố mã phòng ==========

    private fun showUpdateRoomNameDialog() {
        val editText = EditText(this).apply {
            filters = arrayOf(InputFilter.LengthFilter(3))
            inputType = InputType.TYPE_CLASS_TEXT
            setText(Room.prefix)
            setSelection(text.length)

            addTextChangedListener(object : TextWatcher {
                private var internalChange = false
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (internalChange) return
                    val current = s?.toString() ?: ""
                    val upper = current.uppercase(Locale.getDefault())
                    if (upper != current) {
                        internalChange = true
                        setText(upper)
                        setSelection(upper.length)
                        internalChange = false
                    }
                }
            })
        }

        AlertDialog.Builder(this)
            .setTitle("Đổi tiền tố mã phòng")
            .setMessage("Nhập tiền tố mới (tối đa 3 ký tự). Ví dụ: F, HTL, RM...")
            .setView(editText)
            .setPositiveButton("Lưu") { _, _ ->
                val input = editText.text.toString()
                    .trim()
                    .uppercase(Locale.getDefault())
                    .take(3)

                if (input.isEmpty()) {
                    Toast.makeText(this, "Tiền tố không được để trống", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                Room.prefix = input

                val hotel = hotelId
                if (hotel != null) {
                    val prefs = getSharedPreferences("roomio_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("room_prefix_$hotel", input)
                        .apply()
                }

                // Đổi luôn trên Firestore
                applyPrefixChangeOnFirestore(input)

                // Refresh UI tức thì
                roomsAdapter.submitList(allRooms.toList())

                Toast.makeText(this, "Đã đổi tiền tố thành \"$input\"", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // build code mới từ Room + prefix chỉ định (dùng khi đổi Firestore)
    private fun buildRoomCodeWithPrefix(prefix: String, room: Room): String {
        val p = prefix.ifBlank { "F" }
        val floorStr = room.floor.toString().padStart(2, '0')
        val patternWithBlock = Regex("^[A-Z]\\d{2}$")

        return if (patternWithBlock.matches(room.room_number)) {
            "${p}${floorStr}${room.room_number}"
        } else {
            val blockChar = ('A'.code + (room.floor - 1)).toChar()
            val roomStr = room.room_number.padStart(2, '0')
            "${p}${floorStr}${blockChar}${roomStr}"
        }
    }

    // Đổi prefix cho toàn bộ rooms trong Firestore
    private fun applyPrefixChangeOnFirestore(newPrefix: String) {
        val hotel = hotelId ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val roomsSnap = db.collection("hotels")
                    .document(hotel)
                    .collection("rooms")
                    .get()
                    .await()

                if (roomsSnap.isEmpty) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@RoomHotelActivity,
                            "Không có phòng nào để đổi tiền tố",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val batch = db.batch()
                var changedCount = 0

                for (doc in roomsSnap.documents) {
                    val room = doc.toObject(Room::class.java) ?: continue
                    val oldId = doc.id

                    val newId = buildRoomCodeWithPrefix(newPrefix, room)
                    val newDocRef = doc.reference.parent.document(newId)
                    val data = (doc.data ?: continue).toMutableMap()
                    data["room_id"] = newId

                    batch.set(newDocRef, data)

                    if (newId != oldId) {
                        batch.delete(doc.reference)
                        changedCount++
                    }
                }

                batch.commit().await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RoomHotelActivity,
                        "Đã đổi tiền tố trên Firestore cho $changedCount phòng",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RoomHotelActivity,
                        "Lỗi khi đổi tiền tố: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ========== Hỗ trợ file upload ==========

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
