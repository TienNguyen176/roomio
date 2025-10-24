package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import at.favre.lib.crypto.bcrypt.BCrypt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.databinding.LoginLayoutBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs by lazy { getSharedPreferences("user_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔹 Tự động điền thông tin nếu đã lưu
        val savedEmail = prefs.getString("email", "")
        val isSaved = prefs.getBoolean("isSaved", false)

        if (isSaved) {
            binding.edtEmail.setText(savedEmail)
            binding.saveAccount.isChecked = true
        }

        // 🔹 Nút đăng nhập
        binding.btnLogin.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Địa chỉ email không hợp lệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🔹 Hiện loading
            showLoading(true)

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user ?: return@addOnSuccessListener
                    val uid = user.uid

                    val newHash = BCrypt.withDefaults().hashToString(12, password.toCharArray())

                    db.collection("accounts").document(uid)
                        .update("password", newHash)
                        .addOnSuccessListener {
                            // ✅ Lưu tài khoản nếu người dùng chọn
                            if (binding.saveAccount.isChecked) {
                                prefs.edit()
                                    .putString("email", email)
                                    .putBoolean("isSaved", true)
                                    .apply()
                            } else {
                                prefs.edit().clear().apply()
                            }

                            db.collection("users")
                                .whereEqualTo("email", email)
                                .get()
                                .addOnSuccessListener { userResult ->
                                    val username = userResult.documents.firstOrNull()?.getString("username") ?: "Người dùng"

                                    Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                                    showLoading(false)

                                    val intent = Intent(this, ProfileActivity::class.java)
                                    intent.putExtra("email", email)
                                    intent.putExtra("username", username)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    showLoading(false)
                                    Toast.makeText(this, "Lỗi tải thông tin: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .addOnFailureListener { e ->
                            showLoading(false)
                            Toast.makeText(this, "Lỗi cập nhật mật khẩu: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Toast.makeText(this, "Sai email hoặc mật khẩu: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 🔹 Khi nhấn “Bạn chưa có tài khoản?”
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, ProfileSignUpActivity::class.java))
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        // 🔹 Khi nhấn “Quên mật khẩu?”
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    // ------------------ Loading ------------------
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE

        // 🔹 Disable toàn bộ layout khi đang loading
        val visibility = if (show) View.GONE else View.VISIBLE
        binding.edtEmail.visibility = visibility
        binding.edtPassword.visibility = visibility
        binding.btnLogin.isEnabled = !show
        binding.saveAccount.isEnabled = !show
        binding.tvForgotPassword.isEnabled = !show
        binding.tvRegister.isEnabled = !show

        // Làm mờ
        val alpha = if (show) 0.4f else 1f
        binding.edtEmail.alpha = alpha
        binding.edtPassword.alpha = alpha
        binding.btnLogin.alpha = alpha
        binding.saveAccount.alpha = alpha
        binding.tvForgotPassword.alpha = alpha
        binding.tvRegister.alpha = alpha
    }
}
