package com.tdc.nhom6.roomio.activities.profile

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import com.tdc.nhom6.roomio.apis.BankLookupClient
import com.tdc.nhom6.roomio.apis.VietQRClient
import com.tdc.nhom6.roomio.databinding.LinkBankLayoutBinding
import com.tdc.nhom6.roomio.models.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LinkBankActivity : AppCompatActivity() {

    private lateinit var binding: LinkBankLayoutBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var selectedBank: Bank? = null
    private var loadingDialog: Dialog? = null
    private var lastLookupAcc: String? = null

    private val apiKey = "44a4a79c-1f4f-4df7-ac3f-31ed52c8d644key"
    private val apiSecret = "5ae25b4e-77f4-41fa-abc8-a3174bf1cafesecret"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LinkBankLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initLoading()

        binding.layoutSelectBank.setOnClickListener { showBankList() }
        binding.btnLinkBank.setOnClickListener { saveBankInfo() }
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Lookup khi rời focus
        binding.edtAccountNumber.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val bank = selectedBank
                val accNum = binding.edtAccountNumber.text.toString().trim()
                if (bank != null && accNum.isNotEmpty()) lookupAccount(bank.id, accNum)
            }
        }

        // Lookup khi nhập đủ số tài khoản
        binding.edtAccountNumber.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val bank = selectedBank
                val accNum = s.toString().trim()
                if (bank != null && accNum.length >= 10) { // chỉnh theo ngân hàng
                    lookupAccount(bank.id, accNum)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // ===== SHOW BANK LIST (VietQR API) =====
    private fun showBankList() {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottomsheet_select_bank, null)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerBanks)
        val edtSearch = view.findViewById<EditText>(R.id.edtSearchBank)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        recycler.layoutManager = LinearLayoutManager(this)
        progressBar.visibility = ProgressBar.VISIBLE

        VietQRClient.api.getBanks().enqueue(object: Callback<VietQRBankResponse> {
            override fun onResponse(call: Call<VietQRBankResponse>, response: Response<VietQRBankResponse>) {
                progressBar.visibility = ProgressBar.GONE
                if (response.isSuccessful && response.body() != null) {
                    val banks = response.body()!!.data.map {
                        Bank(id = it.code, name = it.name, logoUrl = it.logo ?: "")
                    }

                    val adapter = BankAdapter(banks) { bank ->
                        selectedBank = bank
                        binding.tvBankName.text = bank.name
                        Glide.with(this@LinkBankActivity).load(bank.logoUrl).into(binding.imgBankLogo)
                        bottomSheet.dismiss()

                        // Lookup nếu số tài khoản đã nhập
                        val accNum = binding.edtAccountNumber.text.toString().trim()
                        if (accNum.isNotEmpty()) lookupAccount(bank.id, accNum)
                    }

                    recycler.adapter = adapter

                    // Search filter
                    edtSearch.addTextChangedListener(object: TextWatcher {
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            adapter.filter.filter(s)
                        }
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun afterTextChanged(s: Editable?) {}
                    })
                } else {
                    Toast.makeText(this@LinkBankActivity, "Không tải được danh sách ngân hàng", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<VietQRBankResponse>, t: Throwable) {
                progressBar.visibility = ProgressBar.GONE
                Toast.makeText(this@LinkBankActivity, "Lỗi: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })

        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    // ===== LOOKUP ACCOUNT (BankLookup API) =====
    private fun lookupAccount(bankBin: String, accountNumber: String) {
        if (accountNumber == lastLookupAcc) return
        lastLookupAcc = accountNumber

        showLoading()
        Log.e("LOOKUP_DEBUG", "Lookup → bank: $bankBin | acc: $accountNumber")

        val req = BankLookupRequest(bank = bankBin, account = accountNumber)
        BankLookupClient.api.lookupAccount(apiKey, apiSecret, req)
            .enqueue(object: Callback<BankLookupResponse> {
                override fun onResponse(call: Call<BankLookupResponse>, response: Response<BankLookupResponse>) {
                    hideLoading()
                    val name = response.body()?.data?.ownerName ?: ""
                    binding.edtAccountHolder.setText(name)
                    if (!response.isSuccessful || name.isEmpty()) {
                        Toast.makeText(this@LinkBankActivity, "Không tìm thấy thông tin", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<BankLookupResponse>, t: Throwable) {
                    hideLoading()
                    binding.edtAccountHolder.setText("")
                    Toast.makeText(this@LinkBankActivity, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // ===== SAVE BANK INFO =====
    private fun saveBankInfo() {
        val uid = auth.currentUser?.uid ?: return
        val bank = selectedBank ?: run { Toast.makeText(this, "Vui lòng chọn ngân hàng", Toast.LENGTH_SHORT).show(); return }
        val accNumber = binding.edtAccountNumber.text.toString().trim()
        val accHolder = binding.edtAccountHolder.text.toString().trim()
        val chiNhanh = binding.edtChiNhanh.text.toString().trim()
        if (accNumber.isEmpty()) { Toast.makeText(this, "Vui lòng nhập số tài khoản", Toast.LENGTH_SHORT).show(); return }
        if (accHolder.isEmpty()) { Toast.makeText(this, "Không thể xác thực số tài khoản", Toast.LENGTH_SHORT).show(); return }

        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val bankInfo = BankInfo(
            account_holder = accHolder,
            account_number = accNumber,
            bank_code = bank.id,
            bank_name = bank.name,
            chi_nhanh = chiNhanh,
            default = false,
            linked_at = timestamp,
            logo_url = bank.logoUrl
        )

        showLoading()
        db.collection("users").document(uid)
            .collection("bank_info")
            .add(bankInfo)
            .addOnSuccessListener { hideLoading(); Toast.makeText(this, "Liên kết thành công!", Toast.LENGTH_SHORT).show(); finish() }
            .addOnFailureListener { hideLoading(); Toast.makeText(this, "Lỗi: ${it.message}", Toast.LENGTH_SHORT).show() }
    }

    // ===== LOADING =====
    private fun initLoading() {
        loadingDialog = Dialog(this)
        loadingDialog!!.setContentView(R.layout.dialog_loading)
        loadingDialog!!.setCancelable(false)
    }
    private fun showLoading() { try { if (!loadingDialog!!.isShowing) loadingDialog!!.show() } catch (_: Exception) {} }
    private fun hideLoading() { try { if (loadingDialog!!.isShowing) loadingDialog!!.dismiss() } catch (_: Exception) {} }
}
