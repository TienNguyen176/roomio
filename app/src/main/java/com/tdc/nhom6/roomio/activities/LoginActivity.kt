package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import at.favre.lib.crypto.bcrypt.BCrypt
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.LoginLayoutBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs by lazy { getSharedPreferences("user_prefs", MODE_PRIVATE) }

    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_GOOGLE_SIGN_IN = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPasswordEye()
        setupGoogleSignIn()

        // Nếu người dùng đã đăng nhập trước đó và có "Lưu tài khoản" → bỏ qua màn login
        val savedUid = prefs.getString("uid", null)
        val isSaved = prefs.getBoolean("isSaved", false)
        val currentUser = auth.currentUser

        if (isSaved && savedUid != null && currentUser != null) {
            showLoading(true)
            db.collection("users").document(savedUid).get()
                .addOnSuccessListener { doc ->
                    showLoading(false)
                    if (doc.exists()) {
                        val email = doc.getString("email") ?: ""
                        val username = doc.getString("username") ?: "Người dùng"
                        //toast("Đã đăng nhập tự động")
                        goToProfile(email, username)
                    } else {
                        prefs.edit().clear().apply()
                        toast("Không tìm thấy hồ sơ người dùng, vui lòng đăng nhập lại")
                    }
                }
                .addOnFailureListener {
                    showLoading(false)
                    toast("Lỗi tải dữ liệu: ${it.message}")
                }
            return
        }

        //  Hiển thị email nếu trước đó có lưu
        val savedEmail = prefs.getString("email", "")
        val savedChecked = prefs.getBoolean("isSaved", false)
        if (savedChecked) {
            binding.edtEmail.setText(savedEmail)
            binding.saveAccount.isChecked = true
        }

        // Nút đăng nhập thường
        binding.btnLogin.setOnClickListener { loginWithEmail() }

        // Đăng nhập Google
        binding.btnGoogle.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
        }

        // Chuyển sang đăng ký
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, ProfileSignUpActivity::class.java))
        }

        // Quên mật khẩu
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }


    // signin email

    private fun loginWithEmail() {
        val email = binding.edtEmail.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            toast("Vui lòng nhập đầy đủ thông tin")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Địa chỉ email không hợp lệ")
            return
        }

        showLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener
                val uid = user.uid
                val newHash = BCrypt.withDefaults().hashToString(12, password.toCharArray())

                db.collection("accounts").document(uid)
                    .update("password", newHash)
                    .addOnSuccessListener {
                        if (binding.saveAccount.isChecked) {
                            prefs.edit()
                                .putString("uid", uid)
                                .putString("email", email)
                                .putBoolean("isSaved", true)
                                .apply()
                        }

                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { userDoc ->
                                showLoading(false)
                                if (userDoc.exists()) {
                                    val username = userDoc.getString("username") ?: "Người dùng"
                                    //toast("Đăng nhập thành công ")
                                    goToProfile(email, username)
                                } else {
                                    toast("Không tìm thấy hồ sơ người dùng")
                                }
                            }
                    }
            }
            .addOnFailureListener {
                showLoading(false)
                toast("Sai email hoặc mật khẩu ")
            }
    }


    // google signin

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { result ->
                            val user = result.user ?: return@addOnSuccessListener
                            val uid = user.uid

                            //  Lưu lại nếu có tick “Lưu tài khoản”
                            if (binding.saveAccount.isChecked) {
                                prefs.edit()
                                    .putString("uid", uid)
                                    .putString("email", user.email)
                                    .putBoolean("isSaved", true)
                                    .apply()
                            }

                            db.collection("users").document(uid).get()
                                .addOnSuccessListener { doc ->
                                    if (doc.exists()) {
                                        goToProfile(user.email ?: "")
                                    } else {
                                        val intent = Intent(this, EditProfileActivity::class.java)
                                        intent.putExtra("fromGoogle", true)
                                        intent.putExtra("email", user.email)
                                        intent.putExtra("username", user.displayName)
                                        intent.putExtra("avatar", user.photoUrl?.toString() ?: "")
                                        startActivity(intent)
                                        finish()
                                    }
                                }
                        }
                        .addOnFailureListener {
                            toast("Đăng nhập Google thất bại: ${it.message}")
                        }
                }
            } catch (e: ApiException) {
                toast("Đăng nhập Google bị hủy hoặc lỗi: ${e.statusCode}")
            }
        }
    }


    // CHUNG

    private fun goToProfile(email: String? = null, username: String? = null) {
        val intent = Intent(this, ProfileActivity::class.java)
        email?.let { intent.putExtra("email", it) }
        username?.let { intent.putExtra("username", it) }
        startActivity(intent)
        finish()
    }

    private fun setupPasswordEye() {
        binding.edtPassword.transformationMethod =
            android.text.method.PasswordTransformationMethod.getInstance()
        binding.edtPassword.setSelection(binding.edtPassword.text?.length ?: 0)

        binding.passwordLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        binding.passwordLayout.endIconDrawable =
            ContextCompat.getDrawable(this, R.drawable.ic_hide_eye)
        binding.passwordLayout.endIconDrawable?.setTint(
            ContextCompat.getColor(this, R.color.orange)
        )

        binding.passwordLayout.setEndIconOnClickListener {
            val isVisible =
                binding.edtPassword.transformationMethod !is android.text.method.PasswordTransformationMethod
            binding.edtPassword.transformationMethod =
                if (isVisible) android.text.method.PasswordTransformationMethod.getInstance() else null
            binding.edtPassword.setSelection(binding.edtPassword.text?.length ?: 0)

            val iconRes = if (isVisible) R.drawable.ic_hide_eye else R.drawable.ic_show_eye
            binding.passwordLayout.endIconDrawable =
                ContextCompat.getDrawable(this, iconRes)
            binding.passwordLayout.endIconDrawable?.setTint(
                ContextCompat.getColor(this, R.color.orange)
            )
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
