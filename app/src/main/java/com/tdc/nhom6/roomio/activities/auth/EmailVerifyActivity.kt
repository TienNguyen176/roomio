package com.tdc.nhom6.roomio.activities.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private var handler: Handler? = null

    private lateinit var username: String
    private lateinit var email: String
    private lateinit var phone: String
    private lateinit var genderId: String
    private lateinit var birthDate: String
    private lateinit var password: String
    private var roleId: String = "user"
    private var walletBalance: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VerifyEmailLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadData()
        setupUI()
        createAccountAndSendEmail()
    }

    private fun loadData() = with(intent) {
        username = getStringExtra("username") ?: ""
        email = getStringExtra("email") ?: ""
        phone = getStringExtra("phone") ?: ""
        genderId = getStringExtra("genderId") ?: ""
        birthDate = getStringExtra("birthDate") ?: ""
        password = getStringExtra("password") ?: ""
        roleId = getStringExtra("roleId") ?: "user"
        walletBalance = getDoubleExtra("walletBalance", 0.0)
    }

    private fun setupUI() = binding.apply {
        edtEmailVerify.text = email

        btnResendEmail.setOnClickListener {
            auth.currentUser?.sendEmailVerification()
                ?.addOnSuccessListener { toast("Đã gửi lại email xác minh") }
                ?.addOnFailureListener { toast("Lỗi: ${it.message}") }
        }
    }

    private fun createAccountAndSendEmail() {
        toggleLoading(true, "Đang gửi email xác minh...")

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                auth.currentUser?.sendEmailVerification()
                startVerifyChecking()
            }
            .addOnFailureListener {
                toast("Lỗi tạo tài khoản: ${it.message}")
                finish()
            }
    }

    private fun startVerifyChecking() {
        toggleLoading(true, "Đang chờ xác minh...")

        handler = Handler(Looper.getMainLooper())
        handler?.postDelayed(object : Runnable {
            override fun run() {
                auth.currentUser?.reload()?.addOnSuccessListener {
                    if (auth.currentUser?.isEmailVerified == true) {
                        saveUserToFirestore(auth.currentUser!!.uid)
                        handler?.removeCallbacks(this)
                    } else {
                        handler?.postDelayed(this, 2000)
                    }
                }
            }
        }, 2000)
    }

    private fun saveUserToFirestore(uid: String) {
        val time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            .format(Date())

        val userData = mapOf(
            "avatar" to "",
            "username" to username,
            "email" to email,
            "phone" to phone,
            "genderId" to genderId,
            "birthDate" to birthDate,
            "roleId" to roleId,
            "walletBalance" to walletBalance,
            "createdAt" to time
        )

        db.collection("users").document(uid).set(userData)
            .addOnSuccessListener {
                toast("Đăng ký thành công!")
                goToLogin()
            }
            .addOnFailureListener {
                toast("Lỗi lưu dữ liệu: ${it.message}")
            }

    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun toggleLoading(active: Boolean, msg: String = "") = binding.apply {
        progressBar.visibility = if (active) View.VISIBLE else View.GONE
        txtStatus.visibility = if (active) View.VISIBLE else View.GONE
        txtStatus.text = msg
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacksAndMessages(null)
    }
}
