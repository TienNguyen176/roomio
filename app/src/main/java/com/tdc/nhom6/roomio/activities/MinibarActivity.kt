package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.adapters.MinibarAdapterHotel
import com.tdc.nhom6.roomio.databinding.ActivityMinibarLayoutBinding
import com.tdc.nhom6.roomio.databinding.DialogAddMinibarLayoutBinding
import com.tdc.nhom6.roomio.models.MinibarItem
import java.util.UUID

class MinibarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMinibarLayoutBinding
    private lateinit var adapter: MinibarAdapterHotel
    private val minibarList = mutableListOf<MinibarItem>()
    private val db = FirebaseFirestore.getInstance()

    private var hotelId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMinibarLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hotelId = intent.getStringExtra("hotelId") ?: ""

        initRecycler()
        loadMinibar()
        registerEvents()
    }

    private fun initRecycler() {
        adapter = MinibarAdapterHotel(minibarList, object : MinibarAdapterHotel.MinibarListener {

            override fun onEdit(item: MinibarItem) {
                openEditDialog(item)
            }

            override fun onDelete(item: MinibarItem, position: Int) {
                confirmDelete(item, position)
            }

        })

        binding.rvMinibar.layoutManager = LinearLayoutManager(this)
        binding.rvMinibar.adapter = adapter
    }


    private fun registerEvents() {
        binding.rowBack.setOnClickListener { finish() }

        binding.btnAddMinibar.setOnClickListener {
            openAddDialog()
        }
    }

    private fun loadMinibar() {
        db.collection("hotels")
            .document(hotelId)
            .collection("minibar")
            .get()
            .addOnSuccessListener { result ->
                minibarList.clear()
                for (doc in result) {
                    minibarList.add(doc.toObject(MinibarItem::class.java))
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun openAddDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogAddMinibarLayoutBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.btnSave.setOnClickListener {

            val name = dialogBinding.edtName.text.toString().trim()
            val price = dialogBinding.edtPrice.text.toString().toLongOrNull() ?: 0L

            if (name.isEmpty()) {
                dialogBinding.edtName.error = "Không được để trống"
                return@setOnClickListener
            }

            val id = UUID.randomUUID().toString()
            val item = MinibarItem(id, name, price, true)

            db.collection("hotels")
                .document(hotelId)
                .collection("minibar")
                .document(id)
                .set(item)
                .addOnSuccessListener {
                    minibarList.add(item)
                    adapter.notifyItemInserted(minibarList.size - 1)
                    dialog.dismiss()
                }
        }

        dialog.show()
    }
    private fun openEditDialog(item: MinibarItem) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogAddMinibarLayoutBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.edtName.setText(item.name)
        dialogBinding.edtPrice.setText(item.price.toString())
        dialogBinding.btnSave.text = "Cập nhật"

        dialogBinding.btnSave.setOnClickListener {

            val name = dialogBinding.edtName.text.toString().trim()
            val price = dialogBinding.edtPrice.text.toString().toLongOrNull() ?: 0

            if (name.isEmpty()) {
                dialogBinding.edtName.error = "Không được để trống"
                return@setOnClickListener
            }

            val newItem = item.copy(
                name = name,
                price = price
            )

            db.collection("hotels")
                .document(hotelId)
                .collection("minibar")
                .document(item.id)
                .set(newItem)
                .addOnSuccessListener {
                    val index = minibarList.indexOfFirst { it.id == item.id }
                    if (index != -1) {
                        adapter.updateItem(index, newItem)
                    }
                    dialog.dismiss()
                }
        }

        dialog.show()
    }

    private fun confirmDelete(item: MinibarItem, position: Int) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Xóa sản phẩm?")
            .setMessage("Bạn có chắc muốn xóa '${item.name}' khỏi minibar?")
            .setPositiveButton("Xóa") { _, _ ->
                db.collection("hotels")
                    .document(hotelId)
                    .collection("minibar")
                    .document(item.id)
                    .delete()
                    .addOnSuccessListener {
                        adapter.removeItem(position)
                    }
            }
            .setNegativeButton("Hủy", null)
            .create()

        dialog.show()
    }

}
