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
import com.tdc.nhom6.roomio.adapters.ImageListAdapter
import com.tdc.nhom6.roomio.adapters.ServiceAdapter
import com.tdc.nhom6.roomio.apis.CloudinaryRepository
import com.tdc.nhom6.roomio.databinding.EditHotelLayoutBinding
import com.tdc.nhom6.roomio.models.Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EditHotelActivity : AppCompatActivity() {

    private lateinit var binding: EditHotelLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var cloudinaryRepository: CloudinaryRepository

    private var hotelId: String? = null
    private var currentImageUrls: MutableList<String> = mutableListOf()
    private var selectedNewUris: MutableList<Uri> = mutableListOf()

    private var allServices: List<Service> = emptyList()
    private val selectedServiceRates: MutableMap<String, Double> = mutableMapOf()

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
        loadAllServices()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Hủy bỏ hành động")
                .setMessage("Hành động đang được thực hiện vẫn chưa được lưu, đồng ý thoát?")
                .setPositiveButton("Đồng ý") { _, _ -> finish() }
                .setNegativeButton("Hủy", null)
                .show()
        }

        binding.btnChangeImage.setOnClickListener { imagePickerLauncher.launch("image/*") }

        binding.btnAddFacility.setOnClickListener { showAddServiceDialog() }

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
                val serviceIds = doc.get("serviceIds") as? List<String> ?: emptyList()

                currentImageUrls = imageList.toMutableList()

                launch(Dispatchers.Main) {
                    binding.edtHotelName.setText(name)
                    if (imageList.isNotEmpty())
                        Glide.with(this@EditHotelActivity).load(imageList.first()).into(binding.imgHotel)

                    val adapter = ImageListAdapter(currentImageUrls) { url -> removeImage(url) }
                    binding.rvCurrentImages.adapter = adapter
                    binding.rvFacilities.adapter = ServiceAdapter(allServices, selectedServiceRates)
                }

                // Load giá dịch vụ
                val rateSnapshot = db.collection("serviceRates")
                    .whereEqualTo("hotel_id", hotelId)
                    .get().await()
                rateSnapshot.documents.forEach {
                    val serviceId = it.getString("service_id") ?: return@forEach
                    val price = it.getDouble("price") ?: 0.0
                    selectedServiceRates[serviceId] = price
                }

                launch(Dispatchers.Main) {
                    binding.rvFacilities.adapter = ServiceAdapter(allServices, selectedServiceRates)
                }
            }
        } catch (e: Exception) {
            Log.e("EditHotel", "Lỗi tải dữ liệu: ${e.message}")
        }
    }

    private fun loadAllServices() = lifecycleScope.launch(Dispatchers.IO) {
        try {
            // Dịch vụ chung
            val globalSnap = db.collection("services").get().await()
            val globalServices = globalSnap.documents.map {
                Service(
                    id = it.id,
                    service_name = it.getString("service_name") ?: "",
                    description = it.getString("description") ?: ""
                )
            }

            // Dịch vụ riêng của khách sạn
            val hotelSnap = db.collection("hotels")
                .document(hotelId!!)
                .collection("services")
                .get().await()
            val hotelServices = hotelSnap.documents.map {
                Service(
                    id = it.id,
                    service_name = it.getString("service_name") ?: "",
                    description = it.getString("description") ?: ""
                )
            }

            allServices = (globalServices + hotelServices).sortedBy { it.service_name }

            launch(Dispatchers.Main) {
                binding.rvFacilities.adapter = ServiceAdapter(allServices, selectedServiceRates)
            }
        } catch (e: Exception) {
            Log.e("EditHotel", "Lỗi tải danh sách dịch vụ: ${e.message}")
        }
    }


    // ----------------------------------------------------
    // REMOVE IMAGE
    // ----------------------------------------------------
    private fun removeImage(url: Int) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa ảnh")
            .setMessage("Bạn có chắc chắn muốn xóa ảnh này không?")
            .setPositiveButton("Xóa") { _, _ ->
                currentImageUrls.removeAt(url)
                (binding.rvCurrentImages.adapter as? ImageListAdapter)?.notifyDataSetChanged()
                Toast.makeText(this, "Đã xóa ảnh khỏi danh sách tạm", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ----------------------------------------------------
    // ADD NEW SERVICE
    // ----------------------------------------------------
    private fun showAddServiceDialog() {
        val input = EditText(this).apply {
            hint = "Nhập tên dịch vụ mới"
            setPadding(50, 50, 50, 50)
        }

        AlertDialog.Builder(this)
            .setTitle("Thêm dịch vụ")
            .setView(input)
            .setPositiveButton("Thêm") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) addNewService(name)
                else Toast.makeText(this, "Tên không được trống", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun addNewService(name: String) {
        val newData = hashMapOf(
            "service_name" to name,
            "description" to "Dịch vụ riêng của khách sạn"
        )

        db.collection("hotels")
            .document(hotelId!!)
            .collection("services")
            .add(newData)
            .addOnSuccessListener { ref ->
                // Ghi ID dịch vụ mới vào danh sách serviceIds của khách sạn
                db.collection("hotels").document(hotelId!!)
                    .update("serviceIds", com.google.firebase.firestore.FieldValue.arrayUnion(ref.id))

                selectedServiceRates[ref.id] = 0.0
                loadAllServices()

                Toast.makeText(this, "Đã thêm dịch vụ riêng '$name' cho khách sạn này", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi khi thêm dịch vụ!", Toast.LENGTH_SHORT).show()
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
        val allUrls = (currentImageUrls + newUrls).distinct()

        updateFirestore(newName, allUrls)
    }

    private fun updateFirestore(newName: String, imageUrls: List<String>) {
        val updates = mapOf(
            "hotelName" to newName,
            "serviceIds" to selectedServiceRates.keys.toList(),
            "images" to imageUrls
        )

        db.collection("hotels").document(hotelId!!)
            .update(updates)
            .addOnSuccessListener {
                saveServiceRates()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi lưu dữ liệu!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveServiceRates() = lifecycleScope.launch(Dispatchers.IO) {
        val batch = db.batch()
        val collection = db.collection("serviceRates")

        selectedServiceRates.forEach { (serviceId, price) ->
            val newDoc = collection.document()
            batch.set(newDoc, mapOf(
                "hotel_id" to hotelId,
                "service_id" to serviceId,
                "price" to price
            ))
        }

        try {
            batch.commit().await()
            launch(Dispatchers.Main) {
                Toast.makeText(this@EditHotelActivity, "Lưu thay đổi thành công!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
        } catch (e: Exception) {
            Log.e("EditHotel", "Lỗi lưu serviceRates: ${e.message}")
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
