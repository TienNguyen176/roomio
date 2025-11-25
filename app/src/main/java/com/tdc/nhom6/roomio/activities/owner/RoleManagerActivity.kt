package com.tdc.nhom6.roomio.activities.owner

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.databinding.RolesHotelAdminLayoutBinding

class RoleManagerActivity : AppCompatActivity() {

    private lateinit var binding: RolesHotelAdminLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private var hotelId: String? = null
    private var selectedRole: String? = null

    private val roles = listOf("letan", "donphong", "xulydon")

    private fun getRoleMapping(role: String) = when (role) {
        "letan" -> Triple(binding.sectionReceptionistDetail, binding.btnEditReceptionist, binding.cbReceptionist)
        "donphong" -> Triple(binding.sectionCleanerDetail, binding.btnEditCleaner, binding.cbCleaner)
        "xulydon" -> Triple(binding.sectionPayerDetail, binding.btnEditPayer, binding.cbPayer)
        else -> null
    }

    private fun mapRoleToVietnamese(role: String): String {
        return when (role) {
            "letan" -> "Lễ tân"
            "donphong" -> "Dọn phòng"
            "xulydon" -> "Xử lý đơn"
            "user" -> "Nhân viên thường"
            else -> role
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RolesHotelAdminLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hotelId = intent.getStringExtra("hotelId")

        if (hotelId.isNullOrEmpty()) {
            Toast.makeText(this, "Không tìm thấy mã khách sạn!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRadioButtons()
        setupButtons()
        loadStaffs()
    }

    // ----------------------------------------------------------------------
    // HIỂN THỊ DANH SÁCH NHÂN VIÊN
    // ----------------------------------------------------------------------

    private fun loadStaffs() {
        val basePath = "hotels/$hotelId/staffs"

        roles.forEach { role ->
            db.collection(basePath).document(role)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        val accounts = doc.get("staffUids") as? List<String> ?: emptyList()
                        showStaffList(role, accounts)
                    } else {
                        showStaffList(role, emptyList())
                    }
                }
                .addOnFailureListener {
                    Log.e("RoleManager", "Lỗi tải $role", it)
                }
        }
    }

    private fun showStaffList(role: String, uids: List<String>) {
        val layout = getRoleMapping(role)?.first ?: return
        layout.removeAllViews()
        layout.visibility = View.GONE

        if (uids.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "Chưa có nhân viên"
                textSize = 14f
                setPadding(8, 8, 8, 8)
            }
            layout.addView(tvEmpty)
            return
        }

        uids.forEach { uid ->
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener { userDoc ->
                    val email = userDoc.getString("email") ?: "Không rõ email"
                    val username = userDoc.getString("username") ?: uid

                    val itemRow = LinearLayout(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(8, 8, 8, 8)
                    }

                    val tvName = TextView(this).apply {
                        text = "• $username ($email)"
                        textSize = 15f
                        layoutParams = LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                        )
                    }
                    itemRow.addView(tvName)

                    val tvAction = TextView(this).apply {
                        text = "SỬA/XÓA"
                        textSize = 14f
                        setTextColor(0xFF2F6FD1.toInt())
                        setPadding(16, 0, 0, 0)
                        setOnClickListener { showStaffActionDialog(uid, username, role) }
                    }
                    itemRow.addView(tvAction)
                    layout.addView(itemRow)
                }
        }
    }

    // ----------------------------------------------------------------------
    // HÀNH ĐỘNG: XÓA / CHUYỂN VAI TRÒ
    // ----------------------------------------------------------------------

    private fun showStaffActionDialog(userId: String, username: String, currentRole: String) {
        val options = arrayOf("Xóa nhân viên", "Chuyển vai trò")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Hành động cho $username")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmRemoveStaff(userId, username, currentRole)
                    1 -> showRoleChangeDialog(userId, username, currentRole)
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmRemoveStaff(userId: String, username: String, role: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Xóa $username khỏi vai trò ${mapRoleToVietnamese(role)}?")
            .setPositiveButton("Xóa") { _, _ ->
                removeStaffCompletely(userId, username, role)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showRoleChangeDialog(userId: String, username: String, currentRole: String) {
        val availableRoles = roles + listOf("user")
        val displayRoles = availableRoles.map { mapRoleToVietnamese(it) }.toTypedArray()

        var selectedIndex = availableRoles.indexOf(currentRole)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Chuyển vai trò cho $username")
            .setSingleChoiceItems(displayRoles, selectedIndex) { _, which -> selectedIndex = which }
            .setPositiveButton("Xác nhận") { dialog, _ ->
                val newRole = availableRoles[selectedIndex]
                if (newRole != currentRole) {
                    removeStaffFromListOnly(currentRole, userId) {
                        addStaffToListOnly(newRole, userId, username)
                    }
                } else {
                    Toast.makeText(this, "Vai trò không thay đổi.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ----------------------------------------------------------------------
    // THÊM / XÓA / CẬP NHẬT NHÂN VIÊN
    // ----------------------------------------------------------------------

    private fun addStaff(email: String, role: String) {
        val currentHotelId = hotelId!!

        db.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "Không tìm thấy nhân viên với email này", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val userDoc = result.documents.first()
                val userId = userDoc.id
                val username = userDoc.getString("username") ?: email
                val userRole = userDoc.getString("roleId") ?: "user"
                val userHotelId = userDoc.getString("hotelId")

                if (userRole == "admin" || userRole == "owner") {
                    Toast.makeText(this, "Không thể thêm ${mapRoleToVietnamese(userRole)}.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                if (!userHotelId.isNullOrEmpty() && userHotelId != currentHotelId) {
                    Toast.makeText(this, "Nhân viên này đã thuộc khách sạn khác.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                addStaffToListOnly(role, userId, username)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi tìm nhân viên: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addStaffToListOnly(role: String, userId: String, username: String) {
        val currentHotelId = hotelId!!
        if (role == "user") {
            updateUserRoleAndHotel(userId, "user", null, username)
            return
        }

        // 🧩 1. Kiểm tra xem nhân viên đã nằm trong vai trò khác chưa
        val staffsCollection = db.collection("hotels").document(currentHotelId).collection("staffs")

        staffsCollection.get()
            .addOnSuccessListener { snapshot ->
                var alreadyExistsInRole: String? = null

                for (doc in snapshot.documents) {
                    val staffList = doc.get("staffUids") as? List<String> ?: emptyList()
                    if (staffList.contains(userId)) {
                        alreadyExistsInRole = doc.id
                        break
                    }
                }

                if (alreadyExistsInRole != null) {
                    val viet = mapRoleToVietnamese(alreadyExistsInRole)
                    Toast.makeText(
                        this,
                        "Nhân viên này đã là $viet trong khách sạn, không thể thêm vào vai trò khác.",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnAddRole.isEnabled = true
                    return@addOnSuccessListener
                }

                // 🧩 2. Nếu chưa có trong vai trò nào → thêm mới
                val roleDocRef = staffsCollection.document(role)
                roleDocRef.update("staffUids", FieldValue.arrayUnion(userId))
                    .addOnSuccessListener {
                        updateUserRoleAndHotel(userId, role, currentHotelId, username)
                    }
                    .addOnFailureListener {
                        val newData = mapOf("staffUids" to listOf(userId))
                        roleDocRef.set(newData)
                            .addOnSuccessListener {
                                updateUserRoleAndHotel(userId, role, currentHotelId, username)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Lỗi thêm vào danh sách: ${e.message}", Toast.LENGTH_SHORT).show()
                                binding.btnAddRole.isEnabled = true
                            }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi kiểm tra vai trò: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.btnAddRole.isEnabled = true
            }
    }



    private fun removeStaffCompletely(userId: String, username: String, role: String) {
        val currentHotelId = hotelId!!
        val roleDocRef = db.collection("hotels").document(currentHotelId)
            .collection("staffs").document(role)

        roleDocRef.update("staffUids", FieldValue.arrayRemove(userId))
            .addOnSuccessListener {
                updateUserRoleAndHotel(userId, "user", null, username)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi khi xóa nhân viên.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun removeStaffFromListOnly(role: String, userId: String, onComplete: () -> Unit) {
        if (role == "user") {
            onComplete()
            return
        }
        val currentHotelId = hotelId!!
        val roleDocRef = db.collection("hotels").document(currentHotelId)
            .collection("staffs").document(role)

        roleDocRef.update("staffUids", FieldValue.arrayRemove(userId))
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener {
                Log.e("RoleManager", "Lỗi xóa khỏi list $role", it)
                onComplete()
            }
    }

    private fun updateUserRoleAndHotel(userId: String, role: String, hotelId: String?, username: String) {
        val updates = mutableMapOf<String, Any>("roleId" to role)
        if (hotelId != null) {
            updates["hotelId"] = hotelId
        } else {
            updates["hotelId"] = FieldValue.delete()
        }

        db.collection("users").document(userId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Đã cập nhật $username sang ${mapRoleToVietnamese(role)}", Toast.LENGTH_SHORT).show()
                binding.edtAccount.text?.clear()
                loadStaffs()
            }
    }

    // ----------------------------------------------------------------------
    // UI
    // ----------------------------------------------------------------------

    private fun setEditButtonsEnabled(enable: Boolean, excludeButton: View? = null) {
        binding.btnEditReceptionist.isEnabled = enable || (binding.btnEditReceptionist == excludeButton)
        binding.btnEditCleaner.isEnabled = enable || (binding.btnEditCleaner == excludeButton)
        binding.btnEditPayer.isEnabled = enable || (binding.btnEditPayer == excludeButton)
    }

    private fun toggleSection(section: LinearLayout, button: View) {
        val visible = section.visibility == View.VISIBLE
        if (visible) {
            section.visibility = View.GONE
            setEditButtonsEnabled(true)
        } else {
            binding.sectionReceptionistDetail.visibility = View.GONE
            binding.sectionCleanerDetail.visibility = View.GONE
            binding.sectionPayerDetail.visibility = View.GONE
            section.visibility = View.VISIBLE
            setEditButtonsEnabled(false, button)
        }
    }

    private fun setupButtons() = with(binding) {
        setEditButtonsEnabled(true)

        btnEditReceptionist.setOnClickListener {
            toggleSection(sectionReceptionistDetail, btnEditReceptionist)
        }
        btnEditCleaner.setOnClickListener {
            toggleSection(sectionCleanerDetail, btnEditCleaner)
        }
        btnEditPayer.setOnClickListener {
            toggleSection(sectionPayerDetail, btnEditPayer)
        }

        btnAddRole.setOnClickListener {
            val email = edtAccount.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this@RoleManagerActivity, "Vui lòng nhập email nhân viên", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedRole == null) {
                Toast.makeText(this@RoleManagerActivity, "Vui lòng chọn vai trò", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addStaff(email, selectedRole!!)
        }

        btnConfirm.setOnClickListener {
            Toast.makeText(this@RoleManagerActivity, "Cập nhật hoàn tất", Toast.LENGTH_SHORT).show()
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun setupRadioButtons() = with(binding) {
        val radios = listOf(cbReceptionist, cbCleaner, cbPayer)

        cbReceptionist.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                radios.filter { it != cbReceptionist }.forEach { it.isChecked = false }
                selectedRole = "letan"
            }
        }
        cbCleaner.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                radios.filter { it != cbCleaner }.forEach { it.isChecked = false }
                selectedRole = "donphong"
            }
        }
        cbPayer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                radios.filter { it != cbPayer }.forEach { it.isChecked = false }
                selectedRole = "xulydon"
            }
        }
    }
}
