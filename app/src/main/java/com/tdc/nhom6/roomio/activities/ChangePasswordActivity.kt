package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import at.favre.lib.crypto.bcrypt.BCrypt
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.tdc.nhom6.roomio.databinding.ChangePasswordLayoutBinding

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var binding: ChangePasswordLayoutBinding
    private val auth = Firebase.auth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ChangePasswordLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnChangePassword.setOnClickListener {
            val oldPassword = binding.edtOldPassword.text.toString().trim()
            val newPassword = binding.edtNewPassword.text.toString().trim()
            val confirmPassword = binding.edtConfirmPassword.text.toString().trim()
            val email = auth.currentUser?.email

            if (email.isNullOrEmpty()) {
                Toast.makeText(this, "Không tìm thấy tài khoản đang đăng nhập", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                Toast.makeText(this, "Mật khẩu mới không khớp", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ Lấy mật khẩu hash từ Firestore
            db.collection("accounts")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { result ->
                    if (result.isEmpty) {
                        Toast.makeText(this, "Không tìm thấy tài khoản trong hệ thống", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val doc = result.documents[0]
                    val storedHash = doc.getString("password")

                    if (storedHash == null) {
                        Toast.makeText(this, "Lỗi: không tìm thấy mật khẩu trong Firestore", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    // ✅ So sánh mật khẩu cũ
                    val verify = BCrypt.verifyer().verify(oldPassword.toCharArray(), storedHash)
                    if (!verify.verified) {
                        Toast.makeText(this, "Mật khẩu cũ không đúng", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    // 🔐 Re-auth Firebase Auth trước khi đổi
                    val credential = EmailAuthProvider.getCredential(email, oldPassword)
                    auth.currentUser?.reauthenticate(credential)
                        ?.addOnSuccessListener {
                            // ✅ Cập nhật mật khẩu Firebase Auth
                            auth.currentUser?.updatePassword(newPassword)
                                ?.addOnSuccessListener {
                                    // ✅ Cập nhật mật khẩu mới vào Firestore (hash)
                                    val newHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
                                    db.collection("accounts").document(doc.id)
                                        .update("password", newHash)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Đổi mật khẩu thành công ✅", Toast.LENGTH_SHORT).show()
                                            // ➡️ Quay về Login hoặc trang chủ tuỳ ý
                                            auth.signOut()
                                            val intent = Intent(this, LoginActivity::class.java)
                                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            startActivity(intent)
                                            finish()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(this, "Lỗi cập nhật Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                                ?.addOnFailureListener { e ->
                                    Toast.makeText(this, "Lỗi đổi mật khẩu: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        ?.addOnFailureListener {
                            Toast.makeText(this, "Xác thực mật khẩu cũ thất bại", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Lỗi kết nối Firestore", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
