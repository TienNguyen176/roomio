package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.databinding.LoginLayoutBinding
import at.favre.lib.crypto.bcrypt.BCrypt

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()

            // ✅ Kiểm tra đầu vào
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Địa chỉ email không hợp lệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🔹 Xác thực qua Firebase Auth (vì mật khẩu có thể đã đổi qua link)
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user ?: return@addOnSuccessListener
                    val uid = user.uid

                    // ✅ Hash lại mật khẩu mới và cập nhật vào Firestore (đồng bộ)
                    val newHash = BCrypt.withDefaults().hashToString(12, password.toCharArray())

                    db.collection("accounts").document(uid)
                        .update("password", newHash)
                        .addOnSuccessListener {
                            // 🔹 Lấy thông tin user hiển thị (nếu có)
                            db.collection("users")
                                .whereEqualTo("email", email)
                                .get()
                                .addOnSuccessListener { userResult ->
                                    val userDoc = userResult.documents.firstOrNull()
                                    val username = userDoc?.getString("username") ?: "Người dùng"

                                    Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()

                                    val intent = Intent(this, ProfileActivity::class.java)
                                    intent.putExtra("email", email)
                                    intent.putExtra("username", username)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Lỗi tải thông tin: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Lỗi cập nhật mật khẩu: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    // Nếu sai mật khẩu hoặc email chưa reset qua Firebase Auth
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
}
