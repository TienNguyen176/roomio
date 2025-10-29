package com.tdc.nhom6.roomio.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.FacilitiesAdapter
import com.tdc.nhom6.roomio.apis.CloudinaryRepository
import com.tdc.nhom6.roomio.databinding.HotelFacilitiesLayoutBinding
import com.tdc.nhom6.roomio.models.FacilitiesModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class HotelFacilitiesActivity : AppCompatActivity() {
    private lateinit var binding: HotelFacilitiesLayoutBinding

    private lateinit var adapter: FacilitiesAdapter
    private val facilitiesList = mutableListOf<FacilitiesModel>()
    private var selectedIndex: Int? = null
    private val db = FirebaseFirestore.getInstance()
    private var facilitiesListener: ListenerRegistration? = null
    private var fileAnhFacilities: File? = null

    private lateinit var cloudinaryRepo: CloudinaryRepository

    companion object {
        const val FOLDER_FACILITIES = "facilities"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HotelFacilitiesLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.app_bar_title_facilities)

        cloudinaryRepo = CloudinaryRepository(this)

        setupRecyclerView()
        setEvent()
        loadFacilitiesData()
    }

    private fun setEvent() {
        binding.apply {
            btnChooseImage.setOnClickListener { openGallery() }
            btnAddFacility.setOnClickListener { confirmAddDialog() }
            btnUpdateFacility.setOnClickListener { confirmUpdateDialog() }
            btnDeleteFacility.setOnClickListener { confirmDeleteDialog() }
        }
    }

    private fun setupRecyclerView() {
        adapter = FacilitiesAdapter(facilitiesList) { facility, index ->
            selectedIndex = index
            binding.edtFacilityName.setText(facility.facilities_name)
            binding.edtFacilityDesc.setText(facility.description)
            Glide.with(this).load(facility.iconUrl).into(binding.imgPreview)
        }
        binding.rvListFacilities.layoutManager = LinearLayoutManager(this)
        binding.rvListFacilities.adapter = adapter
    }


    private fun loadFacilitiesData() {
        facilitiesListener = db.collection("facilities")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    Log.e("Firestore", "Lỗi đọc dữ liệu", error)
                    return@addSnapshotListener
                }
                val list = value?.toObjects(FacilitiesModel::class.java) ?: emptyList()
                facilitiesList.clear()
                facilitiesList.addAll(list)
                adapter.updateList(list)
            }
    }

    @SuppressLint("DefaultLocale")
    private fun addFacility(name: String, desc: String, imageUrl: String) {
        if (name.isEmpty()) {
            showToast("Vui lòng nhập tên tiện ích")
            return
        }

        val counterRef = db.collection("counters").document("facilitiesCounter")

        db.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            val current = snapshot.getLong("current") ?: 0
            val next = current + 1

            // Cập nhật counter trong Firestore
            transaction.update(counterRef, "current", next)

            // Sinh ID mới theo format
            val facilityId = String.format("facilities_%02d", next)

            // Tạo object tiện ích mới
            val facility = FacilitiesModel(
                id = facilityId,
                facilities_name = name,
                description = desc,
                iconUrl = imageUrl
            )

            // Ghi vào Firestore
            val docRef = db.collection("facilities").document(facilityId)
            transaction.set(docRef, facility)

            null
        }.addOnSuccessListener {
            showToast("Đã thêm tiện ích thành công!")
            clearForm()
        }.addOnFailureListener { e ->
            Log.w("Firestore", "Lỗi transaction", e)
            showToast("Lỗi khi thêm tiện ích!")
        }
    }

    private fun updateFacility(id: String, name: String, desc: String, imageUrl: String) {
        db.collection("facilities").document(id)
            .update(
                "facilities_name", name,
                "description", desc,
                "iconUrl", imageUrl
            )
            .addOnSuccessListener {
                showToast("Đã cập nhật thành công!")
                clearForm()
            }
            .addOnFailureListener {
                showToast("Lỗi khi cập nhật!")
            }
    }

    private fun deleteFacility(id: String) {
        db.collection("facilities").document(id)
            .delete()
            .addOnSuccessListener {
                showToast("Đã xóa tiện ích thành công!")
                clearForm()
            }
            .addOnFailureListener {
                showToast("Lỗi khi xóa!")
            }
    }

    private fun confirmAddDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận thêm")
            .setMessage("Bạn có chắc muốn thêm tiện ích này không?")
            .setPositiveButton("Thêm") { _, _ -> uploadAndAddFacility() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmUpdateDialog() {
        if (selectedIndex == null) {
            showToast("Vui lòng chọn tiện ích để cập nhật!")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Xác nhận cập nhật")
            .setMessage("Bạn có chắc muốn cập nhật tiện ích này không?")
            .setPositiveButton("Cập nhật") { _, _ -> uploadAndUpdateFacility() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmDeleteDialog() {
        if (selectedIndex == null) {
            showToast("Vui lòng chọn tiện ích để xóa!")
            return
        }
        val facility = facilitiesList[selectedIndex!!]
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc muốn xóa '${facility.facilities_name}' không?")
            .setPositiveButton("Xóa") { _, _ -> facility.id?.let { deleteFacility(it) } }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun uploadAndAddFacility() {
        val name = binding.edtFacilityName.text.toString().trim()
        val desc = binding.edtFacilityDesc.text.toString().trim()
        val file = fileAnhFacilities

        if (name.isEmpty()) {
            showToast("Vui lòng nhập tên tiện ích")
            return
        }
        if (file == null) {
            showToast("Vui lòng chọn ảnh")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uploadResult = cloudinaryRepo.uploadSingleImage(file, FOLDER_FACILITIES)
                val imageUrl = uploadResult?.secure_url ?: ""

                withContext(Dispatchers.Main) {
                    if (imageUrl.isNotEmpty()) {
                        addFacility(name, desc, imageUrl)
                    } else {
                        showToast("Không lấy được link ảnh!")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Lỗi khi upload ảnh: ${e.message}")
                }
            }
        }
    }

    private fun uploadAndUpdateFacility() {
        val name = binding.edtFacilityName.text.toString().trim()
        val desc = binding.edtFacilityDesc.text.toString().trim()
        val facility = facilitiesList[selectedIndex!!]

        if (name.isEmpty()) {
            showToast("Vui lòng nhập tên tiện ích")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imageUrl = if (fileAnhFacilities != null) {
                    cloudinaryRepo.uploadSingleImage(fileAnhFacilities!!, FOLDER_FACILITIES)?.secure_url
                } else facility.iconUrl

                withContext(Dispatchers.Main) {
                    facility.id?.let { updateFacility(it, name, desc, imageUrl ?: "") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Lỗi khi cập nhật: ${e.message}")
                }
            }
        }
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageUri: Uri? = result.data?.data
                if (imageUri != null) {
                    Glide.with(this).load(imageUri).into(binding.imgPreview)
                    fileAnhFacilities = uriToFile(imageUri)
                }
            }
        }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        pickImageLauncher.launch(intent)
    }

    private fun uriToFile(uri: Uri): File {
        val documentFile = DocumentFile.fromSingleUri(this, uri)
        val fileName = documentFile?.name ?: "temp_${System.currentTimeMillis()}.jpg"
        val tempFile = File(cacheDir, fileName)

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        return tempFile
    }

    private fun clearForm() {
        binding.edtFacilityName.text?.clear()
        binding.edtFacilityDesc.text?.clear()
        binding.imgPreview.setImageResource(R.drawable.ic_not_image)
        fileAnhFacilities = null
        selectedIndex = null
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        facilitiesListener?.remove()
    }
}
