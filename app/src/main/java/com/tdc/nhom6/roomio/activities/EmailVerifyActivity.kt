package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import at.favre.lib.crypto.bcrypt.BCrypt
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.tdc.nhom6.roomio.databinding.VerifyEmailLayoutBinding
import java.text.SimpleDateFormat
import java.util.*

class EmailVerifyActivity : AppCompatActivity() {

    private lateinit var binding: VerifyEmailLayoutBinding
    private val auth = Firebase.auth
    private val db = FirebaseFirestore.getInstance()
    private var checkVerifyHandler: Handler? = null

    // Dữ liệu đăng ký
    private lateinit var currentId: String
    private lateinit var username: String
    private lateinit var email: String
    private lateinit var phone: String
    private lateinit var gender: String
    private lateinit var birthDate: String
    private lateinit var password: String
    private var roleId: String = "user"
    private var walletBalance: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VerifyEmailLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receiveDataFromSignUp()
        setupUI()
        createAccountAndSendVerify()
    }

    //nhan du lieu
    private fun receiveDataFromSignUp() = with(intent) {
        currentId = getStringExtra("current_id") ?: ""
        username = getStringExtra("username") ?: ""
        email = getStringExtra("email") ?: ""
        phone = getStringExtra("phone") ?: ""
        gender = getStringExtra("gender") ?: ""
        birthDate = getStringExtra("birthDate") ?: ""
        password = getStringExtra("password") ?: ""
        roleId = getStringExtra("roleId") ?: "user"
        walletBalance = getDoubleExtra("balance", 0.0)
    }

    //giao dien
    private fun setupUI() = binding.apply {
        edtEmailVerify.text = email

        btnResendEmail.setOnClickListener {
            auth.currentUser?.sendEmailVerification()
                ?.addOnSuccessListener { toast("Đã gửi lại email xác minh ") }
                ?.addOnFailureListener { toast("Lỗi gửi email: ${it.message}") }
        }
    }

    //Tao account gui email
    private fun createAccountAndSendVerify() {
        toggleLoading(true, "Đang gửi email xác minh...")

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                auth.currentUser?.sendEmailVerification()
                    ?.addOnSuccessListener {
                        toast("Đã gửi email xác minh ")
                        toggleLoading(true, "⏳ Đang chờ xác minh...")
                        startAutoCheckVerification()
                    }
                    ?.addOnFailureListener {
                        toast("Gửi email thất bại: ${it.message}")
                        toggleLoading(false)
                    }
            }
            .addOnFailureListener {
                val msg = if (it.message?.contains("already in use", true) == true)
                    "Email này đã được đăng ký"
                else "Lỗi tạo tài khoản: ${it.message}"
                toast(msg)
                finish()
            }
    }

    //Ktra xac minh tu dong
    private fun startAutoCheckVerification() {
        checkVerifyHandler = Handler(Looper.getMainLooper())
        checkVerifyHandler?.postDelayed(object : Runnable {
            override fun run() {
                auth.currentUser?.reload()?.addOnSuccessListener {
                    if (auth.currentUser?.isEmailVerified == true) {
                        toast("Email đã được xác minh!")
                        saveAccountAndUser(auth.currentUser!!.uid)
                        checkVerifyHandler?.removeCallbacks(this)
                    } else {
                        checkVerifyHandler?.postDelayed(this, 3000)
                    }
                }
            }
        }, 3000)
    }

    //luu len database
    private fun saveAccountAndUser(uid: String) {
        val formattedTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
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
            "balance" to walletBalance,
            "createdAt" to formattedTime
        )

        db.collection("accounts").document(uid).set(accountData)
            .continueWithTask { db.collection("users").document(uid).set(userData) }
            .addOnSuccessListener {
                toast("🎉 Đăng ký thành công!")
                navigateToLogin()
            }
            .addOnFailureListener {
                toast("Lỗi lưu dữ liệu: ${it.message}")
                toggleLoading(false)
            }
    }

    //Chuyen man hinh
    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    //loading
    private fun toggleLoading(isLoading: Boolean, message: String = "") = binding.apply {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        txtStatus.text = message
        txtStatus.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        checkVerifyHandler?.removeCallbacksAndMessages(null)
    }
}
