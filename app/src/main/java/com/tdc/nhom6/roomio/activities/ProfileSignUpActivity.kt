package com.tdc.nhom6.roomio.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.tdc.nhom6.roomio.databinding.ProfileSignUpLayoutBinding
import java.util.Calendar

class ProfileSignUpActivity : AppCompatActivity() {

    private lateinit var binding: ProfileSignUpLayoutBinding
    private val auth = FirebaseAuth.getInstance()
    private var selectedBirthDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProfileSignUpLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔸 Nút quay lại Login
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        // 🔸 Chọn ngày sinh
        binding.edtBirthDate.setOnClickListener { showDatePicker() }
        binding.imgCalendar.setOnClickListener { showDatePicker() }

        // 🔸 Xử lý đăng ký
        binding.btnSignUp.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()

            // ⚡ 1. Kiểm tra email trước tiên
            if (email.isEmpty()) {
                binding.edtEmail.error = "Vui lòng nhập email"
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.edtEmail.error = "Email không hợp lệ"
                return@setOnClickListener
            }

            // ✅ 2. Kiểm tra email có tồn tại chưa (Firebase Auth)
            auth.fetchSignInMethodsForEmail(email)
                .addOnSuccessListener { result ->
                    val signInMethods = result.signInMethods
                    if (signInMethods != null && signInMethods.isNotEmpty()) {
                        // ❌ Email đã tồn tại → hiển thị lỗi ngay tại màn này, không chuyển trang
                        binding.edtEmail.error = "Email này đã được đăng ký"
                    } else {
                        // ✅ Email hợp lệ và chưa đăng ký → kiểm tra các trường còn lại
                        checkOtherFieldsAndNext(email)
                    }
                }
                .addOnFailureListener { e ->
                    binding.edtEmail.error = "Không thể kiểm tra email: ${e.message}"
                }
        }
    }

    /**
     * 📌 Kiểm tra các trường còn lại khi email đã hợp lệ và chưa trùng
     */
    private fun checkOtherFieldsAndNext(email: String) {
        var current_id = ""
        val username = binding.edtUsername.text.toString().trim()
        val phone = binding.edtPhone.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()
        val confirm = binding.edtConfirmPassword.text.toString().trim()
        val gender = when {
            binding.radioMale.isChecked -> "Nam"
            binding.radioFemale.isChecked -> "Nữ"
            else -> ""
        }
        val role = "userRoles/user" // mặc định user

        if (username.isEmpty()) {
            binding.edtUsername.error = "Vui lòng nhập tên"
            return
        }
        if (phone.isEmpty()) {
            binding.edtPhone.error = "Vui lòng nhập số điện thoại"
            return
        }
        if (password.isEmpty()) {
            binding.edtPassword.error = "Vui lòng nhập mật khẩu"
            return
        }
        if (confirm.isEmpty()) {
            binding.edtConfirmPassword.error = "Vui lòng xác nhận mật khẩu"
            return
        }
        if (gender.isEmpty()) {
            binding.edtPhone.error = "Vui lòng chọn giới tính"
            return
        }
        if (selectedBirthDate.isEmpty() || !isBirthDateValid(selectedBirthDate)) {
            binding.edtBirthDate.error = "Ngày sinh không hợp lệ"
            return
        }
        if (password != confirm) {
            binding.edtConfirmPassword.error = "Mật khẩu nhập lại không khớp"
            return
        }

        // ✅ Chuyển sang màn xác minh email
        val intent = Intent(this, EmailVerifyActivity::class.java).apply {
            putExtra("current_id", current_id)
            putExtra("username", username)
            putExtra("email", email)
            putExtra("phone", phone)
            putExtra("gender", gender)
            putExtra("birthDate", selectedBirthDate)
            putExtra("password", password)
            putExtra("roleId", role)
        }
        startActivity(intent)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(
            this,
            { _, y, m, d ->
                val monthFormatted = String.format("%02d", m + 1)
                val dayFormatted = String.format("%02d", d)
                selectedBirthDate = "$dayFormatted/$monthFormatted/$y"
                binding.edtBirthDate.setText(selectedBirthDate)
                binding.edtBirthDate.error = null
            },
            year, month, day
        )

        datePicker.datePicker.maxDate = System.currentTimeMillis()
        datePicker.show()
    }

    private fun isBirthDateValid(birthDate: String): Boolean {
        val parts = birthDate.split("/")
        if (parts.size != 3) return false
        val day = parts[0].toInt()
        val month = parts[1].toInt() - 1
        val year = parts[2].toInt()

        val dob = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }
        val today = Calendar.getInstance()
        return !dob.after(today)
    }
}
