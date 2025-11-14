package com.tdc.nhom6.roomio.fragments

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.EditProfileActivity
import com.tdc.nhom6.roomio.activities.LoginActivity
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

    private var userRoleId: String = "user"
    private var roleName: String = "User"

    private var isBalanceVisible = true
    private var currentBalance: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ProfileLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.bottomNav.visibility = View.GONE
        binding.topAppBar.title = "Roomio"

        checkSession()
        setupActions()
        setupWalletToggle()
        updateRoleMenu()
    }

    private fun checkSession() {
        val firebaseUser = auth.currentUser
        val savedUid = prefs.getString("uid", null)

        if (firebaseUser == null && savedUid == null) {
            Toast.makeText(
                requireContext(),
                "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại",
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
            return
        }

        loadUserData()
        loadWalletData()
    }

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
        binding.showProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            prefs.edit().clear().apply()

            Toast.makeText(requireContext(), "Đã đăng xuất thành công", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }

        binding.topAppBar.setOnMenuItemClickListener {
            // TODO: Navigate to role-specific screens when available
            false
        }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: prefs.getString("uid", null) ?: return

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                if (doc.exists()) {
                    val user = doc.toObject(User::class.java)
                    if (user != null) {
                        binding.tvUsername.text = user.username.ifEmpty { "Người dùng" }

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

                        db.collection("userRoles").document(userRoleId)
                            .get()
                            .addOnSuccessListener { roleDoc ->
                                if (!isAdded || _binding == null) return@addOnSuccessListener
                                roleName =
                                    roleDoc.getString("role_name") ?: userRoleId.replaceFirstChar { it.uppercase() }
                                binding.tvRank.text = roleName
                                updateRoleUI(userRoleId)
                                animateRoleColor(userRoleId)
                                updateRoleMenu()
                            }
                            .addOnFailureListener {
                                if (!isAdded || _binding == null) return@addOnFailureListener
                                binding.tvRank.text = "User"
                                updateRoleUI("user")
                                animateRoleColor("user")
                                updateRoleMenu()
                            }
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Không tìm thấy thông tin người dùng",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                Toast.makeText(
                    requireContext(),
                    "Lỗi tải dữ liệu: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun loadWalletData() {
        val uid = auth.currentUser?.uid ?: prefs.getString("uid", null) ?: return
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                currentBalance = doc.getLong("balance") ?: 0L
                binding.tvBalance.text =
                    if (isBalanceVisible) formatMoney(currentBalance) else "•••••••••"
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                Toast.makeText(
                    requireContext(),
                    "Lỗi tải ví: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun updateRoleUI(roleId: String) {
        val colorRes = when (roleId.lowercase()) {
            "admin" -> R.color.red
            "owner" -> R.color.orange
            "letan" -> R.color.green
            "donphong" -> R.color.light_blue
            "xulydon" -> R.color.purple
            else -> R.color.gray
        }

        val bgColor = ContextCompat.getColor(requireContext(), colorRes)
        val shape = GradientDrawable().apply {
            cornerRadius = 25f
            setColor(bgColor)
        }
        binding.tvRank.background = shape

        binding.tvRank.animate()
            .scaleX(1.1f).scaleY(1.1f)
            .setDuration(250)
            .withEndAction {
                binding.tvRank.animate().scaleX(1f).scaleY(1f).duration = 250
            }
            .start()
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
        val bg = (binding.tvRank.background as? GradientDrawable) ?: GradientDrawable()
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

    private fun updateRoleMenu() {
        val menu = binding.topAppBar.menu
        menu.findItem(R.id.navAdmin)?.isVisible = false
        menu.findItem(R.id.navChuKS)?.isVisible = false
        menu.findItem(R.id.navLeTan)?.isVisible = false
        menu.findItem(R.id.navDonPhong)?.isVisible = false
        menu.findItem(R.id.navXuLy)?.isVisible = false

        when (userRoleId.lowercase()) {
            "admin" -> menu.findItem(R.id.navAdmin)?.isVisible = true
            "owner" -> menu.findItem(R.id.navChuKS)?.isVisible = true
            "letan" -> menu.findItem(R.id.navLeTan)?.isVisible = true
            "donphong" -> menu.findItem(R.id.navDonPhong)?.isVisible = true
            "xulydon" -> menu.findItem(R.id.navXuLy)?.isVisible = true
        }
    }

    private fun formatMoney(amount: Long): String {
        return String.format("%,d VNĐ", amount).replace(",", ".")
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            loadUserData()
            loadWalletData()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

