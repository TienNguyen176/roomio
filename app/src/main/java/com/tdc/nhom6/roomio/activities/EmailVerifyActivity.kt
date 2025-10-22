package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.tdc.nhom6.roomio.databinding.VerifyEmailLayoutBinding
import at.favre.lib.crypto.bcrypt.BCrypt

class EmailVerifyActivity : AppCompatActivity() {

    private lateinit var binding: VerifyEmailLayoutBinding
    private val auth = Firebase.auth
    private val db = FirebaseFirestore.getInstance()

    private  var current_id = ""
    private var username = ""
    private var email = ""
    private var phone = ""
    private var gender = ""
    private var birthDate = ""
    private var password = ""
    private var roleId = "userRoles/user"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VerifyEmailLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 📥 Nhận dữ liệu từ ProfileSignUpActivity
        current_id = intent.getStringExtra("current_id") ?: ""
        username = intent.getStringExtra("username") ?: ""
        email = intent.getStringExtra("email") ?: ""
        phone = intent.getStringExtra("phone") ?: ""
        gender = intent.getStringExtra("gender") ?: ""
        birthDate = intent.getStringExtra("birthDate") ?: ""
        password = intent.getStringExtra("password") ?: ""
        roleId = intent.getStringExtra("roleId") ?: "userRoles/user"

        binding.edtEmailVerify.text = email

        // 📨 Gửi email xác minh ngay sau khi tạo tài khoản
        createAccountAndSendVerify()

        // 🔁 Gửi lại email xác minh nếu cần
        binding.btnResendEmail.setOnClickListener {
            auth.currentUser?.sendEmailVerification()
                ?.addOnSuccessListener {
                    Toast.makeText(this, "Đã gửi lại email xác minh 📩", Toast.LENGTH_SHORT).show()
                }
                ?.addOnFailureListener { e ->
                    Toast.makeText(this, "Lỗi gửi email: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // ✅ Khi nhấn “Xác minh xong”
        binding.btnVerifyDone.setOnClickListener {
            val user = auth.currentUser
            if (user != null) {
                binding.progressBar.visibility = View.VISIBLE
                user.reload().addOnSuccessListener {
                    if (user.isEmailVerified) {
                        saveAccountAndUser(user.uid)
                    } else {
                        Toast.makeText(this, "Email chưa được xác minh ❌", Toast.LENGTH_LONG).show()
                        binding.progressBar.visibility = View.GONE
                    }
                }.addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Lỗi xác minh: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Không tìm thấy người dùng hiện tại!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 📩 Tạo tài khoản Firebase Auth + gửi email xác minh
     */
    private fun createAccountAndSendVerify() {
        binding.progressBar.visibility = View.VISIBLE

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                auth.currentUser?.sendEmailVerification()
                    ?.addOnSuccessListener {
                        Toast.makeText(
                            this,
                            "Đã gửi email xác minh. Vui lòng kiểm tra hộp thư 📩",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    ?.addOnFailureListener { e ->
                        Toast.makeText(this, "Gửi email thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                val errorMessage = e.message ?: ""
                if (errorMessage.contains("The email address is already in use", ignoreCase = true)) {
                    Toast.makeText(this, "Email này đã được đăng ký ❌", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Lỗi tạo tài khoản: $errorMessage", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
    }

    /**
     * 💾 Lưu thông tin vào Firestore (accounts + users)
     */
    private fun saveAccountAndUser(uid: String) {
        // ✅ Hash password bằng bcrypt
        val hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray())

        // ⚡ Thống nhất key là "password" để LoginActivity đọc được
        val accountData = hashMapOf(
            "email" to email,
            "phone" to phone,
            "password" to hashedPassword
        )

        val userData = hashMapOf(
            "current_id" to current_id,
            "username" to username,
            "email" to email,
            "gender" to gender,
            "birthDate" to birthDate,
            "avatar" to "",
            "roleId" to roleId,
            "accountId" to uid
        )

        db.collection("accounts").document(uid).set(accountData)
            .addOnSuccessListener {
                db.collection("users").document(uid).set(userData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Đăng ký thành công ✅", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Lỗi lưu user: ${e.message}", Toast.LENGTH_SHORT).show()
                        binding.progressBar.visibility = View.GONE
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi lưu account: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
            }
    }
}
