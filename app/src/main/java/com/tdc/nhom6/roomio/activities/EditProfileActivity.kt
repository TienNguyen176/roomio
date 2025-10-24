package com.tdc.nhom6.roomio.activities

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
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
import java.util.*

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: EditProfileLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isEditing = false
    private var imageUri: Uri? = null
    private var currentRoleId = "user"

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

        loadUserInfo()
        setupUI()
    }

    /** ------------------ UI & Sự kiện ------------------ **/
    private fun setupUI() = binding.apply {
        // 🔹 Nút quay lại
        imgBack.setOnClickListener {
            if (isEditing) showConfirmExitDialog() else finish()
        }

        // 🔹 Nút Sửa / Lưu
        tvEdit.setOnClickListener {
            if (!isEditing) enableEditing(true) else saveUserInfo()
        }

        // 🔹 Chọn ảnh
        imgAvatar.setOnClickListener { if (isEditing) pickImageLauncher.launch("image/*") }

        // 🔹 Chọn ngày sinh
        edtBirthDate.setOnClickListener { if (isEditing) showDatePicker() }
    }


    /** ------------------ Load dữ liệu ------------------ **/
    private fun loadUserInfo() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (!userDoc.exists()) return@addOnSuccessListener

                binding.apply {
                    edtUsername.setText(userDoc.getString("username") ?: "")
                    edtEmail.setText(userDoc.getString("email") ?: "")
                    edtBirthDate.setText(userDoc.getString("birthDate") ?: "")

                    val gender = userDoc.getString("gender") ?: ""
                    radioMale.isChecked = gender.equals("Nam", ignoreCase = true)
                    radioFemale.isChecked = gender.equals("Nữ", ignoreCase = true)

                    currentRoleId = userDoc.getString("roleId") ?: "user"

                    Glide.with(this@EditProfileActivity)
                        .load(userDoc.getString("avatar"))
                        .placeholder(R.drawable.user)
                        .into(imgAvatar)
                }

                // 🔹 Lấy số điện thoại
                db.collection("accounts").document(uid).get()
                    .addOnSuccessListener { accDoc ->
                        if (accDoc.exists()) {
                            binding.edtPhone.setText(accDoc.getString("phone") ?: "")
                        }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "❌ Lỗi tải dữ liệu: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }


    /** ------------------ Chế độ chỉnh sửa ------------------ **/
    private fun enableEditing(enable: Boolean) {
        isEditing = enable
        binding.apply {
            edtUsername.isEnabled = enable
            edtPhone.isEnabled = enable
            radioMale.isEnabled = enable
            radioFemale.isEnabled = enable
            edtBirthDate.isEnabled = enable
            tvEdit.text = if (enable) "Lưu" else "Sửa"

        }
    }

    /** ------------------ Lưu dữ liệu ------------------ **/
    private fun saveUserInfo() {
        val uid = auth.currentUser?.uid ?: return
        val username = binding.edtUsername.text.toString().trim()
        val phone = binding.edtPhone.text.toString().trim()
        val birthDate = binding.edtBirthDate.text.toString().trim()

        // ✅ Đọc chính xác giới tính đang chọn
        val checkedGenderId = binding.radioGender.checkedRadioButtonId
        val gender = if (checkedGenderId != -1) {
            findViewById<RadioButton>(checkedGenderId).text.toString()
        } else ""

        if (username.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "⚠️ Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
            return
        }

        val userUpdates = hashMapOf<String, Any>(
            "username" to username,
            "gender" to gender,
            "birthDate" to birthDate,
            "roleId" to currentRoleId
        )

        if (imageUri != null) {
            val storageRef = FirebaseStorage.getInstance().reference.child("avatars/$uid.jpg")
            storageRef.putFile(imageUri!!)
                .continueWithTask { task ->
                    if (!task.isSuccessful) throw task.exception ?: Exception("Upload thất bại")
                    storageRef.downloadUrl
                }
                .addOnSuccessListener { uri ->
                    userUpdates["avatar"] = uri.toString()
                    updateUserAndPhone(uid, userUpdates, phone)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "❌ Upload ảnh lỗi: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else updateUserAndPhone(uid, userUpdates, phone)
    }

    private fun updateUserAndPhone(uid: String, userUpdates: HashMap<String, Any>, phone: String) {
        val userRef = db.collection("users").document(uid)
        val accRef = db.collection("accounts").document(uid)

        db.runBatch { batch ->
            batch.update(userRef, userUpdates)
            batch.update(accRef, mapOf("phone" to phone))
        }.addOnSuccessListener {
            enableEditing(false)
            setResult(Activity.RESULT_OK)
            Toast.makeText(this, "✅ Đã lưu thay đổi!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "❌ Lỗi lưu: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** ------------------ Hộp thoại xác nhận ------------------ **/
    private fun showConfirmExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận thoát")
            .setMessage("Bạn có muốn lưu thay đổi không?")
            .setPositiveButton("Lưu") { _, _ -> saveUserInfo() }
            .setNegativeButton("Không") { _, _ -> finish() }
            .show()
    }

    /** ------------------ Date Picker ------------------ **/
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
}
