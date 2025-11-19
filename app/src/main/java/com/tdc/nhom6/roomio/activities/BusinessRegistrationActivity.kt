package com.tdc.nhom6.roomio.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.HotelTypeDialogAdapter
import com.tdc.nhom6.roomio.apis.CloudinaryRepository
import com.tdc.nhom6.roomio.databinding.BusinessRegistrationLayoutBinding
import com.tdc.nhom6.roomio.databinding.DialogLoadingBinding
import com.tdc.nhom6.roomio.models.HotelRequestModel
import com.tdc.nhom6.roomio.models.HotelTypeModel
import com.tdc.nhom6.roomio.utils.navigateTo
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

class BusinessRegistrationActivity : AppCompatActivity() {

    private lateinit var binding: BusinessRegistrationLayoutBinding
    private lateinit var cloudinaryRepo: CloudinaryRepository
    private var hotelTypes: MutableList<HotelTypeModel> = mutableListOf()
    private var selectedHotelTypeId: String? = null
    private var selectedType = ""

    val currentUser = FirebaseAuth.getInstance().currentUser
    var userId = ""

    private var cameraImageUri: Uri? = null

    private var fileCCCDMatTruoc: File? = null
    private var fileCCCDMatSau: File? = null

    private var fileGiayPhepKinhDoanh: File? = null
    private var fileGiayPhepPCCC: File? = null
    private var fileGiayPhepANTT: File? = null
    private var fileGiayPhepVSATTP: File? = null

    private val db = FirebaseFirestore.getInstance()
    private var loadingDialog: Dialog? = null
    private var loadingBinding: DialogLoadingBinding? = null
    private var isAnimatingText = false

    companion object {
        const val CCCD_MAT_TRUOC = "matTruoc"
        const val CCCD_MAT_SAU = "matSau"
        const val GIAY_PHEP_KINH_DOANH = "giayPhepKinhDoanh"
        const val GIAY_PHEP_PCCC = "giayPhepPCCC"
        const val GIAY_PHEP_ANTT = "giayPhepANTT"
        const val GIAY_PHEP_VSATTP = "giayPhepVSATTP"
        const val STATUS_ID = "hotel_request_pending"
        const val HOTEL_SELECTED_TEXT = "Chọn loại hình khách sạn"
        const val FOLDER_CCCD = "room_cccd"
        const val FOLDER_LICENSE = "room_license"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BusinessRegistrationLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.app_bar_title_dang_ky_kinh_doanh)

        if (currentUser != null) {
            userId = currentUser.uid
            println("User ID: $userId")
        } else {
            println("Chưa có người dùng đăng nhập")
        }

        loadHotelTypes()
        setEvent()
    }

    private fun setEvent() {
        binding.apply {
            imgMatTruoc.setOnClickListener {
                selectedType = CCCD_MAT_TRUOC
                openImagePickerDialog()
            }
            imgMatSau.setOnClickListener {
                selectedType = CCCD_MAT_SAU
                openImagePickerDialog()
            }
            edtNamSinh.setOnClickListener { showDatePickerDialog() }
            cbNam.setOnCheckedChangeListener { _, isChecked -> if (isChecked) cbNu.isChecked = false }
            cbNu.setOnCheckedChangeListener { _, isChecked -> if (isChecked) cbNam.isChecked = false }

            tvUploadGiayPhepKinhDoanh.setOnClickListener {
                selectedType = GIAY_PHEP_KINH_DOANH
                openImagePickerDialog()
            }
            tvUploadGiayChungNhanPCCC.setOnClickListener {
                selectedType = GIAY_PHEP_PCCC
                openImagePickerDialog()
            }
            tvUploadGiayChungNhanANTT.setOnClickListener {
                selectedType = GIAY_PHEP_ANTT
                openImagePickerDialog()
            }
            tvUploadGiayChungNhanVSATTP.setOnClickListener {
                selectedType = GIAY_PHEP_VSATTP
                openImagePickerDialog()
            }
            btnNopDon.setOnClickListener { sendData() }
            btnHuy.setOnClickListener { finish() }
        }
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

    // --- IMAGE PICKER AND CAMERA ---
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { handleSelectedImage(it) } }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) cameraImageUri?.let { handleSelectedImage(it) }
        }

    private fun openImagePickerDialog() {
        val options = arrayOf("Chụp ảnh", "Chọn từ thư viện")
        android.app.AlertDialog.Builder(this)
            .setTitle("Chọn hình ảnh")
            .setItems(options) { _, which ->
                if (which == 0) openCamera() else openGallery()
            }
            .show()
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun openCamera() {
        val fileName = "camera_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, fileName)
        if (!file.exists()) file.createNewFile()
        cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        takePhotoLauncher.launch(cameraImageUri!!)
    }

    private fun handleSelectedImage(uri: Uri) {
        when (selectedType) {
            CCCD_MAT_TRUOC -> {
                binding.imgMatTruoc.setImageURI(uri) // Hiển thị trực tiếp
                fileCCCDMatTruoc = uriToFile(uri)
            }
            CCCD_MAT_SAU -> {
                binding.imgMatSau.setImageURI(uri)
                fileCCCDMatSau = uriToFile(uri)
            }
            GIAY_PHEP_KINH_DOANH -> fileGiayPhepKinhDoanh = uriToFile(uri)
            GIAY_PHEP_PCCC -> fileGiayPhepPCCC = uriToFile(uri)
            GIAY_PHEP_ANTT -> fileGiayPhepANTT = uriToFile(uri)
            GIAY_PHEP_VSATTP -> fileGiayPhepVSATTP = uriToFile(uri)
        }
    }

    // --- DATE PICKER ---
    @SuppressLint("DefaultLocale")
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val datePicker = android.app.DatePickerDialog(
            this,
            { _, year, month, day ->
                binding.edtNamSinh.setText(String.format("%02d/%02d/%04d", day, month + 1, year))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.datePicker.maxDate = System.currentTimeMillis()
        datePicker.show()
    }

    // --- LOAD HOTEL TYPES ---
    private fun loadHotelTypes() {
        db.collection("hotelTypes").get()
            .addOnSuccessListener { result ->
                hotelTypes.clear()
                for (doc in result) {
                    hotelTypes.add(HotelTypeModel(doc.id, doc.getString("type_name") ?: "Không tên"))
                }
                binding.tvLoaiHinhKhachSan.setOnClickListener { showHotelTypeDialog() }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Không thể tải danh sách loại khách sạn!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showHotelTypeDialog() {
        if (hotelTypes.isEmpty()) return Toast.makeText(this, "Chưa có dữ liệu loại hình khách sạn!", Toast.LENGTH_SHORT).show()

        val sortedList = hotelTypes.sortedBy { it.type_name.lowercase() }
        val adapter = HotelTypeDialogAdapter(this, sortedList)

        android.app.AlertDialog.Builder(this)
            .setTitle(HOTEL_SELECTED_TEXT)
            .setAdapter(adapter) { dialog, which ->
                selectedHotelTypeId = sortedList[which].type_id
                binding.tvLoaiHinhKhachSan.text = sortedList[which].type_name
                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // --- LOADING DIALOG ---
    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            loadingBinding = DialogLoadingBinding.inflate(LayoutInflater.from(this))
            loadingDialog!!.setContentView(loadingBinding!!.root)
            loadingDialog!!.setCancelable(false)
        }
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        loadingBinding?.layoutLoading?.startAnimation(fadeIn)
        loadingDialog?.show()
        startLoadingAnimation()
    }

    private fun startLoadingAnimation() {
        val textView = loadingBinding?.tvDangXuLy ?: return
        isAnimatingText = true
        lifecycleScope.launch {
            var dotCount = 0
            while (isAnimatingText) {
                textView.text = "Đang xử lý" + ".".repeat(dotCount % 4)
                dotCount++
                delay(500)
            }
        }
    }

    private fun hideLoadingDialog() {
        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)
        val view = loadingBinding?.layoutLoading ?: return
        view.startAnimation(fadeOut)
        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                isAnimatingText = false
                loadingDialog?.dismiss()
            }
            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

    // --- SEND DATA ---
    private fun sendData() {
        val userName = binding.edtHoTen.text.toString().trim()
        val cccdNumber = binding.edtCCCD.text.toString().trim()
        val birthDate = binding.edtNamSinh.text.toString().trim()
        val address = binding.edtDiaChiThuongTru.text.toString().trim()
        val gender = when {
            binding.cbNam.isChecked -> "Nam"
            binding.cbNu.isChecked -> "Nữ"
            else -> ""
        }
        val phone = binding.edtSDT.text.toString().trim()
        val email = binding.edtEmail.text.toString().trim()
        val hotelName = binding.edtHotelName.text.toString().trim()
        val hotelAddress = binding.edtHotelAddress.text.toString().trim()
        val hotelFloors = binding.edtSoTang.text.toString().trim().toIntOrNull() ?: 0
        val hotelRoomFloors = binding.edtSoPhong.text.toString().toIntOrNull() ?: 0
        val hotelTotalRoom = hotelFloors * hotelRoomFloors

        if (userName.isEmpty() || cccdNumber.isEmpty() || address.isEmpty() || phone.isEmpty() ||
            email.isEmpty() || hotelName.isEmpty() || hotelAddress.isEmpty()
        ) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
            return
        }

        val imageCCCDFiles = listOfNotNull(fileCCCDMatTruoc, fileCCCDMatSau)
        val licenseFiles = listOfNotNull(fileGiayPhepKinhDoanh, fileGiayPhepPCCC, fileGiayPhepANTT, fileGiayPhepVSATTP)

        cloudinaryRepo = CloudinaryRepository(this)

        lifecycleScope.launch {
            showLoadingDialog()
            try {
                val cccdUploads = async { cloudinaryRepo.uploadMultipleImages(imageCCCDFiles, FOLDER_CCCD) }
                val licenseUploads = async { cloudinaryRepo.uploadMultipleImages(licenseFiles, FOLDER_LICENSE) }

                val cccdUrls = cccdUploads.await().mapNotNull { it.secure_url }
                val licenseUrls = licenseUploads.await().mapNotNull { it.secure_url }

                val hotelRequest = HotelRequestModel(
                    user_id = userId,
                    username = userName,
                    birth_date = birthDate,
                    cccd_number = cccdNumber,
                    cccd_image = cccdUrls,
                    phone = phone,
                    email = email,
                    address = address,
                    gender = gender,
                    hotel_name = hotelName,
                    hotel_address = hotelAddress,
                    hotel_floors = hotelFloors,
                    hotel_total_rooms = hotelTotalRoom,
                    hotel_type_id = selectedHotelTypeId!!,
                    license = licenseUrls,
                    status_id = STATUS_ID,
                    reason_rejected = "",
                    created_at = Timestamp.now(),
                    updated_at = null
                )

                delay(500) // Optional
                hideLoadingDialog()
                saveHotelRequestToFirestore(hotelRequest)

            } catch (e: Exception) {
                Log.e("Cloudinary", "Lỗi upload: $e")
                hideLoadingDialog()
            }
        }
    }

    private fun saveHotelRequestToFirestore(hotelRequest: HotelRequestModel) {
        val counterRef: DocumentReference = db.collection("counters").document("hotelRequestCounter")
        db.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            val current = snapshot.getLong("current") ?: 0
            val next = current + 1
            transaction.update(counterRef, "current", next)

            val nextId = String.format("request-%03d", next)
            val requestRef = db.collection("hotelRequests").document(nextId)
            transaction.set(requestRef, hotelRequest)
            null
        }.addOnSuccessListener {
            navigateTo(AdminHomeActivity::class.java, flag = false)
            Toast.makeText(this, "Gửi yêu cầu thành công!", Toast.LENGTH_LONG).show()
            finish()
        }.addOnFailureListener { e ->
            Log.w("Firestore", "Lỗi transaction", e)
            Toast.makeText(this, "Lỗi khi gửi yêu cầu!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
