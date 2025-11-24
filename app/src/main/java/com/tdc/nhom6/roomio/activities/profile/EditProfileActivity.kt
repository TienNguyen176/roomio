package com.tdc.nhom6.roomio.activities.profile

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.EditProfileLayoutBinding
import com.tdc.nhom6.roomio.fragments.ProfileFragment
import com.tdc.nhom6.roomio.repositories.CloudinaryRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: EditProfileLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var imageUri: Uri? = null
    private var isEditing = false
    private var currentRoleId = "user"
    private var fromGoogle = false
    private var currentAvatarUrl = ""  // ← Lưu avatar hiện tại
    private var cameraImageUri: Uri? = null
    private lateinit var cloudinaryRepo: CloudinaryRepository

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            binding.imgAvatar.setImageURI(it)
        }
    }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                cameraImageUri?.let {
                    imageUri = it
                    binding.imgAvatar.setImageURI(it)
                }
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EditProfileLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cloudinaryRepo = CloudinaryRepository(this)

        fromGoogle = intent.getBooleanExtra("fromGoogle", false)

        setupUI()
        loadUserInfo()

        if (fromGoogle) {
            binding.imgBack.visibility = View.GONE
            binding.tvEdit.text = "Lưu"
            enableEditing(true)
            setupAutoSaveBehavior()
            binding.edtEmail.setText(auth.currentUser?.email ?: "")
            binding.edtEmail.isEnabled = false
        } else {
            binding.imgBack.visibility = View.VISIBLE
            binding.tvEdit.text = "Sửa"
        }
    }

    // Thiết lập các hành vi UI
    private fun setupUI() = binding.apply {
        imgBack.setOnClickListener { if (isEditing) showConfirmExitDialog() else finish() }
        tvEdit.setOnClickListener { if (!isEditing) enableEditing(true) else saveUserInfo() }
        //imgAvatar.setOnClickListener { if (isEditing) pickImageLauncher.launch("image/*") }
        imgAvatar.setOnClickListener { if (isEditing) openImagePickerDialog() }

        edtBirthDate.setOnClickListener { if (isEditing) showDatePicker() }
    }

    // Auto đổi nút "Sửa" thành "Lưu" khi edit
    private fun setupAutoSaveBehavior() {
        val editTexts = listOf(binding.edtUsername, binding.edtPhone, binding.edtBirthDate)
        editTexts.forEach {
            it.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { binding.tvEdit.text = "Lưu" }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }

    // Load thông tin user từ Firestore
    private fun loadUserInfo() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                binding.apply {
                    val emailFromFirestore = userDoc.getString("email")
                    val emailFromAuth = auth.currentUser?.email
                    val emailFromPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("email", "")
                    val finalEmail = when {
                        !emailFromFirestore.isNullOrEmpty() -> emailFromFirestore
                        !emailFromAuth.isNullOrEmpty() -> emailFromAuth
                        !emailFromPrefs.isNullOrEmpty() -> emailFromPrefs
                        else -> ""
                    }

                    edtUsername.setText(userDoc.getString("username") ?: "")
                    edtEmail.setText(finalEmail)
                    edtBirthDate.setText(userDoc.getString("birthDate") ?: "")

                    val gender = userDoc.getString("gender") ?: ""
                    radioMale.isChecked = gender.equals("Nam", true)
                    radioFemale.isChecked = gender.equals("Nữ", true)

                    currentRoleId = userDoc.getString("roleId") ?: "user"
                    currentAvatarUrl = userDoc.getString("avatar") ?: ""

                    Glide.with(this@EditProfileActivity)
                        .load(currentAvatarUrl)
                        .placeholder(R.drawable.user)
                        .into(imgAvatar)
                }

                // Load phone từ collection accounts
                db.collection("accounts").document(uid).get()
                    .addOnSuccessListener { accDoc ->
                        if (accDoc.exists()) {
                            binding.edtPhone.setText(accDoc.getString("phone") ?: "")
                        }
                    }
            }
            .addOnFailureListener { showToast("Lỗi tải dữ liệu: ${it.message}") }
    }

    // Bật/tắt chế độ edit
    private fun enableEditing(enable: Boolean) = binding.apply {
        isEditing = enable
        listOf(edtUsername, edtPhone, radioMale, radioFemale, edtBirthDate).forEach { it.isEnabled = enable }
        tvEdit.text = if (enable) "Lưu" else "Sửa"
    }

    // Lưu dữ liệu user
    private fun saveUserInfo() {
        val uid = auth.currentUser?.uid ?: return
        val username = binding.edtUsername.text.toString().trim()
        val phone = binding.edtPhone.text.toString().trim()
        val birthDate = binding.edtBirthDate.text.toString().trim()
        val gender = getSelectedGender()

        if (username.isEmpty() || phone.isEmpty()) {
            showToast("Vui lòng nhập đầy đủ thông tin!")
            return
        }

        val createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        // Chuẩn bị map dữ liệu cần update
        val updates = hashMapOf<String, Any>(
            "accountId" to uid,
            "username" to username,
            "birthDate" to birthDate,
            "createdAt" to createdAt,
            "email" to (auth.currentUser?.email ?: ""),
            "gender" to gender,
            "phone" to phone,
            "roleId" to currentRoleId
        )

        // Chỉ thêm avatar nếu user chọn ảnh mới
        if (imageUri != null) updates["avatar"] = ""  // sẽ update khi upload xong

        if (imageUri != null) {
            uploadAvatar(uid, updates, phone)
        } else {
            // Giữ avatar hiện tại nếu không thay đổi
            updates["avatar"] = currentAvatarUrl
            updateUserAndPhone(uid, updates, phone)
        }
    }

    private fun openImagePickerDialog() {
        val options = arrayOf("Chụp ảnh", "Chọn từ thư viện")
        AlertDialog.Builder(this)
            .setTitle("Chọn ảnh đại diện")
            .setItems(options) { _, which ->
                if (which == 0) openCamera() else openGallery()
            }
            .show()
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun openCamera() {
        val fileName = "avatar_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, fileName)
        if (!file.exists()) file.createNewFile()

        cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        takePhotoLauncher.launch(cameraImageUri!!)
    }
    // Upload avatar lên Cloudinary
    private fun uploadAvatar(uid: String, updates: HashMap<String, Any>, phone: String) {
        val uri = imageUri ?: return

        binding.tvEdit.isEnabled = false

        lifecycleScope.launch {
            try {
                val file = uriToFile(uri)   // ← FIX QUAN TRỌNG

                val result = cloudinaryRepo.uploadSingleImage(file, "users")
                val imageUrl = result?.secure_url ?: ""

                if (imageUrl.isNotEmpty()) {
                    updates["avatar"] = imageUrl
                    currentAvatarUrl = imageUrl

                    Glide.with(this@EditProfileActivity)
                        .load(imageUrl)
                        .placeholder(R.drawable.user)
                        .into(binding.imgAvatar)
                }

                updateUserAndPhone(uid, updates, phone)

            } catch (e: Exception) {
                showToast("Lỗi upload ảnh: ${e.message}")
            } finally {
                binding.tvEdit.isEnabled = true
            }
        }
    }



    // Lấy đường dẫn thực tế từ URI
    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri) ?: return File("")
        val tempFile = File(cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
        val outputStream = tempFile.outputStream()

        inputStream.copyTo(outputStream)

        inputStream.close()
        outputStream.close()

        return tempFile
    }

    // Cập nhật user + phone, đồng thời trả dữ liệu avatar + username để ProfileActivity hiển thị ngay
    private fun updateUserAndPhone(uid: String, updates: HashMap<String, Any>, phone: String) {
        val userRef = db.collection("users").document(uid)
        val accRef = db.collection("accounts").document(uid)

        db.runBatch { batch ->
            batch.set(userRef, updates, com.google.firebase.firestore.SetOptions.merge())
            batch.set(accRef, mapOf("phone" to phone), com.google.firebase.firestore.SetOptions.merge())
        }.addOnSuccessListener {
            enableEditing(false)

            // Trả dữ liệu avatar + username về ProfileActivity
            val resultIntent = Intent().apply {
                putExtra("username", updates["username"] as String)
                putExtra("avatar", updates["avatar"] as? String ?: "")
            }
            setResult(Activity.RESULT_OK, resultIntent)
            showToast("Đã lưu thay đổi!")

            if (fromGoogle) {
                val intent = Intent(this, ProfileFragment::class.java)
                startActivity(intent)
                finish()
            }
        }.addOnFailureListener {
            showToast("Lỗi lưu: ${it.message}")
        }
    }

    private fun getSelectedGender(): String {
        val checkedId = binding.radioGender.checkedRadioButtonId
        return if (checkedId != -1) findViewById<RadioButton>(checkedId).text.toString() else ""
    }

    private fun showConfirmExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận thoát")
            .setMessage("Bạn có muốn lưu thay đổi không?")
            .setPositiveButton("Lưu") { _, _ -> saveUserInfo() }
            .setNegativeButton("Không") { _, _ -> finish() }
            .show()
    }

    private fun showDatePicker() {
        val c = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d -> binding.edtBirthDate.setText("$d/${m + 1}/$y") },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}