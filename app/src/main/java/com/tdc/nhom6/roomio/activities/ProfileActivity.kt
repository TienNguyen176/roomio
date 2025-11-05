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
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ProfileLayoutBinding
import com.tdc.nhom6.roomio.models.User

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ProfileLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs by lazy { getSharedPreferences("user_prefs", MODE_PRIVATE) }

    private var userRoleId: String = "user"
    private var roleName: String = "User"

    private var userRef: DocumentReference? = null
    private var userListener: ListenerRegistration? = null
    private var roleListener: ListenerRegistration? = null

    private var isBalanceVisible = true
    private var currentBalance: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProfileLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.title = "Roomio"

        checkSession()
        setupActions()
        setupWalletToggle()

        // Hiển thị mặc định trước khi dữ liệu Firestore load
        binding.tvRank.text = "User"
        updateRoleUI("user")
        animateRoleColor("user")

    }

    override fun onStop() {
        super.onStop()
        userListener?.remove()
        roleListener?.remove()
    }

    // Kiểm tra phiên đăng nhập
    private fun checkSession() {
        val firebaseUser = auth.currentUser
        val savedUid = prefs.getString("uid", null)

        if (firebaseUser == null && savedUid == null) {
            Toast.makeText(this, "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val uid = firebaseUser?.uid ?: savedUid!!
        listenUserRealtime(uid)
    }

    // Lắng nghe realtime thay đổi user (avatar, username, roleId, walletBalance)
    private fun listenUserRealtime(uid: String) {
        userRef = db.collection("users").document(uid)

        userListener = userRef?.addSnapshotListener { doc, e ->
            if (e != null || doc == null || !doc.exists()) return@addSnapshotListener

            val user = doc.toObject(User::class.java) ?: return@addSnapshotListener

            // 🧍 Username
            binding.tvUsername.text = user.username.ifEmpty { "Người dùng" }

            ///️ Avatar
            if (user.avatar.isNotEmpty()) {
                Glide.with(this)
                    .load(user.avatar)
                    .circleCrop()
                    .placeholder(R.drawable.user)
                    .into(binding.imgAvatar)
            } else {
                binding.imgAvatar.setImageResource(R.drawable.user)
            }

            // Wallet realtime
            currentBalance = doc.getDouble("balance") ?: 0.0
            binding.tvBalance.text =
                if (isBalanceVisible) formatMoney(currentBalance) else "•••••••••"

            // Role realtime
            val newRoleId = user.roleId.ifEmpty { "user" }
            if (newRoleId != userRoleId) {
                userRoleId = newRoleId
                listenRoleRealtime(userRoleId)
            }
        }
    }

    // Lắng nghe realtime thay đổi role
    private fun listenRoleRealtime(roleId: String) {
        roleListener?.remove()
        val roleRef = db.collection("userRoles").document(roleId)

        roleListener = roleRef.addSnapshotListener { doc, e ->
            if (e != null) return@addSnapshotListener

            roleName = doc?.getString("role_name") ?: roleId.replaceFirstChar { it.uppercase() }
            binding.tvRank.text = roleName
            updateRoleUI(roleId)
            animateRoleColor(roleId)
            invalidateOptionsMenu()
        }
    }

    // Ẩn / hiện số dư ví
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

    // Các nút bấm
    private fun setupActions() {
        binding.showProfile.setOnClickListener {
            startActivityForResult(Intent(this, EditProfileActivity::class.java), 100)
        }

        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            prefs.edit().clear().apply()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // Cập nhật giao diện vai trò
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

        // Hiệu ứng nhún
        binding.tvRank.animate()
            .scaleX(1.1f).scaleY(1.1f)
            .setDuration(250)
            .withEndAction {
                binding.tvRank.animate().scaleX(1f).scaleY(1f).duration = 250
            }
            .start()
    }

    // Hiệu ứng màu mượt cho role
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
        val bg = binding.tvRank.background as? GradientDrawable ?: GradientDrawable()
        val currentColor = bg.color?.defaultColor ?: Color.WHITE

        val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor)
        colorAnim.duration = 600
        colorAnim.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            bg.setColor(color)
            binding.tvRank.background = bg
        }
        colorAnim.start()
    }

    // Format tiền
// Format tiền chuẩn có dấu . ngăn cách và hậu tố VNĐ
    private fun formatMoney(amount: Double): String {
        return String.format("%,.0f VNĐ", amount).replace(",", ".")
    }


    // Menu theo role
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
            // Realtime tự cập nhật nên không cần reload thủ công
        }
    }
}
