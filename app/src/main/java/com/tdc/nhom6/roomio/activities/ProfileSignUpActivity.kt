package com.tdc.nhom6.roomio.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.tdc.nhom6.roomio.databinding.ProfileSignUpLayoutBinding
import java.text.SimpleDateFormat
import java.util.*

class ProfileSignUpActivity : AppCompatActivity() {

    private lateinit var binding: ProfileSignUpLayoutBinding
    private val auth = FirebaseAuth.getInstance()
    private var selectedBirthDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProfileSignUpLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Quay lại Login
        binding.btnBack.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Chọn ngày sinh
        binding.edtBirthDate.setOnClickListener { showDatePicker() }

        // Đăng ký
        binding.btnSignUp.setOnClickListener { validateAndContinue() }
    }

    // Kiểm tra và xử lý đăng ký
    private fun validateAndContinue() {
        val username = binding.edtUsername.text.toString().trim()
        val email = binding.edtEmail.text.toString().trim()
        val phone = binding.edtPhone.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()
        val confirm = binding.edtConfirmPassword.text.toString().trim()
        val gender = when {
            binding.radioMale.isChecked -> "Nam"
            binding.radioFemale.isChecked -> "Nữ"
            else -> ""
        }

//  Kiểm tra hợp lệ cơ bản
        when {
            username.isEmpty() -> {
                binding.edtUsername.error = "Vui lòng nhập tên"
                return
            }
            email.isEmpty() -> {
                binding.edtEmail.error = "Vui lòng nhập email"
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.edtEmail.error = "Email không hợp lệ"
                return
            }
            phone.isEmpty() -> {
                binding.edtPhone.error = "Vui lòng nhập số điện thoại"
                return
            }
            gender.isEmpty() -> {
                toast("Vui lòng chọn giới tính")
                return
            }
            selectedBirthDate.isEmpty() || !isBirthDateValid(selectedBirthDate) -> {
                binding.edtBirthDate.error = "Ngày sinh không hợp lệ"
                return
            }
            password.length < 6 -> {
                binding.edtPassword.error = "Mật khẩu ít nhất 6 ký tự"
                return
            }
            password != confirm -> {
                binding.edtConfirmPassword.error = "Mật khẩu không khớp"
                return
            }
        }


        //  Kiểm tra email có tồn tại chưa
        auth.fetchSignInMethodsForEmail(email)
            .addOnSuccessListener { result ->
                if (result.signInMethods.isNullOrEmpty()) {
                    goToEmailVerify(username, email, phone, gender, password)
                } else {
                    binding.edtEmail.error = "Email này đã được đăng ký"
                }
            }
            .addOnFailureListener { e ->
                toast("Lỗi kiểm tra email: ${e.message}")
            }
    }

    // Chuyển qua màn EmailVerifyActivity
    private fun goToEmailVerify(
        username: String,
        email: String,
        phone: String,
        gender: String,
        password: String
    ) {
        val createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))

        val intent = Intent(this, EmailVerifyActivity::class.java).apply {
            putExtra("username", username)
            putExtra("email", email)
            putExtra("phone", phone)
            putExtra("gender", gender)
            putExtra("birthDate", selectedBirthDate)
            putExtra("password", password)
            putExtra("roleId", "user")
            putExtra("createdAt", createdAt)
            putExtra("balance", 0.0) //  Thêm số dư mặc định
        }
        startActivity(intent)
    }

    //Chọn ngày sinh
    private fun showDatePicker() {
        val c = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            this,
            { _, y, m, d ->
                selectedBirthDate = "%02d/%02d/%d".format(d, m + 1, y)
                binding.edtBirthDate.setText(selectedBirthDate)
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.datePicker.maxDate = System.currentTimeMillis()
        datePicker.show()
    }

    // Kiểm tra hợp lệ ngày sinh
    private fun isBirthDateValid(birthDate: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dob = sdf.parse(birthDate) ?: return false
            dob.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
