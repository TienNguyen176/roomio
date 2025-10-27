// activities/EditHotelActivity.kt

package com.tdc.nhom6.roomio.activities

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.adapters.FacilityAdapter
import com.tdc.nhom6.roomio.databinding.EditHotelLayoutBinding // Sử dụng tên binding của bạn
import com.tdc.nhom6.roomio.models.Facility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EditHotelActivity : AppCompatActivity() {

    private lateinit var binding: EditHotelLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private var hotelId: String? = null
    private var currentImageUrl: String? = null

    private var allFacilities: List<Facility> = emptyList()
    private val selectedFacilityIds: MutableSet<String> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EditHotelLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hotelId = intent.getStringExtra("hotelId")
        if (hotelId.isNullOrEmpty()) {
            Toast.makeText(this, "Không tìm thấy ID khách sạn!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupListeners()
        loadHotelData()
        loadAllFacilities()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Nút Thay đổi Ảnh (Chỉ thông báo đang bảo trì)
        binding.btnChangeImage.setOnClickListener {
            Toast.makeText(this, "Tính năng tải ảnh đang được bảo trì.", Toast.LENGTH_SHORT).show()
        }

        // Nút Thêm Tiện ích Mới (Giả định ID: btnAddFacility)
        binding.btnAddFacility.setOnClickListener {
            showAddFacilityDialog()
        }

        binding.btnSave.setOnClickListener { saveHotelData() }
    }

// ----------------------------------------------------------------------
// TẢI DỮ LIỆU TỪ FIREBASE
// ----------------------------------------------------------------------

    /**
     * Tải dữ liệu khách sạn hiện tại (Tên, Ảnh, Tiện ích đã chọn)
     */
    private fun loadHotelData() = lifecycleScope.launch(Dispatchers.IO) {
        try {
            val hotelDoc = db.collection("hotels").document(hotelId!!).get().await()

            if (hotelDoc.exists()) {
                val name = hotelDoc.getString("hotelName")
                val imageUrl = hotelDoc.getString("hotelImageUrl")

                @Suppress("UNCHECKED_CAST")
                val facilityIds = hotelDoc.get("facilityIds") as? List<String> ?: emptyList()

                launch(Dispatchers.Main) {
                    binding.edtHotelName.setText(name)
                    currentImageUrl = imageUrl

                    // Hiển thị ảnh cũ (nếu có)
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this@EditHotelActivity).load(imageUrl).into(binding.imgHotel)
                    }

                    selectedFacilityIds.addAll(facilityIds)
                    // Cập nhật trạng thái chọn của Adapter
                    (binding.rvFacilities.adapter as? FacilityAdapter)?.notifyDataSetChanged()
                }
            }
        } catch (e: Exception) {
            Log.e("EditHotel", "Lỗi tải dữ liệu khách sạn: ${e.message}", e)
            launch(Dispatchers.Main) {
                Toast.makeText(this@EditHotelActivity, "Lỗi tải dữ liệu.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Tải tất cả các tiện ích có sẵn từ collection 'facilities'
     */
    private fun loadAllFacilities() = lifecycleScope.launch(Dispatchers.IO) {
        try {
            val snapshot = db.collection("facilities").get().await()

            allFacilities = snapshot.documents.map { doc ->
                Facility(
                    id = doc.id,
                    facilities_name = doc.getString("facilities_name") ?: "",
                    description = doc.getString("description") ?: ""
                )
            }.sortedBy { it.facilities_name }

            launch(Dispatchers.Main) {
                // Khởi tạo Adapter
                binding.rvFacilities.adapter = FacilityAdapter(allFacilities, selectedFacilityIds)
            }
        } catch (e: Exception) {
            Log.e("EditHotel", "Lỗi tải tiện ích: ${e.message}", e)
            launch(Dispatchers.Main) {
                Toast.makeText(this@EditHotelActivity, "Lỗi tải tiện ích.", Toast.LENGTH_SHORT).show()
            }
        }
    }

// -----------------------------------------------------------------------------------------------
// THÊM TIỆN ÍCH MỚI
// -----------------------------------------------------------------------------------------------

    /**
     * Hiển thị Dialog cho phép chủ khách sạn nhập tên tiện ích mới.
     */
    private fun showAddFacilityDialog() {
        val input = EditText(this).apply {
            hint = "Nhập tên tiện ích (Ví dụ: Bể bơi vô cực)"
            setPadding(50, 50, 50, 50)
        }

        AlertDialog.Builder(this)
            .setTitle("Thêm Tiện ích Mới")
            .setView(input)
            .setPositiveButton("Thêm") { _, _ ->
                val facilityName = input.text.toString().trim()
                if (facilityName.isNotEmpty()) {
                    addNewFacilityToFirebase(facilityName)
                } else {
                    Toast.makeText(this, "Tên tiện ích không được trống.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /**
     * Thêm tiện ích mới vào collection 'facilities' và tải lại danh sách.
     */
    private fun addNewFacilityToFirebase(name: String) {
        val newFacilityData = hashMapOf(
            "facilities_name" to name,
            "description" to "Tiện ích được thêm bởi chủ khách sạn"
        )

        db.collection("facilities").add(newFacilityData)
            .addOnSuccessListener { newDocRef ->
                Toast.makeText(this, "Đã thêm tiện ích '$name'", Toast.LENGTH_LONG).show()

                // Tự động chọn tiện ích mới thêm
                selectedFacilityIds.add(newDocRef.id)

                // Tải lại toàn bộ danh sách tiện ích để cập nhật RecyclerView
                loadAllFacilities()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi khi thêm tiện ích: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EditHotel", "Lỗi thêm tiện ích", e)
            }
    }

// -----------------------------------------------------------------------------------------------
// LƯU DỮ LIỆU
// -----------------------------------------------------------------------------------------------

    /**
     * Xử lý lưu dữ liệu khách sạn (Tên và Tiện ích đã chọn).
     */
    private fun saveHotelData() {
        val newName = binding.edtHotelName.text.toString().trim()
        if (newName.isEmpty()) {
            binding.edtHotelName.error = "Tên khách sạn không được để trống"
            return
        }

        val updates = mapOf<String, Any>(
            "hotelName" to newName,
            "hotelImageUrl" to (currentImageUrl ?: ""),
            "facilityIds" to selectedFacilityIds.toList()
        )

        db.collection("hotels").document(hotelId!!)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Lưu thay đổi thành công!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi lưu: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("EditHotel", "Lỗi lưu dữ liệu: ", e)
            }
    }
}