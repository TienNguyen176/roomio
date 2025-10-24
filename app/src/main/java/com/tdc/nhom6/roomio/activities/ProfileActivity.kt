package com.tdc.nhom6.roomio.activities

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ProfileLayoutBinding
import com.tdc.nhom6.roomio.models.User

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ProfileLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var userRoleId: String = "user"
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
        setupWalletToggle()
        loadUserData()
        loadWalletData()
    }

    // 👁 Ẩn / hiện số dư ví
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

    // ⚙️ Hành động nút
    private fun setupActions() {
        binding.showProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivityForResult(intent, 100)
        }

        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // 📥 Tải dữ liệu user
    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val user = doc.toObject(User::class.java)
                    if (user != null) {
                        binding.tvUsername.text = user.username.ifEmpty { "Người dùng" }

                        // Ảnh đại diện
                        if (user.avatar.isNotEmpty()) {
                            Glide.with(this)
                                .load(user.avatar)
                                .circleCrop()
                                .placeholder(R.drawable.user)
                                .into(binding.imgAvatar)
                        } else {
                            binding.imgAvatar.setImageResource(R.drawable.user)
                        }

                        userRoleId = user.roleId.ifEmpty { "user" }

                        // Lấy thông tin role từ Firestore
                        db.collection("userRoles").document(userRoleId)
                            .get()
                            .addOnSuccessListener { roleDoc ->
                                if (roleDoc.exists()) {
                                    roleName = roleDoc.getString("role_name") ?: userRoleId.capitalize()
                                } else {
                                    roleName = userRoleId.capitalize()
                                }
                                binding.tvRank.text = roleName
                                updateRoleUI(userRoleId)
                                animateRoleColor(userRoleId)
                                invalidateOptionsMenu()
                            }
                            .addOnFailureListener {
                                binding.tvRank.text = "User"
                                updateRoleUI("user")
                                animateRoleColor("user")
                            }
                    }
                }
            }
    }

    // 🎨 Cập nhật UI rank (màu + bo góc + hiệu ứng)
    private fun updateRoleUI(roleId: String) {
        val colorRes = when (roleId.lowercase()) {
            "admin" -> R.color.red
            "owner" -> R.color.orange
            "letan" -> R.color.green
            "donphong" -> R.color.light_blue
            "xulydon" -> R.color.purple
            else -> R.color.gray
        }

        val bgColor = ContextCompat.getColor(this, colorRes)
        val shape = GradientDrawable().apply {
            cornerRadius = 25f
            setColor(bgColor)
        }
        binding.tvRank.background = shape

        // Hiệu ứng phóng nhẹ
        binding.tvRank.animate()
            .scaleX(1.1f).scaleY(1.1f)
            .setDuration(250)
            .withEndAction {
                binding.tvRank.animate().scaleX(1f).scaleY(1f).duration = 250
            }
            .start()
    }

    // 🌈 Hiệu ứng chuyển màu mượt
    private fun animateRoleColor(roleId: String) {
        val colorMap = mapOf(
            "admin" to Color.parseColor("#FF4C4C"),   // đỏ
            "owner" to Color.parseColor("#FFA500"),   // cam
            "letan" to Color.parseColor("#4CAF50"),   // xanh lá
            "donphong" to Color.parseColor("#03A9F4"), // xanh da trời
            "xulydon" to Color.parseColor("#9C27B0"), // tím
            "user" to Color.parseColor("#BDBDBD")     // xám
        )

        val targetColor = colorMap[roleId.lowercase()] ?: Color.parseColor("#BDBDBD")
        val bg = binding.tvRank.background as? GradientDrawable ?: GradientDrawable()
        val currentColor = (bg.color?.defaultColor ?: Color.WHITE)

        val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor)
        colorAnim.duration = 600
        colorAnim.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            bg.setColor(color)
            binding.tvRank.background = bg
        }
        colorAnim.start()
    }

    // 💰 Tải số dư ví
    private fun loadWalletData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("wallets").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentBalance = doc.getLong("balance") ?: 0L
                } else {
                    currentBalance = 0L
                }
                binding.tvBalance.text =
                    if (isBalanceVisible) formatMoney(currentBalance) else "•••••••••"
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi tải ví: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun formatMoney(amount: Long): String {
        return String.format("%,d VNĐ", amount).replace(",", ".")
    }

    // 📋 Tạo menu quyền
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (userRoleId == "user") return false

        menuInflater.inflate(R.menu.menu_top_profile, menu)
        menu?.findItem(R.id.navAdmin)?.isVisible = false
        menu?.findItem(R.id.navChuKS)?.isVisible = false
        menu?.findItem(R.id.navLeTan)?.isVisible = false
        menu?.findItem(R.id.navDonPhong)?.isVisible = false
        menu?.findItem(R.id.navXuLy)?.isVisible = false

        when (userRoleId) {
            "admin" -> menu?.findItem(R.id.navAdmin)?.isVisible = true
            "owner" -> menu?.findItem(R.id.navChuKS)?.isVisible = true
            "letan" -> menu?.findItem(R.id.navLeTan)?.isVisible = true
            "donphong" -> menu?.findItem(R.id.navDonPhong)?.isVisible = true
            "xulydon" -> menu?.findItem(R.id.navXuLy)?.isVisible = true
        }
        return true
    }

//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        when (item.itemId) {
//            R.id.navAdmin -> startActivity(Intent(this, AdminActivity::class.java))
//            R.id.navChuKS -> startActivity(Intent(this, ChuKhachSanActivity::class.java))
//            R.id.navLeTan -> startActivity(Intent(this, LeTanActivity::class.java))
//            R.id.navDonPhong -> startActivity(Intent(this, DonPhongActivity::class.java))
//            R.id.navXuLy -> startActivity(Intent(this, XuLyDonActivity::class.java))
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
