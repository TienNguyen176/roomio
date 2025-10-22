package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.tdc.nhom6.roomio.databinding.ForgotpasswordLayoutBinding

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ForgotpasswordLayoutBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ForgotpasswordLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnContinue.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()

            // 🔸 Kiểm tra email hợp lệ
            if (email.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Email không hợp lệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🔸 Gửi link reset mật khẩu
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        "Đã gửi liên kết đặt lại mật khẩu đến $email.\nHãy kiểm tra hộp thư!",
                        Toast.LENGTH_LONG
                    ).show()

                    // ✅ Sau khi gửi link → quay về màn hình đăng nhập
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 🔹 Nút quay lại
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
