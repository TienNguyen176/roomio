package com.tdc.nhom6.roomio.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.text.*

class BusinessRegistrationActivity : AppCompatActivity() {
    private lateinit var binding: BusinessRegistrationLayoutBinding
    private lateinit var cloudinaryRepo: CloudinaryRepository
    private var hotelTypes: MutableList<HotelTypeModel> = mutableListOf()
    private var selectedHotelTypeId: String? = null
    private var selectedType = ""

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
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.app_bar_title_dang_ky_kinh_doanh)

        loadHotelTypes()
        setEvent()
    }

    private fun setEvent() {
        binding.apply {
            imgMatTruoc.setOnClickListener {
                selectedType = CCCD_MAT_TRUOC
                openGallery()
            }
            imgMatSau.setOnClickListener {
                selectedType = CCCD_MAT_SAU
                openGallery()
            }
            binding.edtNamSinh.setOnClickListener {
                showDatePickerDialog()
            }
            binding.cbNam.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    binding.cbNu.isChecked = false
                }
            }
            binding.cbNu.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    binding.cbNam.isChecked = false
                }
            }
            tvUploadGiayPhepKinhDoanh.setOnClickListener {
                selectedType = GIAY_PHEP_KINH_DOANH
                openGallery()
            }
            tvUploadGiayChungNhanPCCC.setOnClickListener {
                selectedType = GIAY_PHEP_PCCC
                openGallery()
            }
            tvUploadGiayChungNhanANTT.setOnClickListener {
                selectedType = GIAY_PHEP_ANTT
                openGallery()
            }
            tvUploadGiayChungNhanVSATTP.setOnClickListener {
                selectedType = GIAY_PHEP_VSATTP
                openGallery()
            }
            btnNopDon.setOnClickListener {
                sendData()
            }
            btnHuy.setOnClickListener {
                finish()
            }
        }
    }

    private fun uriToFile(uri: Uri): File {
        val documentFile = DocumentFile.fromSingleUri(this, uri)
        val fileName = documentFile?.name ?: "temp_${System.currentTimeMillis()}.jpg"
        val tempFile = File(cacheDir, fileName)

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageUri: Uri? = result.data?.data
                imageUri?.let {
                    when (selectedType) {
                        CCCD_MAT_TRUOC -> {
                            Glide.with(this).load(it).into(binding.imgMatTruoc)
                            fileCCCDMatTruoc = uriToFile(it)
                        }

                        CCCD_MAT_SAU -> {
                            Glide.with(this).load(it).into(binding.imgMatSau)
                            fileCCCDMatSau = uriToFile(it)
                        }

                        GIAY_PHEP_KINH_DOANH -> {
                            fileGiayPhepKinhDoanh = uriToFile(it)
                        }

                        GIAY_PHEP_PCCC -> {
                            fileGiayPhepPCCC = uriToFile(it)
                        }

                        GIAY_PHEP_ANTT -> {
                            fileGiayPhepANTT = uriToFile(it)
                        }

                        GIAY_PHEP_VSATTP -> {
                            fileGiayPhepVSATTP = uriToFile(it)
                        }
                    }
                }
            }
        }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        pickImageLauncher.launch(intent)
    }

    @SuppressLint("DefaultLocale")
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = android.app.DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                // Format dạng dd/MM/yyyy
                val formattedDate = String.format("%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear)
                binding.edtNamSinh.setText(formattedDate)
            },
            year,
            month,
            day
        )

        // Giới hạn chọn không vượt quá ngày hiện tại
        datePicker.datePicker.maxDate = System.currentTimeMillis()
        datePicker.show()
    }

    private fun loadHotelTypes() {
        db.collection("hotelTypes")
            .get()
            .addOnSuccessListener { result ->
                hotelTypes.clear()
                for (doc in result) {
                    val id = doc.id
                    val name = doc.getString("type_name") ?: "Không tên"
                    hotelTypes.add(HotelTypeModel(id, name))
                }

                binding.tvLoaiHinhKhachSan.setOnClickListener {
                    showHotelTypeDialog()
                }

            }
            .addOnFailureListener {
                Toast.makeText(this, "Không thể tải danh sách loại khách sạn!", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun showHotelTypeDialog() {
        if (hotelTypes.isEmpty()) {
            Toast.makeText(this, "Chưa có dữ liệu loại hình khách sạn!", Toast.LENGTH_SHORT).show()
            return
        }

        // Sắp xếp theo tên A-Z
        val sortedList = hotelTypes.sortedBy { it.type_name.lowercase() }

        val adapter = HotelTypeDialogAdapter(this, sortedList)

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(HOTEL_SELECTED_TEXT)
            .setAdapter(adapter) { d, which ->
                val selected = sortedList[which]
                selectedHotelTypeId = selected.type_id
                binding.tvLoaiHinhKhachSan.text = selected.type_name
                d.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .create()

        dialog.show()
    }

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
                val dots = ".".repeat(dotCount % 4)
                textView.text = "Đang xử lý$dots"
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

                // ✅ Ẩn bàn phím nếu đang mở
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }

                // ✅ Xóa focus khỏi tất cả các trường nhập liệu
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                currentFocus?.clearFocus()
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

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

        val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        if (userName.isEmpty() || cccdNumber.isEmpty() || address.isEmpty() || phone.isEmpty() ||
            email.isEmpty() || hotelName.isEmpty() || hotelAddress.isEmpty()
        ) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
            return
        }

        val imageCCCDFiles = listOfNotNull(fileCCCDMatTruoc, fileCCCDMatSau)
        val licenseFiles = listOfNotNull(
            fileGiayPhepKinhDoanh,
            fileGiayPhepPCCC,
            fileGiayPhepANTT,
            fileGiayPhepVSATTP
        )

        cloudinaryRepo = CloudinaryRepository(this)

        lifecycleScope.launch {
            showLoadingDialog()
            try {
                // Upload CCCD
                val cccdUploads = async { cloudinaryRepo.uploadMultipleImages(imageCCCDFiles, FOLDER_CCCD) }
                // Upload License
                val licenseUploads = async { cloudinaryRepo.uploadMultipleImages(licenseFiles, FOLDER_LICENSE) }

                val cccdResults = cccdUploads.await()
                val licenseResults = licenseUploads.await()

                val cccdUrls = cccdResults.mapNotNull { it.secure_url }
                val licenseUrls = licenseResults.mapNotNull { it.secure_url }

                val hotelRequest = HotelRequestModel(
                    user_id = "currentUserId",
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

                delay(5000)

                hideLoadingDialog()

                saveHotelRequestToFirestore(hotelRequest)

            } catch (e: Exception) {
                Log.d("Cloundinary", "Loi upload: ${e}")
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun saveHotelRequestToFirestore(hotelRequest: HotelRequestModel) {
        val counterRef: DocumentReference =
            db.collection("counters").document("hotelRequestCounter")

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
            Toast.makeText(this, "Gửi yêu cầu thành công!", Toast.LENGTH_LONG).show()
            navigateTo(AdminHomeActivity::class.java, flag = false)
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