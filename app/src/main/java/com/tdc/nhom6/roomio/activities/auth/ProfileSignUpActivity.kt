package com.tdc.nhom6.roomio.activities.auth

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
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

        binding.btnBack.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.edtBirthDate.setOnClickListener { showDatePicker() }
        binding.btnSignUp.setOnClickListener { validateAndContinue() }
    }

    private fun validateAndContinue() {
        val username = binding.edtUsername.text.toString().trim()
        val email = binding.edtEmail.text.toString().trim()
        val phone = binding.edtPhone.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()
        val confirm = binding.edtConfirmPassword.text.toString().trim()

        val genderId = when {
            binding.radioMale.isChecked -> "male"
            binding.radioFemale.isChecked -> "female"
            else -> ""
        }

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
            genderId.isEmpty() -> {
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

        auth.fetchSignInMethodsForEmail(email)
            .addOnSuccessListener { result ->
                if (result.signInMethods.isNullOrEmpty()) {
                    goToEmailVerify(username, email, phone, genderId, password)
                } else {
                    binding.edtEmail.error = "Email này đã được đăng ký"
                }
            }
            .addOnFailureListener { e ->
                toast("Lỗi kiểm tra email: ${e.message}")
            }
    }


    private fun goToEmailVerify(
        username: String,
        email: String,
        phone: String,
        genderId: String,
        password: String
    ) {
        val createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))

        val intent = Intent(this, EmailVerifyActivity::class.java).apply {
            putExtra("username", username)
            putExtra("email", email)
            putExtra("phone", phone)
            putExtra("genderId", genderId)
            putExtra("birthDate", selectedBirthDate)
            putExtra("password", password)
            putExtra("roleId", "user")
            putExtra("createdAt", createdAt)
            putExtra("walletBalance", 0.0)
        }
        startActivity(intent)
    }

    private fun showDatePicker() {
        val c = Calendar.getInstance()
        val picker = DatePickerDialog(
            this,
            { _, y, m, d ->
                selectedBirthDate = "%02d/%02d/%d".format(d, m + 1, y)
                binding.edtBirthDate.setText(selectedBirthDate)
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        )
        picker.datePicker.maxDate = System.currentTimeMillis()
        picker.show()
    }

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
