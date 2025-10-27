package com.tdc.nhom6.roomio.activities

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.adapters.FacilityAdapter
import com.tdc.nhom6.roomio.apis.CloudinaryRepository
import com.tdc.nhom6.roomio.databinding.EditHotelLayoutBinding
import com.tdc.nhom6.roomio.models.Facility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class EditHotelActivity : AppCompatActivity() {

    private lateinit var binding: EditHotelLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var cloudinaryRepository: CloudinaryRepository

    private var hotelId: String? = null
    private var currentImageUrl: String? = null

    private var selectedNewUris: MutableList<Uri> = mutableListOf()

    private var currentImageUrls: List<String> = emptyList() // Lưu trữ TẤT CẢ URLs cũ từ trường 'images'

    private var allFacilities: List<Facility> = emptyList()
    private val selectedFacilityIds: MutableSet<String> = mutableSetOf()

    /**
     * Launcher GỐC được cấu hình để cho phép chọn NHIỀU ẢNH.
     */
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedNewUris.clear()
            selectedNewUris.addAll(uris)

            // Lấy ảnh đầu tiên làm ảnh đại diện để hiển thị ngay
            val mainUri = uris.first()
            binding.imgHotel.setImageURI(mainUri)

            Toast.makeText(this, "Đã chọn ${uris.size} ảnh mới (Ảnh chính & Thư viện).", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EditHotelLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cloudinaryRepository = CloudinaryRepository(this)

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

// ----------------------------------------------------------------------
// SETUP & LISTENERS
// ----------------------------------------------------------------------

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnChangeImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnAddFacility.setOnClickListener {
            showAddFacilityDialog()
        }

        binding.btnSave.setOnClickListener { saveHotelData() }
    }

// ----------------------------------------------------------------------
// TẢI DỮ LIỆU TỪ FIREBASE
// ----------------------------------------------------------------------

    private fun loadHotelData() = lifecycleScope.launch(Dispatchers.IO) {
        try {
            val hotelDoc = db.collection("hotels").document(hotelId!!).get().await()

            if (hotelDoc.exists()) {
                val name = hotelDoc.getString("hotelName")

                // ĐỌC TẤT CẢ URLs TỪ TRƯỜNG 'images' (List<String>)
                @Suppress("UNCHECKED_CAST")
                val imageList = hotelDoc.get("images") as? List<String> ?: emptyList()
                currentImageUrls = imageList // Lưu TẤT CẢ URLs cũ

                val imageUrl = imageList.firstOrNull()
                currentImageUrl = imageUrl

                @Suppress("UNCHECKED_CAST")
                val facilityIds = hotelDoc.get("facilityIds") as? List<String> ?: emptyList()

                launch(Dispatchers.Main) {
                    binding.edtHotelName.setText(name)

                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this@EditHotelActivity).load(imageUrl).into(binding.imgHotel)
                    }

                    selectedFacilityIds.addAll(facilityIds)
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
                binding.rvFacilities.adapter = FacilityAdapter(allFacilities, selectedFacilityIds)
            }
        } catch (e: Exception) {
            Log.e("EditHotel", "Lỗi tải tiện ích: ${e.message}", e)
            launch(Dispatchers.Main) {
                Toast.makeText(this@EditHotelActivity, "Lỗi tải tiện ích.", Toast.LENGTH_SHORT).show()
            }
        }
    }

// ----------------------------------------------------------------------
// LOGIC THÊM TIỆN ÍCH
// ----------------------------------------------------------------------

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

    private fun addNewFacilityToFirebase(name: String) {
        val newFacilityData = hashMapOf(
            "facilities_name" to name,
            "description" to "Tiện ích được thêm bởi chủ khách sạn"
        )

        db.collection("facilities").add(newFacilityData)
            .addOnSuccessListener { newDocRef ->
                Toast.makeText(this, "Đã thêm tiện ích '$name'", Toast.LENGTH_LONG).show()

                selectedFacilityIds.add(newDocRef.id)

                loadAllFacilities()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi khi thêm tiện ích: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EditHotel", "Lỗi thêm tiện ích", e)
            }
    }

// ----------------------------------------------------------------------
// LƯU DỮ LIỆU VÀ TẢI ẢNH (Hợp nhất)
// ----------------------------------------------------------------------

    private fun saveHotelData() {
        val newName = binding.edtHotelName.text.toString().trim()
        if (newName.isEmpty()) {
            binding.edtHotelName.error = "Tên khách sạn không được để trống"
            return
        }

        if (selectedNewUris.isNotEmpty()) {
            uploadAssetsAndSave(newName)
        } else {
            // Nếu không có ảnh mới, lưu dữ liệu ngay lập tức (dùng ảnh cũ)
            updateFirestore(newName, currentImageUrls)
        }
    }

    /**
     * Tải ảnh đại diện và tất cả ảnh thư viện mới lên Cloudinary.
     */
    private fun uploadAssetsAndSave(newName: String) = lifecycleScope.launch(Dispatchers.Main) {
        if (selectedNewUris.isEmpty()) return@launch

        Toast.makeText(this@EditHotelActivity, "Đang tải ${selectedNewUris.size} ảnh lên...", Toast.LENGTH_LONG).show()

        val newMainUri = selectedNewUris.first()
        val newGalleryUris = selectedNewUris.subList(1, selectedNewUris.size)

        // Danh sách URLs cuối cùng (chỉ chứa các URLs mới được tải lên)
        val finalImageUrls: MutableList<String> = mutableListOf()

        // 1. Tải ảnh đại diện mới
        val mainImageFile = withContext(Dispatchers.IO) { uriToFile(newMainUri) }
        if (mainImageFile != null) {
            val result = withContext(Dispatchers.IO) {
                cloudinaryRepository.uploadSingleImage(mainImageFile, "hotel_images/main")
            }
            mainImageFile.delete()

            if (result != null && !result.secure_url.isNullOrBlank()) {
                finalImageUrls.add(result.secure_url) // Thêm ảnh đại diện vào List
            } else {
                Toast.makeText(this@EditHotelActivity, "Lỗi tải ảnh đại diện.", Toast.LENGTH_LONG).show()
            }
        }

        // 2. Tải các ảnh thư viện mới
        if (newGalleryUris.isNotEmpty()) {
            val filesToUpload = withContext(Dispatchers.IO) {
                newGalleryUris.mapNotNull { uriToFile(it) }
            }

            if (filesToUpload.isNotEmpty()) {
                val results = withContext(Dispatchers.IO) {
                    cloudinaryRepository.uploadMultipleImages(filesToUpload, "hotel_images/gallery")
                }

                filesToUpload.forEach { it.delete() }

                finalImageUrls.addAll(results.mapNotNull { it.secure_url }) // Thêm ảnh thư viện vào List
            }
        }

        // 3. Lưu kết quả cuối cùng vào Firestore
        updateFirestore(newName, finalImageUrls)
    }

    /**
     * Cập nhật tên và URL ảnh cuối cùng vào Firestore.
     */
    private fun updateFirestore(newName: String, finalImageUrls: List<String>) {
        val updates = mutableMapOf<String, Any>(
            "hotelName" to newName,
            "facilityIds" to selectedFacilityIds.toList(),
            // ❌ Loại bỏ galleryUrls khỏi updates
        )

        // ✅ LƯU TẤT CẢ URLs VÀO TRƯỜNG 'images' DUY NHẤT
        updates["images"] = finalImageUrls


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

// ----------------------------------------------------------------------
// HÀM TIỆN ÍCH
// ----------------------------------------------------------------------

    /**
     * HÀM TIỆN ÍCH: Chuyển URI sang File tạm thời để tải lên mạng.
     */
    private fun uriToFile(uri: Uri): File? {
        val contentResolver = applicationContext.contentResolver
        val tempFile = File(cacheDir, "temp_upload_${System.currentTimeMillis()}")

        try {
            contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
                FileOutputStream(tempFile).use { outputStream: FileOutputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return tempFile
        } catch (e: Exception) {
            Log.e("UriToFile", "Error creating temp file from URI: ${e.message}", e)
            return null
        }
    }
}