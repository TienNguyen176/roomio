package com.tdc.nhom6.roomio.activities.profile

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.EditProfileLayoutBinding
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
    private var cameraImageUri: Uri? = null
    private var isEditing = false

    private var fromGoogle = false
    private var currentAvatarUrl: String = ""
    private var currentRoleId: String = "user"

    private lateinit var cloudinaryRepo: CloudinaryRepository

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                imageUri = it
                binding.imgAvatar.setImageURI(it)
            }
        }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && cameraImageUri != null) {
                imageUri = cameraImageUri
                binding.imgAvatar.setImageURI(cameraImageUri)
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
            setupAutoChangeText()
            binding.edtEmail.setText(auth.currentUser?.email ?: "")
            binding.edtEmail.isEnabled = false
        }
    }

    // -----------------------------
    // UI Flow
    // -----------------------------

    private fun setupUI() = binding.apply {

        imgBack.setOnClickListener {
            if (isEditing) showConfirmExitDialog() else finish()
        }

        tvEdit.setOnClickListener {
            if (!isEditing) enableEditing(true)
            else saveUserInfo()
        }

        imgAvatar.setOnClickListener {
            if (isEditing) openImagePickerDialog()
        }

        edtBirthDate.setOnClickListener {
            if (isEditing) showDatePicker()
        }
    }

    private fun enableEditing(enable: Boolean) {
        isEditing = enable
        binding.apply {
            listOf(edtUsername, edtPhone, edtBirthDate).forEach { it.isEnabled = enable }
            radioMale.isEnabled = enable
            radioFemale.isEnabled = enable
            tvEdit.text = if (enable) "Lưu" else "Sửa"
        }
    }

    private fun setupAutoChangeText() {
        val list = listOf(binding.edtUsername, binding.edtPhone, binding.edtBirthDate)
        list.forEach {
            it.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    binding.tvEdit.text = "Lưu"
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }

    // -----------------------------
    // Load data
    // -----------------------------

    private fun loadUserInfo() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                binding.apply {

                    // Email priority: Firestore → Auth → Prefs
                    val email = doc.getString("email")
                        ?: auth.currentUser?.email
                        ?: getSharedPreferences("user_prefs", MODE_PRIVATE)
                            .getString("email", "") ?: ""

                    edtUsername.setText(doc.getString("username") ?: "")
                    edtPhone.setText(doc.getString("phone") ?: "")
                    edtEmail.setText(email)
                    edtBirthDate.setText(doc.getString("birthDate") ?: "")

                    currentAvatarUrl = doc.getString("avatar") ?: ""
                    currentRoleId = doc.getString("roleId") ?: "user"

                    when (doc.getString("genderId")) {
                        "male" -> radioMale.isChecked = true
                        "female" -> radioFemale.isChecked = true
                    }

                    Glide.with(this@EditProfileActivity)
                        .load(currentAvatarUrl)
                        .placeholder(R.drawable.user)
                        .into(imgAvatar)
                }
            }
            .addOnFailureListener {
                showToast("Lỗi tải dữ liệu: ${it.message}")
            }
    }

    // -----------------------------
    // Save
    // -----------------------------

    private fun saveUserInfo() {
        val uid = auth.currentUser?.uid ?: return

        val username = binding.edtUsername.text.toString().trim()
        val phone = binding.edtPhone.text.toString().trim()

        if (username.isEmpty() || phone.isEmpty()) {
            showToast("Vui lòng nhập đầy đủ thông tin!")
            return
        }

        val updates = hashMapOf<String, Any>(
            "username" to username,
            "phone" to phone,
            "birthDate" to binding.edtBirthDate.text.toString(),
            "genderId" to getGenderId(),
            "email" to (auth.currentUser?.email ?: ""),
            "roleId" to currentRoleId,
            "createdAt" to SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                .format(Date())
        )

        // Nếu có ảnh mới → upload Cloudinary
        if (imageUri != null) {
            uploadAvatar(uid, updates)
        } else {
            updates["avatar"] = currentAvatarUrl
            updateFirestore(uid, updates)
        }
    }

    // -----------------------------
    // Upload Avatar
    // -----------------------------

    private fun uploadAvatar(uid: String, updates: HashMap<String, Any>) {
        val uri = imageUri ?: return

        binding.tvEdit.isEnabled = false

        lifecycleScope.launch {
            try {
                val file = uriToFile(uri)
                val result = cloudinaryRepo.uploadSingleImage(file, "users")

                val imageUrl = result?.secure_url ?: ""
                updates["avatar"] = imageUrl
                currentAvatarUrl = imageUrl

                updateFirestore(uid, updates)

            } catch (e: Exception) {
                showToast("Lỗi upload ảnh: ${e.message}")
            } finally {
                binding.tvEdit.isEnabled = true
            }
        }
    }

    private fun uriToFile(uri: Uri): File {
        val input = contentResolver.openInputStream(uri)!!
        val file = File(cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
        input.copyTo(file.outputStream())
        return file
    }

    // -----------------------------
    // Update to Firestore
    // -----------------------------

    private fun updateFirestore(uid: String, updates: HashMap<String, Any>) {
        db.collection("users").document(uid)
            .update(updates)
            .addOnSuccessListener {

                enableEditing(false)
                showToast("Đã lưu thay đổi!")

                // Gửi dữ liệu về ProfileFragment
                val result = Intent().apply {
                    putExtra("username", updates["username"] as String)
                    putExtra("avatar", updates["avatar"] as String)
                }
                setResult(Activity.RESULT_OK, result)

                if (fromGoogle) finish()
            }
            .addOnFailureListener {
                showToast("Lỗi lưu: ${it.message}")
            }
    }

    // -----------------------------
    // Gender
    // -----------------------------

    private fun getGenderId(): String {
        return when (binding.radioGender.checkedRadioButtonId) {
            binding.radioMale.id -> "male"
            binding.radioFemale.id -> "female"
            else -> ""
        }
    }

    // -----------------------------
    // Camera & Gallery
    // -----------------------------

    private fun openImagePickerDialog() {
        val options = arrayOf("Chụp ảnh", "Chọn từ thư viện")
        AlertDialog.Builder(this)
            .setTitle("Chọn ảnh đại diện")
            .setItems(options) { _, which ->
                if (which == 0) openCamera() else pickImageLauncher.launch("image/*")
            }
            .show()
    }

    private fun openCamera() {
        val file = File(cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        takePhotoLauncher.launch(cameraImageUri!!)
    }

    // -----------------------------

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
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
