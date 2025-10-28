package com.tdc.nhom6.roomio.adapters

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.HotelDetailActivity
import com.tdc.nhom6.roomio.models.Facility
import com.tdc.nhom6.roomio.models.FacilityAdapter
import com.tdc.nhom6.roomio.models.FacilityPriceRateModel
import com.tdc.nhom6.roomio.models.RoomImage
import com.tdc.nhom6.roomio.models.RoomType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RoomTypeAdapter(
    private val context: Context,
    private val items: List<RoomType>,
    private val fragmentManager: FragmentManager
): BaseAdapter() {
    private val expandedPositions = mutableSetOf<Int>()

    private class ViewHolder(view: View) {
        // Basic Content Views
        val typeNameBasic: TextView = view.findViewById(R.id.tvNameTypeBasic)
        val typeName: TextView = view.findViewById(R.id.tvNameType)
        val totalPriceBasic: TextView = view.findViewById(R.id.tvPriceBasic)
        val totalPrice: TextView = view.findViewById(R.id.tvTotalPrice)
        val btnReserveBasic: Button = view.findViewById(R.id.btnReverveBasic)
        val btnReserve: Button = view.findViewById(R.id.btnReverve)

        // Detail Toggle Container
        val detailLayout: LinearLayout = view.findViewById(R.id.layout_detail)
        val basicLayout: LinearLayout = view.findViewById(R.id.layout_basic)

        // Detail Content Views
        val cardImgRoom: androidx.cardview.widget.CardView = view.findViewById(R.id.cardImgRoom)
        val imgRoomType: ImageView = view.findViewById(R.id.imgRoomType)
        val tvNumImage: TextView = view.findViewById(R.id.tvNumImage)
        val gridFacilities_basic: GridView = view.findViewById(R.id.gridFacilities_basic)
        val gridFacilities_detail: GridView = view.findViewById(R.id.gridFacilities_detail)
        val tvNumberGuest: TextView = view.findViewById(R.id.tvNumberGuest)
        val area_basic: LinearLayout = view.findViewById(R.id.area_basic)
        val scene_basic: LinearLayout = view.findViewById(R.id.view_basic)
        val area_detail: LinearLayout = view.findViewById(R.id.area_detail)
        val scene_detail: LinearLayout = view.findViewById(R.id.view_detail)

        // THUỘC TÍNH CỤC BỘ DÀNH CHO FACILITY CỦA MỤC NÀY
        val facilitiesList: MutableList<Facility> = mutableListOf()
        lateinit var facilityAdapter: FacilityAdapter
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder
        val roomType: RoomType = items[position]

        if (convertView == null) {
            val inflater = LayoutInflater.from(context)
            view = inflater.inflate(R.layout.item_room_type, parent, false)
            holder = ViewHolder(view)
            holder.facilityAdapter = FacilityAdapter(context, holder.facilitiesList)
            holder.gridFacilities_basic.adapter = holder.facilityAdapter
            holder.gridFacilities_detail.adapter = holder.facilityAdapter

            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder

            holder.gridFacilities_basic.adapter = holder.facilityAdapter
            holder.gridFacilities_detail.adapter = holder.facilityAdapter

        }

        // --- BIND DỮ LIỆU CƠ BẢN ---
        holder.typeNameBasic.text = roomType.typeName
        holder.typeName.text = roomType.typeName
        val formattedPrice = Format.formatCurrency(roomType.pricePerNight)
        holder.totalPrice.text = formattedPrice
        holder.totalPriceBasic.text = formattedPrice
        holder.tvNumberGuest.text = "${roomType.maxPeople} people"
        holder.area_basic.findViewById<TextView>(R.id.tvFacilityName).text="${roomType.area} m2"
        holder.scene_basic.findViewById<TextView>(R.id.tvFacilityName).text=roomType.viewId.toString()
        holder.area_detail.findViewById<TextView>(R.id.tvFacilityName).text="${roomType.area} m2"
        holder.scene_detail.findViewById<TextView>(R.id.tvFacilityName).text=roomType.viewId.toString()
        holder.area_basic.findViewById<ImageView>(R.id.iconFacility).setImageResource(R.drawable.ic_bed)
        holder.scene_basic.findViewById<ImageView>(R.id.iconFacility).setImageResource(R.drawable.ic_bed)
        holder.area_detail.findViewById<ImageView>(R.id.iconFacility).setImageResource(R.drawable.ic_bed)
        holder.scene_detail.findViewById<ImageView>(R.id.iconFacility).setImageResource(R.drawable.ic_bed)

        val thumbnailImage = roomType.roomImages?.firstOrNull()

        if (thumbnailImage != null) {
            Glide.with(context).load(thumbnailImage.imageUrl).into(holder.imgRoomType)
            holder.tvNumImage.text = "+${roomType.roomImages?.size}"
        }


        if (holder.facilitiesList.isEmpty()) {
            loadFacilityRates(roomType.typeId, holder.facilitiesList, holder.facilityAdapter)
        }


        // --- XỬ LÝ ẨN/HIỆN (EXPAND/COLLAPSE) ---
        if (expandedPositions.contains(position)) {
            holder.detailLayout.visibility = View.VISIBLE
            holder.basicLayout.visibility = View.GONE
        } else {
            holder.detailLayout.visibility = View.GONE
            holder.basicLayout.visibility = View.VISIBLE
        }

        // --- XỬ LÝ CLICK ---
        view.setOnClickListener {
            toggleExpansion(position)
        }

        holder.cardImgRoom.setOnClickListener {
            openDialogListImage(roomType.roomImages)
        }


        holder.btnReserve.setOnClickListener {
            showDateRangePickerDialog()
        }
        holder.btnReserveBasic.setOnClickListener {
            showDateRangePickerDialog()
        }

        return view
    }

    private fun openDialogListImage(images: List<RoomImage>) {
        val view= LayoutInflater.from(context).inflate(R.layout.dialog_image_list,null)
        val recyclerImage=view.findViewById<RecyclerView>(R.id.recycleListImage)
        // This lambda function IS the 'onNextClick' implementation
        val nextItemClickListener: (Int) -> Unit = { currentPosition ->
            val nextPosition = currentPosition + 1
            if (nextPosition < images.size) {
                recyclerImage.smoothScrollToPosition(nextPosition)
            }
        }

        recyclerImage.adapter = ImageListAdapter(images, nextItemClickListener) // Passed here
        recyclerImage.layoutManager= LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL,false)
        recyclerImage.adapter= ImageListAdapter(images,nextItemClickListener)
        val dialog:AlertDialog= AlertDialog.Builder(context)
            .setView(view)
            .create()

        view.findViewById<ImageButton>(R.id.cancel_dialog).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }


    private fun loadFacilityRates(roomTypeId: String, list: MutableList<Facility>, adapter: FacilityAdapter) {
        val ratesCollectionPath = "roomTypes/${roomTypeId}/facilityRates"

        HotelDetailActivity.db.collection(ratesCollectionPath)
            .get()
            .addOnSuccessListener { rateResults ->
                val facilityIds = mutableSetOf<String>()
                for (document in rateResults) {
                    try {
                        val rate = document.toObject(FacilityPriceRateModel::class.java)
                        rate.facilityId?.let { facilityIds.add(it) }

                    } catch (e: Exception) {
                        Log.e("Firestore", "Error converting FacilityPriceRateModel: ${document.id}", e)
                    }
                }

                list.clear()
                loadFacilityDetailsSingle(facilityIds, list, adapter)
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error loading Facility Rates: ", exception)
            }
    }

    private fun loadFacilityDetailsSingle(ids: Set<String>, list: MutableList<Facility>, adapter: FacilityAdapter) {
        if (ids.isEmpty()) {
            // Nếu không có ID nào, vẫn cập nhật để GridView biết là không có gì
            adapter.notifyDataSetChanged()
            return
        }

        for (facilityId in ids) {
            // Truy vấn từng document bằng Document ID
            HotelDetailActivity.db.collection("facilities")
                .document(facilityId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        try {
                            val facility = documentSnapshot.toObject(Facility::class.java)
                            if (facility != null) {
                                list.add(facility)
                            }
                        } catch (e: Exception) {
                            Log.e("Firestore", "Error converting Facility model: ${documentSnapshot.id}", e)
                        }
                    }

                    // Gọi cập nhật ngay lập tức sau mỗi lần thêm/xử lý document
                    Log.d("FacilityCheck", "Tiện nghi được tải: ${list.size}")
                    adapter.notifyDataSetChanged()
                }
                .addOnFailureListener { exception ->
                    Log.e("Firestore", "Error loading single Facility $facilityId: ", exception)
                    // Vẫn gọi cập nhật UI để tránh lỗi hiển thị nếu item khác đã được tải thành công
                    adapter.notifyDataSetChanged()
                }
        }
    }

    private fun showDateRangePickerDialog() {
        val constraintsBuilder =
            CalendarConstraints.Builder()
                .setValidator(
                    DateValidatorPointForward.now()
                )
        val calendarConstraints = constraintsBuilder.build()

        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("SELECT DATES")
            .setCalendarConstraints(calendarConstraints)
            .setTheme(R.style.CustomThemeOverlay_MaterialCalendar_Fullscreen)
            .build()
        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            val startDate = selection.first
            val endDate = selection.second

            val formattedCheckInDate = convertLongToDate(startDate)
            val formattedCheckOutDate = convertLongToDate(endDate)

        }

        dateRangePicker.show(fragmentManager, "DATE_RANGE_PICKER")

    }

    private fun toggleExpansion(position: Int) {
        if (expandedPositions.contains(position)) {
            expandedPositions.remove(position)
        } else {
            expandedPositions.clear()
            expandedPositions.add(position)
        }
        notifyDataSetChanged()
    }

    private fun convertLongToDate(time: Long): String {
        val date = Date(time)
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return format.format(date)
    }

    object Format {
        fun formatCurrency(price: Double): String {
            return String.format("VND %,.0f", price)
        }
    }
}