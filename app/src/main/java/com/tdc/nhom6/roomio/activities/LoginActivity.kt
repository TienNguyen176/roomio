package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.databinding.LoginLayoutBinding
import at.favre.lib.crypto.bcrypt.BCrypt

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginLayoutBinding
    private val db = FirebaseFirestore.getInstance()

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

            // 🔸 Tìm tài khoản theo email trong collection "accounts"
            db.collection("accounts")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { result ->
                    if (result.isEmpty) {
                        Toast.makeText(this, "Email chưa được đăng ký!", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val document = result.documents[0]
                    val storedHash = document.getString("password")

                    if (storedHash != null) {
                        // ✅ So sánh mật khẩu nhập vào với mật khẩu hash trong Firestore
                        val verify = BCrypt.verifyer().verify(password.toCharArray(), storedHash)

                        if (verify.verified) {
                            // ✅ Đăng nhập thành công
                            db.collection("users")
                                .whereEqualTo("email", email)
                                .get()
                                .addOnSuccessListener { userResult ->
                                    val userDoc = userResult.documents.firstOrNull()
                                    val username = userDoc?.getString("username") ?: "Người dùng"

                                    Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()

                                    val intent = Intent(this, ForgotPasswordActivity::class.java)
                                    intent.putExtra("email", email)
                                    intent.putExtra("username", username)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Lỗi khi tải thông tin người dùng: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "Sai mật khẩu!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Không tìm thấy mật khẩu người dùng.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Lỗi Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 🔹 Khi nhấn “Bạn chưa có tài khoản?”
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, ProfileSignUpActivity::class.java))
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        // 🔹 Khi nhấn “Quên mật khẩu?” ghimmmm
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }
}
//package com.tdc.nhom6.roomio.activities
//
//import android.content.Intent
//import android.os.Bundle
//import android.util.Patterns
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import at.favre.lib.crypto.bcrypt.BCrypt
//import com.google.android.gms.auth.api.signin.GoogleSignIn
//import com.google.android.gms.auth.api.signin.GoogleSignInAccount
//import com.google.android.gms.auth.api.signin.GoogleSignInClient
//import com.google.android.gms.auth.api.signin.GoogleSignInOptions
//import com.google.android.gms.common.api.ApiException
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.auth.GoogleAuthProvider
//import com.google.firebase.firestore.FirebaseFirestore
//import com.tdc.nhom6.roomio.databinding.LoginLayoutBinding
//
//class LoginActivity : AppCompatActivity() {
//
//    private lateinit var binding: LoginLayoutBinding
//    private val db = FirebaseFirestore.getInstance()
//    private lateinit var auth: FirebaseAuth
//    private lateinit var googleSignInClient: GoogleSignInClient
//
//    private val RC_SIGN_IN = 100
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = LoginLayoutBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        auth = FirebaseAuth.getInstance()
//
//        // ✅ Cấu hình Google Sign In
//        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//            .requestIdToken(getString(com.tdc.nhom6.roomio.R.string.default_web_client_id))
//            .requestEmail()
//            .build()
//        googleSignInClient = GoogleSignIn.getClient(this, gso)
//
//        // 📌 Đăng nhập bằng email + mật khẩu (FireStore)
//        binding.btnLogin.setOnClickListener {
//            val email = binding.edtEmail.text.toString().trim()
//            val password = binding.edtPassword.text.toString().trim()
//
//            if (email.isEmpty() || password.isEmpty()) {
//                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
//                Toast.makeText(this, "Địa chỉ email không hợp lệ", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            db.collection("accounts")
//                .whereEqualTo("email", email)
//                .get()
//                .addOnSuccessListener { result ->
//                    if (result.isEmpty) {
//                        Toast.makeText(this, "Email chưa được đăng ký!", Toast.LENGTH_SHORT).show()
//                        return@addOnSuccessListener
//                    }
//
//                    val document = result.documents[0]
//                    val storedHash = document.getString("password")
//
//                    if (storedHash != null) {
//                        val verify = BCrypt.verifyer().verify(password.toCharArray(), storedHash)
//                        if (verify.verified) {
//                            Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
//                            startActivity(Intent(this, MainActivity::class.java))
//                            finish()
//                        } else {
//                            Toast.makeText(this, "Sai mật khẩu!", Toast.LENGTH_SHORT).show()
//                        }
//                    } else {
//                        Toast.makeText(this, "Không tìm thấy mật khẩu người dùng.", Toast.LENGTH_SHORT).show()
//                    }
//                }
//                .addOnFailureListener { e ->
//                    Toast.makeText(this, "Lỗi Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
//                }
//        }
//
//        // 📌 Đăng nhập bằng Google
//        binding.btnGoogle.setOnClickListener {
//            val signInIntent = googleSignInClient.signInIntent
//            startActivityForResult(signInIntent, RC_SIGN_IN)
//        }
//
//        // 📌 Quên mật khẩu
//        binding.tvForgotPassword.setOnClickListener {
//            startActivity(Intent(this, ForgotPasswordActivity::class.java))
//        }
//
//        // 📌 Đăng ký
//        binding.tvRegister.setOnClickListener {
//            startActivity(Intent(this, ProfileSignUpActivity::class.java))
//        }
//    }
//
//    // ✅ Xử lý kết quả đăng nhập Google
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == RC_SIGN_IN) {
//            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
//            try {
//                val account = task.getResult(ApiException::class.java)!!
//                firebaseAuthWithGoogle(account)
//            } catch (e: ApiException) {
//                Toast.makeText(this, "Đăng nhập Google thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    // ✅ Xác thực với Firebase bằng tài khoản Google
//    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
//        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
//        auth.signInWithCredential(credential)
//            .addOnSuccessListener { result ->
//                val user = result.user
//                val email = user?.email ?: "Unknown"
//
//                // Lưu vào Firestore nếu là lần đầu đăng nhập
//                val docRef = db.collection("accounts").whereEqualTo("email", email)
//                docRef.get().addOnSuccessListener { snapshot ->
//                    if (snapshot.isEmpty) {
//                        val newUser = hashMapOf(
//                            "email" to email,
//                            "password" to "" // Google không có password
//                        )
//                        db.collection("accounts").add(newUser)
//                    }
//                }
//
//                Toast.makeText(this, "Đăng nhập Google thành công!", Toast.LENGTH_SHORT).show()
//                startActivity(Intent(this, MainActivity::class.java))
//                finish()
//            }
//            .addOnFailureListener { e ->
//                Toast.makeText(this, "Lỗi xác thực Google: ${e.message}", Toast.LENGTH_SHORT).show()
//            }
//    }
//}

