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

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            imageUri = uri
            binding.imgAvatar.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EditProfileLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadUserInfo()
        setupUI()
    }

    private fun setupUI() {
        // 🔹 Chọn ảnh đại diện
        binding.imgAvatar.setOnClickListener {
            if (isEditing) pickImageLauncher.launch("image/*")
        }

        // 🔹 Nút Sửa / Lưu
        binding.title.findViewById<android.widget.TextView>(R.id.tvEdit)?.setOnClickListener {
            if (!isEditing) enableEditing(true)
            else saveUserInfo()
        }

        // 🔹 Nút quay lại
        binding.title.findViewById<android.widget.ImageView>(R.id.imgBack)?.setOnClickListener {
            if (isEditing) showConfirmExitDialog()
            else finish()
        }

        // 🔹 Chọn ngày sinh
        binding.edtBirthDate.setOnClickListener {
            if (isEditing) showDatePicker()
        }
    }

    private fun loadUserInfo() {
        val uid = auth.currentUser?.uid ?: return

        // 🔹 Lấy thông tin từ "users"
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    binding.edtUsername.setText(userDoc.getString("username") ?: "")
                    binding.edtEmail.setText(userDoc.getString("email") ?: "")
                    binding.edtBirthDate.setText(userDoc.getString("birthDate") ?: "")

                    val gender = userDoc.getString("gender")
                    if (gender == "Nam") binding.radioMale.isChecked = true
                    else if (gender == "Nữ") binding.radioFemale.isChecked = true

                    val avatarUrl = userDoc.getString("avatar")
                    Glide.with(this)
                        .load(avatarUrl)
                        .placeholder(R.drawable.user)
                        .into(binding.imgAvatar)
                }

                // 🔹 Lấy thêm số điện thoại từ "accounts"
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

    private fun enableEditing(enable: Boolean) {
        isEditing = enable

        binding.edtUsername.isEnabled = enable
        binding.edtPhone.isEnabled = enable
        binding.radioMale.isEnabled = enable
        binding.radioFemale.isEnabled = enable
        binding.edtBirthDate.isEnabled = enable

        val txtEdit = binding.title.findViewById<android.widget.TextView>(R.id.tvEdit)
        txtEdit?.text = if (enable) "Lưu" else "Sửa"

        Toast.makeText(
            this,
            if (enable) "🔓 Chế độ chỉnh sửa bật" else "✅ Đã lưu thay đổi",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun saveUserInfo() {
        val uid = auth.currentUser?.uid ?: return
        val username = binding.edtUsername.text.toString().trim()
        val phone = binding.edtPhone.text.toString().trim()
        val gender = findViewById<RadioButton>(binding.radioGender.checkedRadioButtonId)?.text?.toString()
        val birthDate = binding.edtBirthDate.text.toString().trim()

        if (username.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "⚠️ Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
            return
        }

        val userUpdates = hashMapOf<String, Any>(
            "username" to username,
            "gender" to (gender ?: ""),
            "birthDate" to birthDate
        )

        // 🔹 Nếu chọn ảnh mới → upload lên Firebase Storage
        if (imageUri != null) {
            val storageRef = FirebaseStorage.getInstance().reference.child("avatars/$uid.jpg")
            storageRef.putFile(imageUri!!)
                .continueWithTask { storageRef.downloadUrl }
                .addOnSuccessListener { uri ->
                    userUpdates["avatar"] = uri.toString()
                    updateUserAndPhone(uid, userUpdates, phone)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "❌ Lỗi upload ảnh: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            updateUserAndPhone(uid, userUpdates, phone)
        }
    }

    private fun updateUserAndPhone(uid: String, userUpdates: HashMap<String, Any>, phone: String) {
        val userRef = db.collection("users").document(uid)
        val accountRef = db.collection("accounts").document(uid)

        // 🔹 Cập nhật users và chỉ cập nhật "phone" của accounts
        db.runBatch { batch ->
            batch.update(userRef, userUpdates)
            batch.update(accountRef, mapOf("phone" to phone))
        }.addOnSuccessListener {
            enableEditing(false)
            setResult(Activity.RESULT_OK)
            Toast.makeText(this, "✅ Cập nhật thành công!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "❌ Lỗi lưu: ${it.message}", Toast.LENGTH_SHORT).show()
        }
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
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, y, m, d ->
            binding.edtBirthDate.setText("$d/${m + 1}/$y")
        }, year, month, day).show()
    }
}
