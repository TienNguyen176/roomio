package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.adapters.LinkedBankAdapter
import com.tdc.nhom6.roomio.databinding.ManageBanksLayoutBinding
import com.tdc.nhom6.roomio.models.Bank

class ManageBanksActivity : AppCompatActivity() {

    private lateinit var binding: ManageBanksLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val banks = mutableListOf<Bank>()
    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ManageBanksLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerLinkedBanks.layoutManager = LinearLayoutManager(this)

        loadLinkedBanks()

        binding.btnAddBank.setOnClickListener {
            startActivity(Intent(this, LinkBankActivity::class.java))
        }
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.edtEdit.setOnClickListener {
            isEditing = !isEditing

            (binding.recyclerLinkedBanks.adapter as? LinkedBankAdapter)
                ?.setEditing(isEditing)

            if (isEditing) {
                binding.edtEdit.text = "Save"
                binding.edtEdit.setTextColor(Color.parseColor("#E53935"))
            } else {
                binding.edtEdit.text = "Edit"
                binding.edtEdit.setTextColor(Color.parseColor("#E53935"))
                saveAllChanges()
            }
        }



    }

    private fun loadLinkedBanks() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .collection("bank_info")
            .addSnapshotListener { result, e ->
                if (e != null || result == null) return@addSnapshotListener
                banks.clear()
                for (doc in result) {
                    val bank = Bank(
                        id = doc.id,
                        name = doc.getString("bank_name") ?: "",
                        accountNumber = doc.getString("account_number") ?: "",
                        logoUrl = doc.getString("logo_url") ?: "",
                        isDefault = doc.getBoolean("default") ?: false
                    )
                    banks.add(bank)
                }

                val adapter = LinkedBankAdapter(
                    banks,
                    onDelete = { deleteBank(it) },
                    onSetDefault = { setDefaultBank(it) }
                )

                binding.recyclerLinkedBanks.adapter = adapter
                adapter.setEditing(isEditing)


            }

    }

    //edit
    private fun saveAllChanges() {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("users").document(uid).collection("bank_info")

        for (bank in banks) {
            if (bank.id.isNotEmpty()) {
                val updates = mapOf(
                    "bank_name" to bank.name,
                    "account_number" to bank.accountNumber
                )
                ref.document(bank.id).update(updates)
            }
        }

        Toast.makeText(this, "Đã lưu thay đổi", Toast.LENGTH_SHORT).show()
    }

    //destroy
    private fun deleteBank(bank: Bank) {
        val uid = auth.currentUser?.uid ?: return
        if (bank.id.isEmpty()) return

        db.collection("users").document(uid)
            .collection("bank_info").document(bank.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Đã xóa ngân hàng ${bank.name}", Toast.LENGTH_SHORT).show()
                loadLinkedBanks()
            }
    }

    //them
    private fun setDefaultBank(bank: Bank) {
        val uid = auth.currentUser?.uid ?: return
        if (bank.id.isEmpty()) return

        val ref = db.collection("users").document(uid).collection("bank_info")

        ref.get().addOnSuccessListener { result ->
            for (doc in result) {
                ref.document(doc.id).update("default", false)
            }

            ref.document(bank.id).update("default", true)
                .addOnSuccessListener {
                    Toast.makeText(this, "Đã chọn ${bank.name} làm tài khoản chính", Toast.LENGTH_SHORT).show()
                    loadLinkedBanks()
                }
        }
    }
}
