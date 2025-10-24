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
import java.text.SimpleDateFormat
import java.util.*

class EmailVerifyActivity : AppCompatActivity() {

    private lateinit var binding: VerifyEmailLayoutBinding
    private val auth = Firebase.auth
    private val db = FirebaseFirestore.getInstance()

    private lateinit var currentId: String
    private lateinit var username: String
    private lateinit var email: String
    private lateinit var phone: String
    private lateinit var gender: String
    private lateinit var birthDate: String
    private lateinit var password: String
    private var roleId: String = "user"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VerifyEmailLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 📩 Nhận dữ liệu từ ProfileSignUpActivity
        with(intent) {
            currentId = getStringExtra("current_id") ?: ""
            username = getStringExtra("username") ?: ""
            email = getStringExtra("email") ?: ""
            phone = getStringExtra("phone") ?: ""
            gender = getStringExtra("gender") ?: ""
            birthDate = getStringExtra("birthDate") ?: ""
            password = getStringExtra("password") ?: ""
            roleId = getStringExtra("roleId") ?: "user"
        }

        binding.edtEmailVerify.text = email
        createAccountAndSendVerify()

        binding.btnResendEmail.setOnClickListener { resendVerifyEmail() }

        binding.btnVerifyDone.setOnClickListener {
            toggleLoading(true)
            auth.currentUser?.reload()?.addOnSuccessListener {
                val user = auth.currentUser
                if (user?.isEmailVerified == true) {
                    saveAccountAndUser(user.uid)
                } else {
                    toast("Email chưa được xác minh ❌")
                    toggleLoading(false)
                }
            }?.addOnFailureListener {
                toast("Lỗi xác minh: ${it.message}")
                toggleLoading(false)
            }
        }
    }

    /**
     * 📤 Tạo tài khoản + gửi email xác minh
     */
    private fun createAccountAndSendVerify() {
        toggleLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                toggleLoading(false)
                auth.currentUser?.sendEmailVerification()
                    ?.addOnSuccessListener { toast("Đã gửi email xác minh 📩") }
                    ?.addOnFailureListener { toast("Gửi email thất bại: ${it.message}") }
            }
            .addOnFailureListener {
                toggleLoading(false)
                if (it.message?.contains("already in use", true) == true)
                    toast("Email này đã được đăng ký ❌")
                else toast("Lỗi tạo tài khoản: ${it.message}")
                finish()
            }
    }

    /**
     * 🔁 Gửi lại email xác minh
     */
    private fun resendVerifyEmail() {
        auth.currentUser?.sendEmailVerification()
            ?.addOnSuccessListener { toast("Đã gửi lại email xác minh 📩") }
            ?.addOnFailureListener { toast("Lỗi gửi email: ${it.message}") }
    }

    /**
     * 💾 Lưu account + user vào Firestore
     */
    private fun saveAccountAndUser(uid: String) {
        val now = System.currentTimeMillis()
        val formattedTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(now))
        val hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray())

        val accountData = mapOf(
            "email" to email,
            "phone" to phone,
            "password" to hashedPassword,
            "createdAt" to formattedTime
        )

        val userData = mapOf(
            "current_id" to currentId,
            "avatar" to "",
            "username" to username,
            "email" to email,
            "phone" to phone,
            "gender" to gender,
            "birthDate" to birthDate,
            "accountId" to uid,
            "roleId" to roleId,
            "createdAt" to formattedTime
        )

        db.collection("accounts").document(uid).set(accountData)
            .continueWithTask {
                db.collection("users").document(uid).set(userData)
            }
            .addOnSuccessListener {
                toast("Đăng ký thành công ✅")
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .addOnFailureListener {
                toast("Lỗi lưu dữ liệu: ${it.message}")
                toggleLoading(false)
            }
    }


     // An toan bo

    private fun toggleLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

        // Ẩn toàn bộ
        val visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.btnResendEmail.visibility = visibility
        binding.btnVerifyDone.visibility = visibility
        binding.edtEmailVerify.visibility = visibility
        binding.edtContent.visibility = visibility
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
