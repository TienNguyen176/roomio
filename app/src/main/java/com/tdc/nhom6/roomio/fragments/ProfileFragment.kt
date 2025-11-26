package com.tdc.nhom6.roomio.fragments

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.admin.AdminHomeActivity
import com.tdc.nhom6.roomio.activities.auth.LoginActivity
import com.tdc.nhom6.roomio.activities.cleaner.CleanerActivity
import com.tdc.nhom6.roomio.activities.notification.NotificationsActivity
import com.tdc.nhom6.roomio.activities.owner.AdminHotelActivity
import com.tdc.nhom6.roomio.activities.owner.BusinessRegistrationActivity
import com.tdc.nhom6.roomio.activities.profile.EditProfileActivity
import com.tdc.nhom6.roomio.activities.profile.ManageBanksActivity
import com.tdc.nhom6.roomio.activities.receptionist.ReceptionActivity
import com.tdc.nhom6.roomio.databinding.ProfileLayoutBinding
import com.tdc.nhom6.roomio.models.User

class ProfileFragment : Fragment() {

    private var _binding: ProfileLayoutBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val prefs by lazy {
        requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    }

    private var isBalanceVisible = true
    private var currentBalance: Long = 0L
    private var userRoleId = "user"

    companion object {
        private val ROLE_COLORS = mapOf(
            "admin" to R.color.red,
            "owner" to R.color.orange,
            "letan" to R.color.green,
            "donphong" to R.color.light_blue,
            "xulydon" to R.color.purple,
            "user" to R.color.gray
        )

        private val MENU_MAPPING = mapOf(
            "admin" to R.id.navAdmin,
            "owner" to R.id.navChuKS,
            "letan" to R.id.navLeTan,
            "donphong" to R.id.navDonPhong,
            "xulydon" to R.id.navXuLy
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ProfileLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.bottomNav.visibility = View.GONE
        binding.topAppBar.title = "Roomio"

        checkSession()
        setupActions()
        setupWalletToggle()

        // Load realtime
        loadUserDataRealtime()
        loadWalletRealtime()
    }

    // -----------------------------
    // SESSION CHECK
    // -----------------------------
    private fun checkSession() {
        val firebaseUser = auth.currentUser
        val savedUid = prefs.getString("uid", null)

        if (firebaseUser == null && savedUid == null) {
            logoutForce()
        }
    }

    private fun logoutForce() {
        Toast.makeText(requireContext(), "Phiên đăng nhập hết hạn", Toast.LENGTH_SHORT).show()
        startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        requireActivity().finish()
    }

    // -----------------------------
    // REALTIME: WALLET
    // -----------------------------
    private fun loadWalletRealtime() {
        val uid = auth.currentUser?.uid ?: prefs.getString("uid", null) ?: return

        db.collection("users").document(uid)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists() || !isAdded) return@addSnapshotListener

                currentBalance = doc.getLong("walletBalance") ?: 0L
                binding.tvBalance.text =
                    if (isBalanceVisible) formatMoney(currentBalance) else "•••••••••"
            }
    }

    private fun setupWalletToggle() {
        binding.imgEye.setImageResource(R.drawable.ic_eye_show)

        binding.imgEye.setOnClickListener {
            isBalanceVisible = !isBalanceVisible

            binding.tvBalance.text =
                if (isBalanceVisible) formatMoney(currentBalance) else "•••••••••"

            binding.imgEye.setImageResource(
                if (isBalanceVisible) R.drawable.ic_eye_show else R.drawable.ic_eye_close
            )
        }
    }

    // -----------------------------
    // REALTIME: USER DATA
    // -----------------------------
    private fun loadUserDataRealtime() {
        val uid = auth.currentUser?.uid ?: prefs.getString("uid", null) ?: return

        db.collection("users").document(uid)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists() || !isAdded) return@addSnapshotListener

                val user = doc.toObject(User::class.java) ?: return@addSnapshotListener

                binding.tvUsername.text = user.username.ifEmpty { "Người dùng" }

                Glide.with(this)
                    .load(user.avatar.takeIf { it.isNotEmpty() } ?: R.drawable.user)
                    .circleCrop()
                    .into(binding.imgAvatar)

                userRoleId = user.roleId.ifEmpty { "user" }

                updateRoleRealtime(userRoleId)
            }
    }

    private fun updateRoleRealtime(roleId: String) {
        db.collection("userRoles").document(roleId)
            .addSnapshotListener { doc, _ ->
                if (!isAdded || doc == null) return@addSnapshotListener

                val roleName = doc.getString("role_name")
                    ?: roleId.replaceFirstChar { it.uppercase() }

                binding.tvRank.text = roleName

                updateRoleColor(roleId)
                updateRoleMenu()
            }
    }

    // -----------------------------
    // ROLE UI
    // -----------------------------
    private fun updateRoleColor(roleId: String) {
        val colorRes = ROLE_COLORS[roleId] ?: R.color.gray
        val targetColor = ContextCompat.getColor(requireContext(), colorRes)

        val bg = (binding.tvRank.background as? GradientDrawable)
            ?: GradientDrawable().apply { cornerRadius = 25f }

        val currentColor = bg.color?.defaultColor ?: targetColor

        ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor).apply {
            duration = 400
            addUpdateListener { animator ->
                bg.setColor(animator.animatedValue as Int)
            }
        }.start()

        binding.tvRank.background = bg
    }

    private fun updateRoleMenu() {
        val menu = binding.topAppBar.menu

        MENU_MAPPING.values.forEach { id ->
            menu.findItem(id)?.isVisible = false
        }

        MENU_MAPPING[userRoleId]?.let { menuId ->
            menu.findItem(menuId)?.isVisible = true
        }
    }

    // -----------------------------
    // ACTIONS
    // -----------------------------
    private fun setupActions() {
        binding.showDangKyKD.setOnClickListener {
            startActivity(Intent(requireContext(), BusinessRegistrationActivity::class.java))
        }

        binding.userBank.setOnClickListener {
            startActivity(Intent(requireContext(), ManageBanksActivity::class.java))
        }

        binding.showProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        binding.showThongBao.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationsActivity::class.java))
        }

        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            prefs.edit().clear().apply()

            Toast.makeText(requireContext(), "Đã đăng xuất", Toast.LENGTH_SHORT).show()
            logoutForce()
        }

        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.navAdmin -> startActivity(Intent(requireContext(), AdminHomeActivity::class.java))
                R.id.navChuKS -> startActivity(Intent(requireContext(), AdminHotelActivity::class.java))
                R.id.navLeTan -> startActivity(Intent(requireContext(), ReceptionActivity::class.java))
                R.id.navDonPhong -> startActivity(Intent(requireContext(), CleanerActivity::class.java))
                //R.id.navXuLy -> startActivity(Intent(requireContext(), AdminHotelActivity::class.java))
            }
            true
        }
    }

    private fun formatMoney(amount: Long): String =
        "%,d VNĐ".format(amount).replace(",", ".")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
