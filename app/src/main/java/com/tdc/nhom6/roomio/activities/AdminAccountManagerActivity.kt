package com.tdc.nhom6.roomio.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.AccountAdapter
import com.tdc.nhom6.roomio.databinding.AdminAccountManagerLayoutBinding
import com.tdc.nhom6.roomio.databinding.DialogEditRoleBinding
import com.tdc.nhom6.roomio.models.AccountModel

class AdminAccountManagerActivity : AppCompatActivity() {

    private lateinit var binding: AdminAccountManagerLayoutBinding
    private lateinit var accountAdapter: AccountAdapter
    private val accountList = mutableListOf<AccountModel>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminAccountManagerLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Danh sách tài khoản"


        // Khởi tạo adapter
        accountAdapter = AccountAdapter(accountList) { account ->
            showEditRoleDialog(account)
        }

        binding.rcvAccounts.apply {
            layoutManager = LinearLayoutManager(this@AdminAccountManagerActivity)
            adapter = accountAdapter
        }

        loadUsersFromFirebase()
    }

    private fun loadUsersFromFirebase() {
        db.collection("users")
            .get()
            .addOnSuccessListener { documents ->
                val tempList = mutableListOf<AccountModel>()
                for (doc in documents) {
                    val userId = doc.id
                    val fullName = doc.getString("username") ?: ""
                    val roleId = doc.getString("roleId") ?: "user"
                    val avatarUrl = doc.getString("avatar") ?: ""

                    tempList.add(AccountModel(userId, fullName, avatarUrl, roleId))
                }
                accountAdapter.submitList(tempList)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi tải dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditRoleDialog(account: AccountModel) {
        val dialogBinding = DialogEditRoleBinding.inflate(layoutInflater)

        // Load avatar và username
        Glide.with(this)
            .load(account.avatarUrl)
            .placeholder(R.drawable.ic_not_image)
            .circleCrop()
            .into(dialogBinding.imgDialogAvatar)

        dialogBinding.tvDialogUsername.text = account.fullName

        // Lấy danh sách role từ Firestore
        db.collection("userRoles").get().addOnSuccessListener { snapshot ->
            val roles = mutableListOf<String>()
            val roleNames = mutableListOf<String>()
            var currentIndex = 0

            for ((index, doc) in snapshot.documents.withIndex()) {
                val roleId = doc.id
                val roleName = doc.getString("role_name") ?: roleId
                roles.add(roleId)
                roleNames.add(roleName)
                if (roleId == account.roleId) currentIndex = index
            }

            // Set adapter cho spinner
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roleNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            dialogBinding.spinnerDialogRole.adapter = spinnerAdapter
            dialogBinding.spinnerDialogRole.setSelection(currentIndex)

            // Tạo AlertDialog
            val dialog = AlertDialog.Builder(this)
                .setTitle("Chỉnh sửa role")
                .setView(dialogBinding.root)
                .setPositiveButton("Cập nhật", null)  // xử lý bên dưới
                .setNegativeButton("Hủy") { d, _ -> d.dismiss() }
                .create()

            dialog.setOnShowListener {
                dialogBinding.spinnerDialogRole.post {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val selectedIndex = dialogBinding.spinnerDialogRole.selectedItemPosition
                        val selectedRoleId = roles[selectedIndex]

                        // Kiểm tra quyền
                        if (selectedRoleId !in listOf("admin", "owner", "user")) {
                            Toast.makeText(
                                this,
                                "Không thể cập nhật quyền này! \nChỉ dành cho chủ khách sạn.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        AlertDialog.Builder(this)
                            .setTitle("Xác nhận")
                            .setMessage("Bạn có chắc muốn cập nhật role cho ${account.fullName} không?")
                            .setPositiveButton("Đồng ý") { _, _ ->
                                // Update Firestore
                                db.collection("users").document(account.userId)
                                    .update("roleId", selectedRoleId)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Cập nhật role thành công", Toast.LENGTH_SHORT).show()
                                        accountAdapter.updateItemRole(account.userId, selectedRoleId)
                                        dialog.dismiss()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Cập nhật thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            .setNegativeButton("Hủy", null)
                            .show()
                    }
                }
            }

            dialog.show()

        }.addOnFailureListener { e ->
            Toast.makeText(this, "Lỗi tải danh sách role: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}