package com.tdc.nhom6.roomio.activities

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ProfileLayoutBinding
import com.tdc.nhom6.roomio.models.User
import com.tdc.nhom6.roomio.utils.navigateTo

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ProfileLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs by lazy { getSharedPreferences("user_prefs", MODE_PRIVATE) }

    private var userRoleId = "user"
    private var roleName = "User"
    private var currentBalance = 0L
    private var isBalanceVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProfileLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.title = "Roomio"

        checkSession()
        setupActions()
        setupWalletToggle()
        setupToolbarMenuClick()
    }

    private fun checkSession() {
        val firebaseUser = auth.currentUser
        val savedUid = prefs.getString("uid", null)

        if (firebaseUser == null && savedUid == null) {
            Toast.makeText(this, "Phiên đăng nhập hết hạn", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        loadUserData()
        loadWalletData()
    }

    private fun setupWalletToggle() {
        binding.imgEye.setOnClickListener {
            isBalanceVisible = !isBalanceVisible
            binding.tvBalance.text =
                if (isBalanceVisible) formatMoney(currentBalance) else "••••••••"
        }
    }

    private fun setupActions() {
        binding.showProfile.setOnClickListener {
            startActivityForResult(Intent(this, EditProfileActivity::class.java), 100)
        }

        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            prefs.edit().clear().apply()
            Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
            )
            finish()
        }
    }

    private fun setupToolbarMenuClick() {
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.navAdmin -> {
                    navigateTo(AdminHomeActivity::class.java, flag = false)
                    true
                }
                R.id.navChuKS -> {
                    startActivity(Intent(this, AdminHotelActivity::class.java))
                    true
                }
                R.id.navLeTan -> {
                    startActivity(Intent(this, ReceptionActivity::class.java))
                    true
                }
                R.id.navDonPhong -> {
                    startActivity(Intent(this, CleanerActivity::class.java))
                    true
                }
                //R.id.navXuLy -> { startActivity(Intent(this, XuLyDonActivity::class.java)); true }
                else -> false
            }
        }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: prefs.getString("uid", null) ?: return

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java) ?: return@addOnSuccessListener
                binding.tvUsername.text = user.username.ifEmpty { "Người dùng" }

                if (user.avatar.isNotEmpty()) {
                    Glide.with(this).load(user.avatar).circleCrop().into(binding.imgAvatar)
                } else binding.imgAvatar.setImageResource(R.drawable.user)

                userRoleId = user.roleId.ifEmpty { "user" }
                loadRoleName(userRoleId)
            }
    }

    private fun loadRoleName(roleId: String) {
        db.collection("userRoles").document(roleId)
            .get()
            .addOnSuccessListener { doc ->
                roleName = doc.getString("role_name") ?: roleId.capitalize()
                binding.tvRank.text = roleName
                updateRoleUI(roleId)
                animateRoleColor(roleId)
                invalidateOptionsMenu()
            }
            .addOnFailureListener {
                binding.tvRank.text = roleId.capitalize()
                updateRoleUI(roleId)
            }
    }

    private fun loadWalletData() {
        val uid = auth.currentUser?.uid ?: prefs.getString("uid", null) ?: return
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                currentBalance = doc.getLong("balance") ?: 0L
                binding.tvBalance.text =
                    if (isBalanceVisible) formatMoney(currentBalance) else "••••••••"
            }
    }

    private fun updateRoleUI(roleId: String) {
        val colorMap = mapOf(
            "admin" to R.color.red,
            "owner" to R.color.orange,
            "letan" to R.color.green,
            "donphong" to R.color.light_blue,
            "xulydon" to R.color.purple
        )

        val colorRes = colorMap[roleId.lowercase()] ?: R.color.gray
        val shape = GradientDrawable().apply {
            cornerRadius = 25f
            setColor(ContextCompat.getColor(this@ProfileActivity, colorRes))
        }

        binding.tvRank.background = shape
    }

    private fun animateRoleColor(roleId: String) {
        val colorMap = mapOf(
            "admin" to Color.parseColor("#FF4C4C"),
            "owner" to Color.parseColor("#FFA500"),
            "letan" to Color.parseColor("#4CAF50"),
            "donphong" to Color.parseColor("#03A9F4"),
            "xulydon" to Color.parseColor("#9C27B0"),
            "user" to Color.parseColor("#BDBDBD")
        )

        val targetColor = colorMap[roleId.lowercase()] ?: Color.parseColor("#BDBDBD")
        val bg = binding.tvRank.background as GradientDrawable
        val currentColor = bg.color?.defaultColor ?: Color.GRAY

        ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor).apply {
            duration = 600
            addUpdateListener { bg.setColor(it.animatedValue as Int) }
        }.start()
    }

    private fun formatMoney(amount: Long) = String.format("%,d VNĐ", amount).replace(",", ".")

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_top_profile, menu)

        // Ẩn tất cả menu trước
        menu?.findItem(R.id.navAdmin)?.isVisible = false
        menu?.findItem(R.id.navChuKS)?.isVisible = false
        menu?.findItem(R.id.navLeTan)?.isVisible = false
        menu?.findItem(R.id.navDonPhong)?.isVisible = false
        menu?.findItem(R.id.navXuLy)?.isVisible = false

        // Hiển thị menu theo role
        when (userRoleId.lowercase()) {
            "admin" -> menu?.findItem(R.id.navAdmin)?.isVisible = true
            "owner" -> menu?.findItem(R.id.navChuKS)?.isVisible = true
            "letan" -> menu?.findItem(R.id.navLeTan)?.isVisible = true
            "donphong" -> menu?.findItem(R.id.navDonPhong)?.isVisible = true
            "xulydon" -> menu?.findItem(R.id.navXuLy)?.isVisible = true
        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) loadUserData()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }
}
