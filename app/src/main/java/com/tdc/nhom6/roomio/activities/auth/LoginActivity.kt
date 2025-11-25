package com.tdc.nhom6.roomio.activities.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.home.HomeActivity
import com.tdc.nhom6.roomio.activities.profile.EditProfileActivity
import com.tdc.nhom6.roomio.databinding.LoginLayoutBinding
import com.tdc.nhom6.roomio.repositories.FCMRepository
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginLayoutBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs by lazy { getSharedPreferences("user_prefs", MODE_PRIVATE) }

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var fcmRepo: FCMRepository

    private val RC_GOOGLE_SIGN_IN = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fcmRepo = FCMRepository(this)

        setupPasswordEye()
        setupGoogleSignIn()
        checkAutoLogin()
        setupListeners()
    }

    // ==============================================================
    // AUTO LOGIN
    // ==============================================================
    private fun checkAutoLogin() {
        val uid = prefs.getString("uid", null)
        val isSaved = prefs.getBoolean("isSaved", false)
        val user = auth.currentUser

        if (isSaved && uid != null && user != null) {
            showLoading(true)
            loadUserProfile(uid, autoLogin = true)
        }
    }

    // ==============================================================
    // LISTENERS
    // ==============================================================
    private fun setupListeners() {
        binding.btnLogin.setOnClickListener { loginWithEmail() }

        binding.btnGoogle.setOnClickListener {
            startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE_SIGN_IN)
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, ProfileSignUpActivity::class.java))
        }

        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // Hiển thị email nếu có lưu
        val savedEmail = prefs.getString("email", "")
        val savedChecked = prefs.getBoolean("isSaved", false)
        if (savedChecked) {
            binding.edtEmail.setText(savedEmail)
            binding.saveAccount.isChecked = true
        }
    }

    // ==============================================================
    // LOGIN WITH EMAIL
    // ==============================================================
    private fun loginWithEmail() {
        val email = binding.edtEmail.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        if (!validateEmailPassword(email, password)) return

        showLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                saveAccountIfNeeded(uid, email)
                loadUserProfile(uid)
                toast("Đăng nhập thành công")
            }
            .addOnFailureListener {
                showLoading(false)
                toast("Sai email hoặc mật khẩu")
            }
    }

    private fun validateEmailPassword(email: String, password: String): Boolean {
        if (email.isEmpty() || password.isEmpty()) {
            toast("Vui lòng nhập đầy đủ thông tin")
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Email không hợp lệ")
            return false
        }
        return true
    }

    // ==============================================================
    // LOAD USER PROFILE
    // ==============================================================
    private fun loadUserProfile(uid: String, autoLogin: Boolean = false) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                showLoading(false)
                if (!doc.exists()) {
                    toast("Không tìm thấy hồ sơ người dùng")
                    return@addOnSuccessListener
                }

                val email = doc.getString("email") ?: ""
                val username = doc.getString("username") ?: "Người dùng"

                if (autoLogin) toast("Đã đăng nhập tự động")

                sendFCMTokenToServer(uid)
                goToHome(email, username)
            }
            .addOnFailureListener {
                showLoading(false)
                toast("Lỗi kết nối: ${it.message}")
            }
    }

    // ==============================================================
    // GOOGLE SIGN-IN
    // ==============================================================
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != RC_GOOGLE_SIGN_IN) return

        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java) ?: return
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            auth.signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    val user = result.user ?: return@addOnSuccessListener
                    val uid = user.uid

                    saveAccountIfNeeded(uid, user.email ?: "")

                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                sendFCMTokenToServer(uid)
                                goToHome(user.email ?: "")
                            } else {
                                // Lần đầu đăng nhập → chuyển sang setup profile
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

        } catch (e: ApiException) {
            toast("Google Sign-in bị hủy hoặc lỗi")
        }
    }

    // ==============================================================
    // COMMON
    // ==============================================================
    private fun saveAccountIfNeeded(uid: String, email: String) {
        if (binding.saveAccount.isChecked) {
            prefs.edit()
                .putString("uid", uid)
                .putString("email", email)
                .putBoolean("isSaved", true)
                .apply()
        }
    }

    private fun sendFCMTokenToServer(userId: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                lifecycleScope.launch { fcmRepo.registerToken(token, userId) }
            }
        }
    }

    private fun goToHome(email: String? = null, username: String? = null) {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            email?.let { putExtra("email", it) }
            username?.let { putExtra("username", it) }
        }
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
