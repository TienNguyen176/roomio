package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.BankAdapter
import com.tdc.nhom6.roomio.api.RetrofitClient
import com.tdc.nhom6.roomio.databinding.LinkBankLayoutBinding
import com.tdc.nhom6.roomio.models.Bank
import com.tdc.nhom6.roomio.models.BankResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class LinkBankActivity : AppCompatActivity() {

    private lateinit var binding: LinkBankLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var selectedBank: Bank? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LinkBankLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.layoutSelectBank.setOnClickListener { showBankList() }
        binding.btnLinkBank.setOnClickListener { linkBankAccount() }
    }

    private fun showBankList() {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottomsheet_select_bank, null)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerBanks)
        val edtSearch = view.findViewById<EditText>(R.id.edtSearchBank)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        recycler.layoutManager = LinearLayoutManager(this)
        progressBar.visibility = View.VISIBLE

        RetrofitClient.instance.getBanks().enqueue(object : Callback<BankResponse> {
            override fun onResponse(call: Call<BankResponse>, response: Response<BankResponse>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    val banks = response.body()?.data?.map {
                        // map API -> Bank model (sửa theo cấu trúc API)
                        Bank(
                            id = it.bin.ifEmpty { it.shortName }, // ưu tiên bin, fallback shortName
                            name = it.shortName.ifEmpty { it.bin },
                            logoUrl = it.logo
                        )
                    } ?: emptyList()

                    val adapter = BankAdapter(banks) { bank ->
                        selectedBank = bank
                        binding.tvBankName.text = bank.name
                        Glide.with(this@LinkBankActivity).load(bank.logoUrl).into(binding.imgBankLogo)
                        bottomSheet.dismiss()
                    }

                    recycler.adapter = adapter

                    edtSearch.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            adapter.filter.filter(s)
                        }
                        override fun afterTextChanged(s: Editable?) {}
                    })
                } else {
                    Toast.makeText(this@LinkBankActivity, "Không tải được danh sách ngân hàng", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<BankResponse>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@LinkBankActivity, "Lỗi: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })

        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    private fun linkBankAccount() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show()
            return
        }

        val bank = selectedBank
        val accNum = binding.edtAccountNumber.text.toString().trim()
        val accHolder = binding.edtAccountHolder.text.toString().trim()

        if (bank == null) {
            Toast.makeText(this, "Vui lòng chọn ngân hàng", Toast.LENGTH_SHORT).show()
            return
        }
        if (accNum.isEmpty() || accHolder.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val bankInfo: HashMap<String, Any> = hashMapOf(
            "bank_code" to bank.id,
            "bank_name" to bank.name,
            "account_number" to accNum,
            "account_holder" to accHolder,
            "linked_at" to timestamp,
            "logo_url" to bank.logoUrl
        )

        db.collection("users").document(uid)
            .collection("bank_info")
            .add(bankInfo)
            .addOnSuccessListener {
                Toast.makeText(this, "Liên kết ngân hàng thành công!", Toast.LENGTH_SHORT).show()

                // Quay về ProfileActivity
                val intent = Intent(this, ProfileActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi khi lưu dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
            }

    }
}
