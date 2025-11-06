package com.tdc.nhom6.roomio.activities

import android.content.Intent
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

    }

    private fun loadLinkedBanks() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .collection("bank_info")
            .get()
            .addOnSuccessListener { result ->
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

                binding.recyclerLinkedBanks.adapter = LinkedBankAdapter(
                    banks,
                    onDelete = { deleteBank(it) },
                    onSetDefault = { setDefaultBank(it) }
                )
            }
    }

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
