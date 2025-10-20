package com.tdc.nhom6.roomio.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.HotelApprovalLayoutBinding
import com.tdc.nhom6.roomio.models.HotelModel
import com.tdc.nhom6.roomio.models.RoomModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HotelApprovalActivity : AppCompatActivity() {
    private lateinit var binding: HotelApprovalLayoutBinding
    private val db = FirebaseFirestore.getInstance()

    private lateinit var userId: String
    private var documentId: String? = null

    val now = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HotelApprovalLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // ✅ Lấy userId từ Intent
        //userId = intent.getStringExtra("USER_ID") ?: ""
        userId = "zd8vs4NN5sezzAMVAmkmQXWh5rl2"
        supportActionBar?.title = "UserID - $userId"

        loadHotelRequest(userId)

        setEvent()
    }

    private fun setEvent() {
        binding.apply {
            btnTuChoi.setOnClickListener { showRejectDialog() }
            btnDongY.setOnClickListener { showApproveDialog() }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadHotelRequest(userId: String) {
        db.collection("hotelRequests")
            .whereEqualTo("user_id", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    val doc = query.documents[0]
                    val data = doc.data ?: return@addOnSuccessListener

                    documentId = doc.id

                    // Gán dữ liệu vào View
                    binding.apply {
                        tvUsername.text = data["username"]?.toString() ?: ""
                        tvDob.text = data["birth_date"]?.toString() ?: ""
                        tvGioiTinh.text = data["gender"]?.toString() ?: ""
                        tvCCCD.text = data["cccd_number"]?.toString() ?: ""
                        tvSoDienThoai.text = data["phone"]?.toString() ?: ""
                        tvEmail.text = data["email"]?.toString() ?: ""
                        tvDiaChiThuongTru.text = data["address"]?.toString() ?: ""
                        tvHotelName.text = data["hotel_name"]?.toString() ?: ""
                        tvDiaChiHotel.text = data["hotel_address"]?.toString() ?: ""

                        val floors = data["hotel_floors"]?.toString() ?: "0"
                        val rooms = data["hotel_total_rooms"]?.toString() ?: "0"
                        tvQuyMoHotel.text = "$floors tầng - $rooms phòng"
                    }

                    // Load tên loại hình khách sạn (type_name)
                    val hotelTypeId = data["hotel_type_id"]?.toString()
                    if (hotelTypeId != null) {
                        loadHotelTypeName(hotelTypeId)
                    } else {
                        binding.tvLoaiHinhHotel.text = "Không xác định"
                    }

                    // Load ảnh CCCD & Giấy phép
                    val cccdImages = data["cccd_image"] as? List<String> ?: emptyList()
                    val licenseImages = data["license"] as? List<String> ?: emptyList()

                    // Hiển thị ảnh CCCD
                    Glide.with(this)
                        .load(cccdImages.getOrNull(0))
                        .placeholder(R.drawable.add_photo)
                        .error(R.drawable.add_photo)
                        .into(binding.imgMatTruoc)

                    Glide.with(this)
                        .load(cccdImages.getOrNull(1))
                        .placeholder(R.drawable.add_photo)
                        .error(R.drawable.add_photo)
                        .into(binding.imgMatSau)

                    // Cập nhật check icon
                    updateCheckIcon(binding.imgCheckGiayPhepKinhDoanh, licenseImages.getOrNull(0))
                    updateCheckIcon(binding.imgCheckGiayPhepPCCC, licenseImages.getOrNull(1))
                    updateCheckIcon(binding.imgCheckGiayPhepANTT, licenseImages.getOrNull(2))
                    updateCheckIcon(binding.imgCheckGiayPhepVSATTP, licenseImages.getOrNull(3))

                    // Sự kiện xem ảnh
                    binding.imgMatTruoc.setOnClickListener { showImageDialog(cccdImages.getOrNull(0)) }
                    binding.imgMatSau.setOnClickListener { showImageDialog(cccdImages.getOrNull(1)) }

                    binding.imgViewGiayPhepKinhDoanh.setOnClickListener { showImageDialog(licenseImages.getOrNull(0)) }
                    binding.imgViewGiayPhepPCCC.setOnClickListener { showImageDialog(licenseImages.getOrNull(1)) }
                    binding.imgViewGiayPhepANTT.setOnClickListener { showImageDialog(licenseImages.getOrNull(2)) }
                    binding.imgViewGiayPhepVSATTP.setOnClickListener { showImageDialog(licenseImages.getOrNull(3)) }

                    // Xử lý hiển thị trạng thái
                    val status = data["status_id"]?.toString() ?: "hotel_request_pending"
                    handleStatusUI(status, data)

                } else {
                    Toast.makeText(this, "Không tìm thấy dữ liệu cho user này", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi khi tải dữ liệu: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun handleStatusUI(status: String, data: Map<String, Any>) {
        binding.apply {
            when (status) {
                "hotel_request_rejected" -> {
                    btnDongY.visibility = android.view.View.GONE
                    btnTuChoi.visibility = android.view.View.GONE
                    val updatedAt = data["updated_at"]?.toString() ?: ""
                    val reason = data["reason_rejected"]?.toString() ?: "Không rõ lý do"
                    tvStatus.apply {
                        text = "Đã từ chối vào ngày: $updatedAt\nLý do: $reason"
                        setBackgroundResource(R.drawable.bg_rejected_status)
                    }
                }

                "hotel_request_approved" -> {
                    btnDongY.visibility = android.view.View.GONE
                    btnTuChoi.visibility = android.view.View.GONE
                    val updatedAt = data["updated_at"]?.toString() ?: ""
                    tvStatus.text = "Đã duyệt đơn vào ngày: $updatedAt"
                    tvStatus.setBackgroundResource(R.drawable.bg_approved_status)
                }

                else -> {
                    btnDongY.visibility = android.view.View.VISIBLE
                    btnTuChoi.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun showApproveDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận duyệt đơn")
            .setMessage("Bạn có chắc chắn muốn duyệt đơn đăng ký này không?")
            .setPositiveButton("Đồng ý", null)
            .setNegativeButton("Hủy", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        // ✅ Hiển thị tiến trình xử lý
                        val progress = Dialog(this@HotelApprovalActivity)
                        progress.setContentView(R.layout.dialog_loading)
                        progress.setCancelable(false)
                        progress.show()

                        approveRequest {
                            progress.dismiss()
                            dismiss() // đóng dialog xác nhận
                        }
                    }

                    getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        dismiss()
                    }
                }
            }
            .show()
    }

    private fun approveRequest(onComplete: () -> Unit) {
        val id = documentId ?: return

        db.collection("hotelRequests").document(id).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    Toast.makeText(this, "Không tìm thấy yêu cầu!", Toast.LENGTH_SHORT).show()
                    onComplete()
                    return@addOnSuccessListener
                }

                val requestData = snapshot.data ?: return@addOnSuccessListener

                // 👉 Bước 1: cập nhật trạng thái duyệt
                db.collection("hotelRequests").document(id)
                    .update(
                        mapOf(
                            "status_id" to "hotel_request_approved",
                            "updated_at" to now,
                            "reason_rejected" to ""
                        )
                    )
                    .addOnSuccessListener {
                        // 👉 Bước 2: tạo khách sạn mới
                        createHotelFromRequest(requestData) { hotelId ->
                            if (hotelId != null) {
                                // 👉 Bước 3: tạo danh sách phòng
                                createHotelRooms(hotelId, requestData) {
                                    // 👉 Bước 4: cập nhật quyền user
                                    updateUserRole(userId) {
                                        Toast.makeText(this, "✅ Duyệt & thêm khách sạn thành công!", Toast.LENGTH_LONG).show()
                                        loadHotelRequest(userId)
                                        onComplete()
                                    }
                                }
                            } else {
                                Toast.makeText(this, "⚠️ Lỗi khi thêm khách sạn!", Toast.LENGTH_SHORT).show()
                                onComplete()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "❌ Lỗi cập nhật yêu cầu: ${e.message}", Toast.LENGTH_LONG).show()
                        onComplete()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "⚠️ Lỗi tải dữ liệu yêu cầu: ${it.message}", Toast.LENGTH_LONG).show()
                onComplete()
            }
    }

    @SuppressLint("DefaultLocale")
    private fun createHotelFromRequest(data: Map<String, Any>, onComplete: (String?) -> Unit) {
        val counterRef = db.collection("counters").document("hotelCounter")

        db.runTransaction { transaction ->
            // 🔹 Lấy số hiện tại
            val snapshot = transaction.get(counterRef)
            val current = snapshot.getLong("current") ?: 0
            val next = current + 1

            // 🔹 Cập nhật lại counter
            transaction.update(counterRef, "current", next)

            // 🔹 Sinh ID theo mẫu: hotel-001
            val nextId = String.format("hotel-%03d", next)

            // 🔹 Chuẩn bị dữ liệu khách sạn
            val newHotel = HotelModel(
                ownerId = data["user_id"] as? String ?: "",
                hotelName = data["hotel_name"] as? String ?: "",
                hotelAddress = data["hotel_address"] as? String ?: "",
                hotelFloors = (data["hotel_floors"] as? Number)?.toInt() ?: 0,
                hotelTotalRooms = (data["hotel_total_rooms"] as? Number)?.toInt() ?: 0,
                pricePerNight = 0.0,
                images = listOf(),
                description = "",
                statusId = "hotel_active",
                typeId = data["hotel_type_id"] as? String ?: "",
                totalReviews = 0,
                averageRating = 0.0,
                createdAt = now
            )

            // 🔹 Lưu vào Firestore với ID cố định
            val hotelRef = db.collection("hotels").document(nextId)
            transaction.set(hotelRef, newHotel)

            nextId // return để .addOnSuccessListener nhận được
        }.addOnSuccessListener { newHotelId ->
            onComplete(newHotelId)
        }.addOnFailureListener { e ->
            e.printStackTrace()
            onComplete(null)
        }
    }

    private fun createHotelRooms(
        hotelId: String,
        data: Map<String, Any>,
        onComplete: () -> Unit
    ) {
        val floors = (data["hotel_floors"] as? Number)?.toInt() ?: 0
        val totalRooms = (data["hotel_total_rooms"] as? Number)?.toInt() ?: 0

        if (floors == 0 || totalRooms == 0) {
            onComplete()
            return
        }

        val roomsPerFloor = totalRooms / floors
        val batch = db.batch()
        val roomsRef = db.collection("hotels").document(hotelId).collection("rooms")

        for (floor in 1..floors) {
            // 🔤 Lấy ký tự tầng: 1 -> A, 2 -> B, ...
            val floorLetter = ('A' + (floor - 1))

            for (i in 1..roomsPerFloor) {
                val roomNumber = "%c%02d".format(floorLetter, i)
                val roomId = "F%02d%s".format(floor, roomNumber)

                val roomData = RoomModel(
                    room_id = roomId,
                    room_number = roomNumber,
                    floor = floor,
                    room_type_id = "",
                    status_id = "room_available"
                )

                val roomDoc = roomsRef.document(roomId)
                batch.set(roomDoc, roomData)
            }
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d("Firestore", "✅ Đã tạo ${floors * roomsPerFloor} phòng cho khách sạn $hotelId")
                onComplete()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "⚠️ Lỗi tạo phòng: ${e.message}")
                onComplete()
            }
    }

    private fun updateUserRole(userId: String, onComplete: () -> Unit) {
        db.collection("users")
            .document(userId)
            .update("roleId", "owner")
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onComplete() }
    }

    private fun showRejectDialog() {
        val editText = EditText(this)
        editText.hint = "Nhập lý do từ chối"
        editText.setPadding(50, 40, 50, 40)

        AlertDialog.Builder(this)
            .setTitle("Từ chối yêu cầu")
            .setView(editText)
            .setPositiveButton("Xác nhận", null)
            .setNegativeButton("Hủy", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val reason = editText.text.toString().trim()
                        if (reason.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập lý do!", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        // ✅ Hiển thị tiến trình xử lý
                        val progress = Dialog(this@HotelApprovalActivity)
                        progress.setContentView(R.layout.dialog_loading)
                        progress.setCancelable(false)
                        progress.show()

                        rejectRequest(reason) {
                            progress.dismiss()
                            dismiss() // đóng dialog lý do
                        }
                    }

                    getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        dismiss()
                    }
                }
            }
            .show()
    }

    private fun rejectRequest(reason: String, onComplete: () -> Unit) {
        val now = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        db.collection("hotelRequests")
            .whereEqualTo("user_id", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    val docId = query.documents[0].id
                    db.collection("hotelRequests").document(docId)
                        .update(
                            mapOf(
                                "status_id" to "hotel_request_rejected",
                                "reason_rejected" to reason,
                                "updated_at" to now
                            )
                        )
                        .addOnSuccessListener {
                            Toast.makeText(this, "Đã từ chối yêu cầu!", Toast.LENGTH_SHORT).show()
                            loadHotelRequest(userId) // ✅ Load lại dữ liệu sau khi cập nhật
                            onComplete()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Lỗi cập nhật: ${it.message}", Toast.LENGTH_LONG).show()
                            onComplete()
                        }
                } else {
                    Toast.makeText(this, "Không tìm thấy yêu cầu!", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi Firestore: ${it.message}", Toast.LENGTH_LONG).show()
                onComplete()
            }
    }

    private fun loadHotelTypeName(hotelTypeId: String) {
        db.collection("hotelTypes")
            .document(hotelTypeId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("type_name") ?: "Không xác định"
                    binding.tvLoaiHinhHotel.text = name
                } else {
                    binding.tvLoaiHinhHotel.text = "Không xác định"
                }
            }
            .addOnFailureListener {
                binding.tvLoaiHinhHotel.text = "Lỗi tải loại hình"
            }
    }

    private fun updateCheckIcon(imageView: ImageView, url: String?) {
        if (url.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.ic_check_cancel) // ❌ chưa có ảnh
        } else {
            imageView.setImageResource(R.drawable.ic_check_circle_outline) // ✅ có ảnh
        }
    }

    private fun showImageDialog(imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) {
            return
        }
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_view_image, null)
        val img = view.findViewById<ImageView>(R.id.dialogImage)

        Glide.with(this).load(imageUrl).into(img)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}