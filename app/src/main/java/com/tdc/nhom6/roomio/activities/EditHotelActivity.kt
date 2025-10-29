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
import com.tdc.nhom6.roomio.adapters.ImageListAdapter
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
    private var currentImageUrls: MutableList<String> = mutableListOf()
    private var selectedNewUris: MutableList<Uri> = mutableListOf()

    private var allFacilities: List<Facility> = emptyList()
    private val selectedFacilityIds: MutableSet<String> = mutableSetOf()

    // Chọn nhiều ảnh mới
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedNewUris.clear()
            selectedNewUris.addAll(uris)
            binding.imgHotel.setImageURI(uris.first())
            Toast.makeText(this, "Đã chọn ${uris.size} ảnh mới", Toast.LENGTH_SHORT).show()
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

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Hủy bỏ hành động")
                .setMessage("Hành động đang được thực hiện vẫn chưa được lưu, đồng ý thoát?")
                .setPositiveButton("Đồng ý") { _, _ ->
                    finish()
                }
                .setNegativeButton("Hủy", null)
                .show()
             }
        binding.btnChangeImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.btnAddFacility.setOnClickListener { showAddFacilityDialog() }
        binding.btnSave.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Xác nhận lưu thay đổi")
                .setMessage("Bạn có chắc chắn muốn lưu các thay đổi cho khách sạn này không?")
                .setPositiveButton("Lưu") { _, _ ->
                    saveHotelData()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

    }

    // ----------------------------------------------------
    // LOAD HOTEL DATA
    // ----------------------------------------------------
    private fun loadHotelData() = lifecycleScope.launch(Dispatchers.IO) {
        try {
            val doc = db.collection("hotels").document(hotelId!!).get().await()
            if (doc.exists()) {
                val name = doc.getString("hotelName")
                val imageList = doc.get("images") as? List<String> ?: emptyList()
                val facilityIds = doc.get("facilityIds") as? List<String> ?: emptyList()

                currentImageUrls = imageList.toMutableList()
                selectedFacilityIds.addAll(facilityIds)

                launch(Dispatchers.Main) {
                    binding.edtHotelName.setText(name)
                    if (imageList.isNotEmpty())
                        Glide.with(this@EditHotelActivity).load(imageList.first()).into(binding.imgHotel)

                    val adapter = ImageListAdapter(currentImageUrls) { url -> removeImage(url) }
                    binding.rvCurrentImages.adapter = adapter
                    binding.rvFacilities.adapter = FacilityAdapter(allFacilities, selectedFacilityIds)
                }
            }
        } catch (e: Exception) {
            Log.e("EditHotel", "Lỗi tải dữ liệu: ${e.message}")
        }
    }

    private fun loadAllFacilities() = lifecycleScope.launch(Dispatchers.IO) {
        try {
            val snapshot = db.collection("facilities").get().await()
            allFacilities = snapshot.documents.map {
                Facility(
                    id = it.id,
                    facilities_name = it.getString("facilities_name") ?: "",
                    description = it.getString("description") ?: ""
                )
            }.sortedBy { it.facilities_name }

            launch(Dispatchers.Main) {
                binding.rvFacilities.adapter = FacilityAdapter(allFacilities, selectedFacilityIds)
            }
        } catch (e: Exception) {
            Log.e("EditHotel", "Lỗi tải tiện ích: ${e.message}")
        }
    }

    // ----------------------------------------------------
    // REMOVE IMAGE
    // ----------------------------------------------------
    private fun removeImage(url: String) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa ảnh")
            .setMessage("Bạn có chắc chắn muốn xóa ảnh này không? Ảnh sẽ chỉ bị xóa khi bạn bấm 'Lưu thay đổi'.")
            .setPositiveButton("Xóa") { _, _ ->
                currentImageUrls.remove(url)
                (binding.rvCurrentImages.adapter as? ImageListAdapter)?.notifyDataSetChanged()

                Toast.makeText(this, "Đã xóa ảnh khỏi danh sách tạm. Nhấn 'Lưu thay đổi' để cập nhật.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }



    // ----------------------------------------------------
    // ADD NEW FACILITY
    // ----------------------------------------------------
    private fun showAddFacilityDialog() {
        val input = EditText(this).apply {
            hint = "Nhập tên tiện ích mới"
            setPadding(50, 50, 50, 50)
        }

        AlertDialog.Builder(this)
            .setTitle("Thêm tiện ích")
            .setView(input)
            .setPositiveButton("Thêm") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) addNewFacility(name)
                else Toast.makeText(this, "Tên không được trống", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun addNewFacility(name: String) {
        val newData = hashMapOf(
            "facilities_name" to name,
            "description" to "Tiện ích được thêm bởi chủ khách sạn"
        )
        db.collection("facilities").add(newData)
            .addOnSuccessListener { ref ->
                selectedFacilityIds.add(ref.id)
                loadAllFacilities()
                Toast.makeText(this, "Đã thêm '$name'", Toast.LENGTH_SHORT).show()
            }
    }

    // ----------------------------------------------------
    // SAVE HOTEL
    // ----------------------------------------------------
    private fun saveHotelData() {
        val newName = binding.edtHotelName.text.toString().trim()
        if (newName.isEmpty()) {
            binding.edtHotelName.error = "Tên không được trống"
            return
        }

        if (selectedNewUris.isNotEmpty()) uploadAssetsAndSave(newName)
        else updateFirestore(newName, currentImageUrls)
    }

    private fun uploadAssetsAndSave(newName: String) = lifecycleScope.launch(Dispatchers.Main) {
        Toast.makeText(this@EditHotelActivity, "Đang tải ảnh mới...", Toast.LENGTH_SHORT).show()
        val newUrls = mutableListOf<String>()

        val files = withContext(Dispatchers.IO) {
            selectedNewUris.mapNotNull { uriToFile(it) }
        }

        val uploaded = withContext(Dispatchers.IO) {
            cloudinaryRepository.uploadMultipleImages(files, "hotel_images")
        }

        files.forEach { it.delete() }
        newUrls.addAll(uploaded.mapNotNull { it.secure_url })

        // Gộp ảnh cũ + ảnh mới
        val allUrls = (currentImageUrls + newUrls).distinct()
        updateFirestore(newName, allUrls)
    }

    private fun updateFirestore(newName: String, imageUrls: List<String>) {
        val updates = mapOf(
            "hotelName" to newName,
            "facilityIds" to selectedFacilityIds.toList(),
            "images" to imageUrls
        )

        db.collection("hotels").document(hotelId!!)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Lưu thay đổi thành công!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi lưu dữ liệu!", Toast.LENGTH_SHORT).show()
            }
    }


    // ----------------------------------------------------
    // UTIL
    // ----------------------------------------------------
    private fun uriToFile(uri: Uri): File? {
        val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}")
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
