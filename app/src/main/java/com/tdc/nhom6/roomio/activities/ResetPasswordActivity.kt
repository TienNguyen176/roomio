package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tdc.nhom6.roomio.databinding.ResetPasswordLayoutBinding

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ResetPasswordLayoutBinding
    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ResetPasswordLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        email = intent.getStringExtra("email") ?: ""
        binding.tvEmail.text = email

        binding.btnBack.setOnClickListener { finish() }

        binding.btnVerifyDone.setOnClickListener {
            // Sau khi người dùng xác minh xong qua email, chuyển sang màn hình đổi mật khẩu
            val intent = Intent(this, ChangePasswordActivity::class.java)
            intent.putExtra("email", email)
            startActivity(intent)
            finish()
        }

        binding.btnResendEmail.setOnClickListener {
            Toast.makeText(this, "Vui lòng kiểm tra lại email 📩", Toast.LENGTH_SHORT).show()
        }
    }
}
