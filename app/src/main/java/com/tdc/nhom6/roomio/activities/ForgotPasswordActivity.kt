package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.tdc.nhom6.roomio.databinding.ForgotpasswordLayoutBinding

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ForgotpasswordLayoutBinding
    private val auth = Firebase.auth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ForgotpasswordLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnContinue.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập email 📧", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🔸 Kiểm tra email có trong Firestore chưa
            db.collection("accounts")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { result ->
                    if (result.isEmpty) {
                        Toast.makeText(this, "Email chưa được đăng ký!", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    binding.btnContinue.isEnabled = false
                    binding.btnContinue.text = "Đang gửi..."

                    // 📨 Gửi email xác nhận từ Firebase Auth
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "Đã gửi link xác minh đổi mật khẩu tới email 📩",
                                Toast.LENGTH_LONG
                            ).show()

                            val intent = Intent(this, ResetPasswordActivity::class.java)
                            intent.putExtra("email", email)
                            startActivity(intent)
                        }
                        .addOnFailureListener { e ->
                            binding.btnContinue.isEnabled = true
                            binding.btnContinue.text = "Tiếp tục →"
                            Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Lỗi kiểm tra email!", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
