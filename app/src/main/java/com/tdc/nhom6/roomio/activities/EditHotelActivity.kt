package com.tdc.nhom6.roomio.activities

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
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
                    .whereEqualTo("hotelId", hotelId)
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
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)

        val edtName = EditText(this).apply {
            hint = "Tên dịch vụ"
            setPadding(40, 40, 40, 40)
        }

        val edtPrice = EditText(this).apply {
            hint = "Giá dịch vụ"
            setPadding(40, 40, 40, 40)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(edtName)
            addView(edtPrice)
        }

        AlertDialog.Builder(this)
            .setTitle("Thêm dịch vụ mới")
            .setView(container)
            .setPositiveButton("Thêm") { _, _ ->
                val name = edtName.text.toString().trim()
                val priceText = edtPrice.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, "Tên dịch vụ không được trống", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val price = priceText.toDoubleOrNull()
                if (price == null) {
                    Toast.makeText(this, "Giá dịch vụ không hợp lệ", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                addNewService(name, price)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    private fun addNewService(name: String, price: Double) {
        val newService = hashMapOf(
            "service_name" to name,
            "description" to "Dịch vụ riêng của khách sạn"
        )

        db.collection("hotels")
            .document(hotelId!!)
            .collection("services")
            .add(newService)
            .addOnSuccessListener { ref ->

                // Ghi ID của service vào hotel
                db.collection("hotels")
                    .document(hotelId!!)
                    .update("serviceIds", com.google.firebase.firestore.FieldValue.arrayUnion(ref.id))

                // ❌ BỎ ĐOẠN NÀY để không tạo serviceRates ngay lập tức
                // db.collection("serviceRates")
                //     .add(
                //         mapOf(
                //             "hotelId" to hotelId,
                //             "service_id" to ref.id,
                //             "price" to price
                //         )
                //     )

                // Cập nhật ngay vào selectedServiceRates
                selectedServiceRates[ref.id] = price

                loadAllServices()

                Toast.makeText(this, "Đã thêm dịch vụ '$name' với giá $price", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi thêm dịch vụ: ${e.message}", Toast.LENGTH_SHORT).show()
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
        val collection = db.collection("serviceRates")

        try {
            // Lấy các doc serviceRates hiện có của khách sạn này
            val snap = collection
                .whereEqualTo("hotelId", hotelId)
                .get()
                .await()

            // Map: service_id -> docId
            val existingByServiceId = mutableMapOf<String, String>()
            for (doc in snap.documents) {
                val serviceId = doc.getString("service_id") ?: continue
                existingByServiceId[serviceId] = doc.id
            }

            val batch = db.batch()

            // 1. Update hoặc tạo mới các service đang có trong selectedServiceRates
            selectedServiceRates.forEach { (serviceId, price) ->
                val existingDocId = existingByServiceId[serviceId]
                if (existingDocId != null) {
                    // Đã có doc -> update price
                    val ref = collection.document(existingDocId)
                    batch.update(ref, mapOf("price" to price))
                } else {
                    // Chưa có -> tạo doc mới
                    val newRef = collection.document()
                    batch.set(
                        newRef,
                        mapOf(
                            "hotelId" to hotelId,
                            "service_id" to serviceId,
                            "price" to price
                        )
                    )
                }
            }

            // 2. (Optional) Xoá các doc cũ không còn trong selectedServiceRates
            val servicesToDelete = existingByServiceId.keys - selectedServiceRates.keys
            servicesToDelete.forEach { serviceId ->
                val docId = existingByServiceId[serviceId] ?: return@forEach
                val ref = collection.document(docId)
                batch.delete(ref)
            }

            // Commit batch
            batch.commit().await()

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@EditHotelActivity,
                    "Lưu thay đổi thành công!",
                    Toast.LENGTH_SHORT
                ).show()
                setResult(Activity.RESULT_OK)
                finish()
            }

        } catch (e: Exception) {
            Log.e("EditHotel", "Lỗi lưu serviceRates: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@EditHotelActivity,
                    "Lỗi lưu giá dịch vụ: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
