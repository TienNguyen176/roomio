package com.tdc.nhom6.roomio.activities

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.EditProfileLayoutBinding
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

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            binding.imgAvatar.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EditProfileLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fromGoogle = intent.getBooleanExtra("fromGoogle", false)

        setupUI()
        loadUserInfo()

        if (fromGoogle) {
            // 🟠 Chỉ áp dụng khi người dùng đăng ký Google lần đầu
            binding.imgBack.visibility = View.GONE
            binding.tvEdit.text = "Lưu"
            enableEditing(true)
            setupAutoSaveBehavior()

            // 🔹 Tự động lấy email từ tài khoản Google
            binding.edtEmail.setText(auth.currentUser?.email ?: "")
            binding.edtEmail.isEnabled = false
        } else {
            // 🟢 Người dùng đăng nhập thường hoặc đã có tài khoản Google → giữ nguyên
            binding.imgBack.visibility = View.VISIBLE
            binding.tvEdit.text = "Sửa"
        }
    }

    private fun setupUI() = binding.apply {
        imgBack.setOnClickListener { if (isEditing) showConfirmExitDialog() else finish() }
        tvEdit.setOnClickListener { if (!isEditing) enableEditing(true) else saveUserInfo() }
        imgAvatar.setOnClickListener { if (isEditing) pickImageLauncher.launch("image/*") }
        edtBirthDate.setOnClickListener { if (isEditing) showDatePicker() }
    }

    private fun setupAutoSaveBehavior() {
        val editTexts = listOf(binding.edtUsername, binding.edtPhone, binding.edtBirthDate)
        editTexts.forEach {
            it.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    binding.tvEdit.text = "Lưu"
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }

    private fun loadUserInfo() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                binding.apply {
                    val emailFromFirestore = userDoc.getString("email")
                    val emailFromAuth = auth.currentUser?.email
                    val emailFromPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("email", "")

                    // 🔹 Ưu tiên lấy email từ Firestore → FirebaseAuth → SharedPreferences
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

                    Glide.with(this@EditProfileActivity)
                        .load(userDoc.getString("avatar"))
                        .placeholder(R.drawable.user)
                        .into(imgAvatar)
                }
                db.collection("accounts").document(uid).get()
                    .addOnSuccessListener { accDoc ->
                        if (accDoc.exists()) {
                            binding.edtPhone.setText(accDoc.getString("phone") ?: "")
                        }
                    }
            }
            .addOnFailureListener { showToast("❌ Lỗi tải dữ liệu: ${it.message}") }
    }



    private fun enableEditing(enable: Boolean) = binding.apply {
        isEditing = enable
        listOf(edtUsername, edtPhone, radioMale, radioFemale, edtBirthDate).forEach { it.isEnabled = enable }
        tvEdit.text = if (enable) "Lưu" else "Sửa"
    }

    private fun saveUserInfo() {
        val uid = auth.currentUser?.uid ?: return
        val username = binding.edtUsername.text.toString().trim()
        val phone = binding.edtPhone.text.toString().trim()
        val birthDate = binding.edtBirthDate.text.toString().trim()
        val gender = getSelectedGender()

        if (username.isEmpty() || phone.isEmpty()) {
            showToast("⚠️ Vui lòng nhập đầy đủ thông tin!")
            return
        }

        val createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            .format(Date())

        val updates = hashMapOf<String, Any>(
            "accountId" to uid,
            "avatar" to "",
            "birthDate" to birthDate,
            "createdAt" to createdAt,
            "email" to (auth.currentUser?.email ?: ""),
            "gender" to gender,
            "phone" to phone,
            "roleId" to currentRoleId,
            "username" to username
        )

        // 🔹 Nếu người dùng chọn ảnh → upload trước, rồi lưu Firestore
        if (imageUri != null) {
            uploadAvatar(uid, updates, phone)
        } else {
            updateUserAndPhone(uid, updates, phone)
        }
    }


    private fun uploadAvatar(uid: String, updates: HashMap<String, Any>, phone: String) {
        val imageUri = imageUri ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("avatars/$uid.jpg")

        // Hiển thị loading tạm thời (tuỳ bạn thêm ProgressBar hoặc disable nút)
        binding.tvEdit.isEnabled = false

        storageRef.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: Exception("Upload thất bại")
                }
                storageRef.downloadUrl
            }
            .addOnSuccessListener { uri ->
                val downloadUrl = uri.toString()
                updates["avatar"] = downloadUrl

                // 🔹 Lưu thông tin và đường dẫn ảnh vào Firestore
                updateUserAndPhone(uid, updates, phone)

                // 🔹 Cập nhật ngay ảnh trên giao diện
                Glide.with(this)
                    .load(downloadUrl)
                    .placeholder(R.drawable.user)
                    .into(binding.imgAvatar)
            }
            .addOnFailureListener { e ->
                Log.e("UploadError", "Firebase upload failed", e)
                showToast("❌ Lỗi tải ảnh: ${e.message}")
            }
            .addOnCompleteListener {
                binding.tvEdit.isEnabled = true
            }
    }



    private fun updateUserAndPhone(uid: String, updates: HashMap<String, Any>, phone: String) {
        val userRef = db.collection("users").document(uid)
        val accRef = db.collection("accounts").document(uid)

        db.runBatch { batch ->
            batch.set(userRef, updates, com.google.firebase.firestore.SetOptions.merge())
            batch.set(accRef, mapOf("phone" to phone), com.google.firebase.firestore.SetOptions.merge())
        }.addOnSuccessListener {
            enableEditing(false)
            setResult(Activity.RESULT_OK)
            showToast("✅ Đã lưu thay đổi!")

            // 🔹 Nếu là lần đầu đăng ký Google → chuyển sang ProfileActivity
            if (fromGoogle) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }.addOnFailureListener {
            showToast("❌ Lỗi lưu: ${it.message}")
        }
    }

    private fun getSelectedGender(): String {
        val checkedId = binding.radioGender.checkedRadioButtonId
        return if (checkedId != -1)
            findViewById<RadioButton>(checkedId).text.toString()
        else ""
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

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
