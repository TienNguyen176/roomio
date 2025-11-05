package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.tdc.nhom6.roomio.databinding.ResetPasswordLayoutBinding

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ResetPasswordLayoutBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var email: String
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ResetPasswordLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        email = intent.getStringExtra("email") ?: ""

        binding.tvEmail.text = email
        binding.btnBack.setOnClickListener { finish() }

        // Hiển thị trạng thái đang chờ
        binding.tvStatus.text = "Đang chờ bạn xác minh qua email..."
        binding.progressBar.visibility = android.view.View.VISIBLE

        // Cứ 5 giây hiển thị nhắc người dùng (mô phỏng chờ reset)
        handler.postDelayed(checkResetDoneRunnable, 5000)

        // Nút gửi lại email reset mật khẩu
        binding.btnResendEmail.setOnClickListener {
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        "Đã gửi lại liên kết đặt lại mật khẩu đến $email",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Mô phỏng quá trình “đổi mật khẩu thành công”
    private val checkResetDoneRunnable = object : Runnable {
        override fun run() {
            Toast.makeText(
                this@ResetPasswordActivity,
                "Nếu bạn đã đổi mật khẩu qua email, hãy quay lại đăng nhập nhé!",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(this@ResetPasswordActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("email", email)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkResetDoneRunnable)
    }
}
