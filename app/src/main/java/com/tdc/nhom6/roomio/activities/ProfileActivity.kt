package com.tdc.nhom6.roomio.activities

import android.animation.ValueAnimator
import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ProfileLayoutBinding
import com.tdc.nhom6.roomio.models.User
import android.graphics.Color

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ProfileLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var userRoleId: String = "userRoles/user"
    private var roleName: String = "User"

    private var isBalanceVisible = true
    private var currentBalance: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProfileLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.title = "Roomio"

        setupActions()
        //wallet
        setupWalletToggle()
        loadUserData()
        //wallet
        loadWalletData()

    }
    // 🔹 Ẩn/hiện số dư ví
    private fun setupWalletToggle() {
        binding.imgEye.setOnClickListener {
            isBalanceVisible = !isBalanceVisible
            if (isBalanceVisible) {
                binding.tvBalance.text = formatMoney(currentBalance)
                binding.imgEye.setImageResource(R.drawable.eye)
            } else {
                binding.tvBalance.text = "•••••••••"
                binding.imgEye.setImageResource(R.drawable.eye)
            }
        }
    }
    private fun setupActions() {
        // 👉 Chỉnh sửa hồ sơ
        binding.showProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivityForResult(intent, 100)
        }

        // 👉 Đăng xuất
        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val user = doc.toObject(User::class.java)
                    if (user != null) {
                        binding.tvUsername.text = user.username.ifEmpty { "Người dùng" }

                        // Load avatar
                        if (user.avatar.isNotEmpty()) {
                            Glide.with(this)
                                .load(user.avatar)
                                .circleCrop()
                                .placeholder(R.drawable.user)
                                .into(binding.imgAvatar)
                        } else {
                            binding.imgAvatar.setImageResource(R.drawable.user)
                        }

                        userRoleId = user.roleId

                        // Lấy role name từ userRoles
                        db.document(userRoleId)
                            .get()
                            .addOnSuccessListener { roleDoc ->
                                if (roleDoc.exists()) {
                                    roleName = roleDoc.getString("role_name") ?: "User"
                                    binding.tvRank.text = roleName
                                } else {
                                    binding.tvRank.text = "User"
                                }
                                invalidateOptionsMenu()
                            }
                    }
                }
            }
    }
//    // 🌈 Hàm tạo hiệu ứng chuyển màu động cho vai trò
//    private fun animateRoleColor(roleName: String) {
//        val colorMap = mapOf(
//            "Admin" to Color.parseColor("#FF4C4C"),
//            "Owner" to Color.parseColor("#FFA500"),
//            "Lễ tân" to Color.parseColor("#4CAF50"),
//            "Dọn phòng" to Color.parseColor("#4CAF50"),
//            "User" to Color.parseColor("#BDBDBD")
//        )
//
//        val targetColor = colorMap[roleName] ?: Color.parseColor("#BDBDBD")
//        val currentColor = (binding.tvRank.background?.mutate() as? android.graphics.drawable.ColorDrawable)?.color ?: Color.WHITE
//
//        val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor)
//        colorAnim.duration = 600
//        colorAnim.addUpdateListener { animator ->
//            val color = animator.animatedValue as Int
//            binding.tvRank.setBackgroundColor(color)
//            binding.tvRank.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).withEndAction {
//                binding.tvRank.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
//            }.start()
//        }
//        colorAnim.start()
//    }

    // 🔹 Lấy số dư từ Firestore
    private fun loadWalletData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("wallets").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentBalance = doc.getLong("balance") ?: 0L
                    if (isBalanceVisible)
                        binding.tvBalance.text = formatMoney(currentBalance)
                    else
                        binding.tvBalance.text = "•••••••••"
                } else {
                    currentBalance = 0L
                    binding.tvBalance.text = formatMoney(0L)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi tải ví: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 🔹 Định dạng tiền tệ
    private fun formatMoney(amount: Long): String {
        return String.format("%,d VNĐ", amount).replace(",", ".")
    }
    // 🟢 Tạo menu 3 chấm
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (userRoleId == "userRoles/user") return false

        menuInflater.inflate(R.menu.menu_top_profile, menu)

        // Ẩn tất cả
        menu?.findItem(R.id.navAdmin)?.isVisible = false
        menu?.findItem(R.id.navChuKS)?.isVisible = false
        menu?.findItem(R.id.navLeTan)?.isVisible = false
        menu?.findItem(R.id.navDonPhong)?.isVisible = false

        // Hiển thị menu phù hợp role
        when (userRoleId) {
            "userRoles/admin" -> menu?.findItem(R.id.navAdmin)?.isVisible = true
            "userRoles/owner" -> menu?.findItem(R.id.navChuKS)?.isVisible = true
            "userRoles/staff" -> {
                menu?.findItem(R.id.navLeTan)?.isVisible = true
                menu?.findItem(R.id.navDonPhong)?.isVisible = true
            }
        }
        return true
    }

    // 🟣 Chọn menu
//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        when (item.itemId) {
//            R.id.navAdmin -> startActivity(Intent(this, AdminActivity::class.java))
//            R.id.navChuKS -> startActivity(Intent(this, ChuKhachSanActivity::class.java))
//            R.id.navLeTan -> startActivity(Intent(this, LeTanActivity::class.java))
//            R.id.navDonPhong -> startActivity(Intent(this, DonPhongActivity::class.java))
//        }
//        return super.onOptionsItemSelected(item)
//    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            loadUserData()
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }
}
