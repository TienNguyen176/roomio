package com.tdc.nhom6.roomio.activities.admin

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
import com.google.firebase.Timestamp
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
    private lateinit var documentId: String

    companion object {
        const val REQUEST_APPROVED = "hotel_request_approved"
        const val REQUEST_REJECTED = "hotel_request_rejected"
        const val STATUS_ID = "status_id"
        const val UPDATED_AT = "updated_at"
        const val REASON_REJECTED = "reason_rejected"
        const val HOTEL_STATUS_ID = "hotel_active"
        const val ROOM_STATUS_ID = "room_available"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HotelApprovalLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        userId = intent.getStringExtra("user_id") ?: ""
        documentId = intent.getStringExtra("hotel_request_id") ?: ""

        if (documentId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy yêu cầu!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        supportActionBar?.title = "UserID - $userId"

        loadHotelRequest(documentId)
        setEvent()
    }

    private fun setEvent() {
        binding.apply {
            btnTuChoi.setOnClickListener { showRejectDialog() }
            btnDongY.setOnClickListener { showApproveDialog() }
        }
    }

    private fun formatDate(timestamp: Any?): String {
        return if (timestamp is Timestamp) {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(timestamp.toDate())
        } else {
            timestamp?.toString() ?: ""
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadHotelRequest(docId: String) {
        db.collection("hotelRequests")
            .document(docId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Không tìm thấy dữ liệu", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val data = doc.data ?: return@addOnSuccessListener

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

                    val floors = (data["hotel_floors"] as? Number)?.toInt() ?: 0
                    val rooms = (data["hotel_total_rooms"] as? Number)?.toInt() ?: 0
                    tvQuyMoHotel.text = "$floors tầng - $rooms phòng"
                }

                val hotelTypeId = data["hotel_type_id"]?.toString()
                loadHotelTypeName(hotelTypeId ?: "")

                val cccdImages = data["cccd_image"] as? List<String> ?: emptyList()
                val licenseImages = data["license"] as? List<String> ?: emptyList()

                Glide.with(this).load(cccdImages.getOrNull(0)).placeholder(R.drawable.add_photo).error(R.drawable.add_photo).into(binding.imgMatTruoc)
                Glide.with(this).load(cccdImages.getOrNull(1)).placeholder(R.drawable.add_photo).error(R.drawable.add_photo).into(binding.imgMatSau)

                updateCheckIcon(binding.imgCheckGiayPhepKinhDoanh, licenseImages.getOrNull(0))
                updateCheckIcon(binding.imgCheckGiayPhepPCCC, licenseImages.getOrNull(1))
                updateCheckIcon(binding.imgCheckGiayPhepANTT, licenseImages.getOrNull(2))
                updateCheckIcon(binding.imgCheckGiayPhepVSATTP, licenseImages.getOrNull(3))

                binding.imgMatTruoc.setOnClickListener { showImageDialog(cccdImages.getOrNull(0)) }
                binding.imgMatSau.setOnClickListener { showImageDialog(cccdImages.getOrNull(1)) }
                binding.imgViewGiayPhepKinhDoanh.setOnClickListener { showImageDialog(licenseImages.getOrNull(0)) }
                binding.imgViewGiayPhepPCCC.setOnClickListener { showImageDialog(licenseImages.getOrNull(1)) }
                binding.imgViewGiayPhepANTT.setOnClickListener { showImageDialog(licenseImages.getOrNull(2)) }
                binding.imgViewGiayPhepVSATTP.setOnClickListener { showImageDialog(licenseImages.getOrNull(3)) }

                val status = data["status_id"]?.toString() ?: ""
                handleStatusUI(status, data)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi tải dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    @SuppressLint("SetTextI18n")
    private fun handleStatusUI(status: String, data: Map<String, Any>) {
        val updatedAt = formatDate(data["updated_at"])
        binding.apply {
            when (status) {
                REQUEST_REJECTED -> {
                    btnDongY.visibility = android.view.View.GONE
                    btnTuChoi.visibility = android.view.View.GONE
                    val reason = data[REASON_REJECTED]?.toString() ?: "Không rõ lý do"
                    tvStatus.apply {
                        text = "Đã từ chối vào ngày: $updatedAt\nLý do: $reason"
                        setBackgroundResource(R.drawable.bg_rejected_status)
                    }
                }
                REQUEST_APPROVED -> {
                    btnDongY.visibility = android.view.View.GONE
                    btnTuChoi.visibility = android.view.View.GONE
                    tvStatus.text = "Đã duyệt đơn vào ngày: $updatedAt"
                    tvStatus.setBackgroundResource(R.drawable.bg_approved_status)
                }
                else -> {
                    btnDongY.visibility = android.view.View.VISIBLE
                    btnTuChoi.visibility = android.view.View.VISIBLE
                    tvStatus.text = "Chờ duyệt"
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
                        val progress = Dialog(this@HotelApprovalActivity)
                        progress.setContentView(R.layout.dialog_loading)
                        progress.setCancelable(false)
                        progress.show()

                        approveRequest {
                            progress.dismiss()
                            dismiss()
                        }
                    }
                    getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { dismiss() }
                }
            }.show()
    }

    private fun approveRequest(onComplete: () -> Unit) {
        val id = documentId
        db.collection("hotelRequests").document(id).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    Toast.makeText(this, "Không tìm thấy yêu cầu!", Toast.LENGTH_SHORT).show()
                    onComplete()
                    return@addOnSuccessListener
                }

                val requestData = snapshot.data ?: return@addOnSuccessListener

                db.collection("hotelRequests").document(id)
                    .update(
                        mapOf(
                            STATUS_ID to REQUEST_APPROVED,
                            UPDATED_AT to Timestamp.now(),
                            REASON_REJECTED to ""
                        )
                    )
                    .addOnSuccessListener {
                        createHotelFromRequest(requestData) { hotelId ->
                            if (hotelId != null) {
                                createHotelRooms(hotelId, requestData) {
                                    updateUserRole(userId) {
                                        Toast.makeText(this, "Duyệt & thêm khách sạn thành công!", Toast.LENGTH_LONG).show()
                                        loadHotelRequest(documentId)
                                        onComplete()
                                    }
                                }
                            } else {
                                Toast.makeText(this, "Lỗi khi thêm khách sạn!", Toast.LENGTH_SHORT).show()
                                onComplete()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Lỗi cập nhật yêu cầu: ${e.message}", Toast.LENGTH_LONG).show()
                        onComplete()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi tải dữ liệu yêu cầu: ${e.message}", Toast.LENGTH_LONG).show()
                onComplete()
            }
    }

    @SuppressLint("DefaultLocale")
    private fun createHotelFromRequest(data: Map<String, Any>, onComplete: (String?) -> Unit) {
        val counterRef = db.collection("counters").document("hotelCounter")
        db.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            val current = snapshot.getLong("current") ?: 0
            val next = current + 1
            transaction.update(counterRef, "current", next)

            val nextId = String.format("hotel-%03d", next)

            val newHotel = HotelModel(
                ownerId = data["user_id"] as? String ?: "",
                hotelName = data["hotel_name"] as? String ?: "",
                hotelAddress = data["hotel_address"] as? String ?: "",
                hotelFloors = (data["hotel_floors"] as? Number)?.toInt() ?: 0,
                hotelTotalRooms = (data["hotel_total_rooms"] as? Number)?.toInt() ?: 0,
                pricePerNight = 0.0,
                images = listOf(),
                description = "",
                statusId = HOTEL_STATUS_ID,
                typeId = data["hotel_type_id"] as? String ?: "",
                totalReviews = 0,
                averageRating = 0.0,
                createdAt = Timestamp.now()
            )

            val hotelRef = db.collection("hotels").document(nextId)
            transaction.set(hotelRef, newHotel)

            nextId
        }.addOnSuccessListener { newHotelId -> onComplete(newHotelId) }
            .addOnFailureListener { e ->
                e.printStackTrace()
                onComplete(null)
            }
    }

    private fun createHotelRooms(hotelId: String, data: Map<String, Any>, onComplete: () -> Unit) {
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
            val floorLetter = ('A' + (floor - 1))
            for (i in 1..roomsPerFloor) {
                val roomNumber = "%c%02d".format(floorLetter, i)
                val roomId = "F%02d%s".format(floor, roomNumber)
                val roomData = RoomModel(
                    room_id = roomId,
                    room_number = roomNumber,
                    floor = floor,
                    room_type_id = "",
                    status_id = ROOM_STATUS_ID
                )
                batch.set(roomsRef.document(roomId), roomData)
            }
        }

        batch.commit()
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Lỗi tạo phòng: ${e.message}")
                onComplete()
            }
    }

    private fun updateUserRole(userId: String, onComplete: () -> Unit) {
        db.collection("users").document(userId)
            .update("roleId", "owner")
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onComplete() }
    }

    private fun showRejectDialog() {
        val editText = EditText(this).apply { hint = "Nhập lý do từ chối"; setPadding(50, 40, 50, 40) }

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

                        val progress = Dialog(this@HotelApprovalActivity)
                        progress.setContentView(R.layout.dialog_loading)
                        progress.setCancelable(false)
                        progress.show()

                        rejectRequest(reason) {
                            progress.dismiss()
                            dismiss()
                        }
                    }
                    getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { dismiss() }
                }
            }.show()
    }

    private fun rejectRequest(reason: String, onComplete: () -> Unit) {
        val now = Timestamp.now()

        db.collection("hotelRequests")
            .document(documentId)
            .update(
                mapOf(
                    STATUS_ID to REQUEST_REJECTED,
                    REASON_REJECTED to reason,
                    UPDATED_AT to now
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Đã từ chối yêu cầu!", Toast.LENGTH_SHORT).show()
                loadHotelRequest(documentId)
                onComplete()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi cập nhật: ${it.message}", Toast.LENGTH_LONG).show()
                onComplete()
            }
    }

    private fun loadHotelTypeName(hotelTypeId: String) {
        if (hotelTypeId.isEmpty()) {
            binding.tvLoaiHinhHotel.text = "Không xác định"
            return
        }

        db.collection("hotelTypes").document(hotelTypeId)
            .get()
            .addOnSuccessListener { doc ->
                binding.tvLoaiHinhHotel.text = doc.getString("type_name") ?: "Không xác định"
            }
            .addOnFailureListener {
                binding.tvLoaiHinhHotel.text = "Lỗi tải loại hình"
            }
    }

    private fun updateCheckIcon(imageView: ImageView, url: String?) {
        imageView.setImageResource(if (url.isNullOrEmpty()) R.drawable.ic_check_cancel else R.drawable.ic_check_circle_outline)
    }

    private fun showImageDialog(imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) return

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
