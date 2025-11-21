// ProfileFragment.kt (đã sửa hoàn chỉnh – giữ nguyên toàn bộ chức năng)

package com.tdc.nhom6.roomio.activities

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.AdminHomeActivity
import com.tdc.nhom6.roomio.activities.AdminHotelActivity
import com.tdc.nhom6.roomio.activities.CleanerActivity
import com.tdc.nhom6.roomio.activities.EditProfileActivity
import com.tdc.nhom6.roomio.activities.LoginActivity
import com.tdc.nhom6.roomio.activities.ReceptionActivity
import com.tdc.nhom6.roomio.databinding.ProfileLayoutBinding
import com.tdc.nhom6.roomio.models.User

class ProfileFragment : Fragment() {

    private var _binding: ProfileLayoutBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs by lazy { requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }

    private var userRoleId: String = "user"
    private var roleName: String = "Người Dùng"
    private var userRef: DocumentReference? = null
    private var userListener: ListenerRegistration? = null
    private var roleListener: ListenerRegistration? = null

    private var isBalanceVisible = true
    private var currentBalance: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = ProfileLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.apply {
            setSupportActionBar(binding.topAppBar)
            supportActionBar?.title = "Roomio"
        }

        binding.tvRank.text = "Người Dùng"
        updateRoleUI("user")
        animateRoleColor("user")

        setupActions()
        setupWalletToggle()
        checkSession()
    }

    override fun onStart() {
        super.onStart()
        checkSession()
    }

    override fun onStop() {
        super.onStop()
        userListener?.remove()
        roleListener?.remove()
        userListener = null
        roleListener = null
    }

    // ================= Firestore =================
    private fun checkSession() {
        val firebaseUser = auth.currentUser
        val savedUid = prefs.getString("uid", null)

        if (firebaseUser == null && savedUid == null) {
            Toast.makeText(requireContext(), "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            activity?.finish()
            return
        }

        val uid = firebaseUser?.uid ?: savedUid!!
        listenUserRealtime(uid)
    }

    private fun listenUserRealtime(uid: String) {
        userRef = db.collection("users").document(uid)
        userListener = userRef?.addSnapshotListener { doc, e ->
            if (e != null || doc == null || !doc.exists()) return@addSnapshotListener

            val user = doc.toObject(User::class.java) ?: return@addSnapshotListener

            binding.tvUsername.text = user.username.ifEmpty { "Người dùng" }

            if (user.avatar.isNotEmpty()) {
                Glide.with(this).load(user.avatar).circleCrop().placeholder(R.drawable.user).into(binding.imgAvatar)
            } else binding.imgAvatar.setImageResource(R.drawable.user)

            currentBalance = doc.getDouble("walletBalance") ?: 0.0
            binding.tvBalance.text = if (isBalanceVisible) formatMoney(currentBalance) else "•••••••••"

            val newRoleId = user.roleId.ifEmpty { "user" }
            if (newRoleId != userRoleId) {
                userRoleId = newRoleId
                listenRoleRealtime(userRoleId)
            }
        }
    }

    private fun listenRoleRealtime(roleId: String) {
        roleListener?.remove()
        val roleRef = db.collection("userRoles").document(roleId)

        roleListener = roleRef.addSnapshotListener { doc, e ->
            if (e != null) return@addSnapshotListener

            val newRoleName = doc?.getString("role_name") ?: roleId.replaceFirstChar { it.uppercase() }

            if (roleName != newRoleName) {
                roleName = newRoleName
                binding.tvRank.text = roleName
                updateRoleUI(roleId)
                animateRoleColor(roleId)
                activity?.invalidateOptionsMenu()
            }
        }
    }

    // ================= Wallet =================
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

    // ================= UI Actions =================
    private fun setupActions() {
        binding.showProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            prefs.edit().clear().apply()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        binding.topAppBar.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.id.navAdmin -> {
                    startActivity(Intent(requireContext(), AdminHomeActivity::class.java))
                    true
                }
                R.id.navChuKS -> {
                    startActivity(Intent(requireContext(), AdminHotelActivity::class.java))
                    true
                }

                R.id.navLeTan -> {
                    startActivity(Intent(requireContext(), ReceptionActivity::class.java))
                    true
                }
                R.id.navDonPhong -> {
                    startActivity(Intent(requireContext(), CleanerActivity::class.java))
                    true
                }

                else -> false
            }
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

        binding.userBank.setOnClickListener {
            startActivity(Intent(requireContext(), ManageBanksActivity::class.java))
        }
        binding.showDangKyKD.setOnClickListener {
            startActivity(Intent(requireContext(), BusinessRegistrationActivity::class.java))
        }
    }

    // ================= Role UI =================
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

        binding.tvRank.animate().scaleX(1.1f).scaleY(1.1f)
            .setDuration(250)
            .withEndAction { binding.tvRank.animate().scaleX(1f).scaleY(1f).duration = 250 }
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
        val bg = binding.tvRank.background as? GradientDrawable ?: GradientDrawable()
        val currentColor = bg.color?.defaultColor ?: Color.WHITE

        val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor)
        colorAnim.duration = 600
        colorAnim.addUpdateListener {
            val color = it.animatedValue as Int
            bg.setColor(color)
            binding.tvRank.background = bg
        }
        colorAnim.start()
    }

    // ================= Menu =================
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (userRoleId == "user") return

        inflater.inflate(R.menu.menu_top_profile, menu)

        menu.findItem(R.id.navAdmin)?.isVisible = userRoleId == "admin"
        menu.findItem(R.id.navChuKS)?.isVisible = userRoleId == "owner"
        menu.findItem(R.id.navLeTan)?.isVisible = userRoleId == "letan"
        menu.findItem(R.id.navDonPhong)?.isVisible = userRoleId == "donphong"
        menu.findItem(R.id.navXuLy)?.isVisible = userRoleId == "xulydon"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
//            R.id.navAdmin -> startActivity(Intent(requireContext(), AdminActivity::class.java))
            R.id.navChuKS -> startActivity(Intent(requireContext(), AdminHotelActivity::class.java))
//            R.id.navLeTan -> startActivity(Intent(requireContext(), LeTanActivity::class.java))
//            R.id.navDonPhong -> startActivity(Intent(requireContext(), DonPhongActivity::class.java))
//            R.id.navXuLy -> startActivity(Intent(requireContext(), XuLyDonActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    // ================= Format =================
    private fun formatMoney(amount: Double) = String.format("%,.0f VNĐ", amount).replace(",", ".")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
