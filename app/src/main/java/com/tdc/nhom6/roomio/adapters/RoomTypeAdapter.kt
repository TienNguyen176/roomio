package com.tdc.nhom6.roomio.adapters

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.booking.GuestDetailActivity
import com.tdc.nhom6.roomio.activities.hotel.HotelDetailActivity
import com.tdc.nhom6.roomio.databinding.ItemRoomTypeBinding
import com.tdc.nhom6.roomio.models.Booking
import com.tdc.nhom6.roomio.models.Facility
import com.tdc.nhom6.roomio.models.FacilityPriceRateModel
import com.tdc.nhom6.roomio.models.RoomImage
import com.tdc.nhom6.roomio.models.Scene
import java.util.Locale
import androidx.recyclerview.widget.LinearLayoutManager
import com.tdc.nhom6.roomio.models.RoomType
import java.util.concurrent.TimeUnit
import com.google.firebase.Timestamp
import java.util.Date

class RoomTypeAdapter(
    private val context: Context,
    private val items: List<RoomType>,
    private val fragmentManager: FragmentManager
) : RecyclerView.Adapter<RoomTypeAdapter.RoomTypeViewHolder>() {

    private val expandedPositions = mutableSetOf<Int>()
    private lateinit var booking: Booking
    private val customerId = "5mDP6WZb1JWcuSDOgPtDycty7H53"

    class RoomTypeViewHolder(
        val binding: ItemRoomTypeBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {

        val facilitiesList: MutableList<Facility> = mutableListOf()
        val facilityAdapter = FacilityAdapter(context, facilitiesList)

        var facilityRatesListener: ListenerRegistration? = null
        var viewListener: ListenerRegistration? = null
        var facilityDetailsListener: ListenerRegistration? = null

        init {
            binding.layoutBasic.gridFacilitiesBasic.layoutManager = GridLayoutManager(context, 2)
            binding.layoutBasic.gridFacilitiesBasic.adapter = facilityAdapter

            binding.layoutDetail.gridFacilitiesDetail.layoutManager = GridLayoutManager(context, 2)
            binding.layoutDetail.gridFacilitiesDetail.adapter = facilityAdapter
        }

        fun clearListeners() {
            facilityRatesListener?.remove()
            viewListener?.remove()
            facilityDetailsListener?.remove()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomTypeViewHolder {
        val binding = ItemRoomTypeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RoomTypeViewHolder(binding, context)
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: RoomTypeViewHolder) {
        super.onViewRecycled(holder)
        holder.clearListeners()
    }

    override fun onBindViewHolder(holder: RoomTypeViewHolder, position: Int) {
        val roomType: RoomType = items[position]
        val binding = holder.binding

        holder.clearListeners()

        val formattedPrice = Format.formatCurrency(roomType.pricePerNight)

        binding.layoutBasic.tvNameTypeBasic.text = roomType.typeName
        binding.layoutBasic.tvPriceBasic.text = formattedPrice

        binding.layoutDetail.tvNameType.text = roomType.typeName
        binding.layoutDetail.tvTotalPrice.text = formattedPrice
        binding.layoutDetail.tvNumberGuest.text = "${roomType.maxPeople} people"

        binding.layoutBasic.areaBasic.tvFacilityName.text = "${roomType.area} m2"
        binding.layoutDetail.areaDetail.tvFacilityName.text = "${roomType.area} m2"

        loadViewRealtime(holder, roomType.viewId!!) { result ->
            binding.layoutBasic.viewBasic.tvFacilityName.text = result.name
            binding.layoutDetail.viewDetail.tvFacilityName.text = result.name
        }

        binding.layoutBasic.areaBasic.iconFacility.setImageResource(R.drawable.ic_bed)
        binding.layoutDetail.areaDetail.iconFacility.setImageResource(R.drawable.ic_bed)
        binding.layoutBasic.viewBasic.iconFacility.setImageResource(R.drawable.ic_view)
        binding.layoutDetail.viewDetail.iconFacility.setImageResource(R.drawable.ic_view)

        val thumbnailImage = roomType.roomImages?.firstOrNull()

        if (thumbnailImage != null) {
            Glide.with(context).load(thumbnailImage.imageUrl).into(binding.layoutDetail.imgRoomType)
            binding.layoutDetail.tvNumImage.text = "+${roomType.roomImages?.size ?: 0}"
        }

        holder.facilitiesList.clear()
        loadFacilityRatesRealtime(roomType.roomTypeId, holder.facilitiesList, holder.facilityAdapter, holder)


        if (expandedPositions.contains(position)) {
            binding.layoutDetail.root.visibility = View.VISIBLE
            binding.layoutBasic.root.visibility = View.GONE
        } else {
            binding.layoutDetail.root.visibility = View.GONE
            binding.layoutBasic.root.visibility = View.VISIBLE
        }

        holder.itemView.setOnClickListener {
            toggleExpansion(position)
        }

        binding.layoutDetail.cardImgRoom.setOnClickListener {
            roomType.roomImages?.let { images ->
                openDialogListImage(images)
            }
        }

        binding.layoutDetail.btnReverve.setOnClickListener {
            showDateRangePickerDialog(roomType)
        }
        binding.layoutBasic.btnReverveBasic.setOnClickListener {
            showDateRangePickerDialog(roomType)
        }
    }

    private fun openDialogListImage(images: List<RoomImage>) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_image_list, null)
        val recyclerImage = view.findViewById<RecyclerView>(R.id.recycleListImage)

        val nextItemClickListener: (Int) -> Unit = { currentPosition ->
            val nextPosition = currentPosition + 1
            if (nextPosition < images.size) {
                recyclerImage.smoothScrollToPosition(nextPosition)
            }
        }

        recyclerImage.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
         recyclerImage.adapter = RoomImageListAdapter(images, nextItemClickListener)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        view.findViewById<ImageButton>(R.id.cancel_dialog).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun loadFacilityRatesRealtime(roomTypeId: String, list: MutableList<Facility>, adapter: FacilityAdapter, holder: RoomTypeViewHolder) {
        val ratesCollectionPath = "roomTypes/${roomTypeId}/facilityRates"

        holder.facilityRatesListener?.remove()

        holder.facilityRatesListener = HotelDetailActivity.db.collection(ratesCollectionPath)
            .addSnapshotListener { rateResults, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Error listening to Facility Rates: ", exception)
                    list.clear()
                    adapter.notifyDataSetChanged()
                    return@addSnapshotListener
                }

                if (rateResults != null) {
                    val facilityIds = mutableSetOf<String>()
                    for (document in rateResults.documents) {
                        try {
                            val rate = document.toObject(FacilityPriceRateModel::class.java)
                            rate?.facilityId?.let { facilityIds.add(it) }

                        } catch (e: Exception) {
                            Log.e("Firestore", "Error converting FacilityPriceRateModel: ${document.id}", e)
                        }
                    }
                    loadFacilityDetailsOneTime(facilityIds, list, adapter)
                }
            }
    }

    private fun loadViewRealtime(holder: RoomTypeViewHolder, viewId: String, onSuccess:(view: Scene) -> Unit) {

        holder.viewListener?.remove()

        holder.viewListener = HotelDetailActivity.db.collection("views")
            .document(viewId)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Error listening to View: ", exception)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val view = snapshot.toObject(Scene::class.java)
                        view?.let(onSuccess)
                    } catch (e: Exception) {
                        Log.e("Firestore", "Error converting View: ${snapshot.id}", e)
                    }
                }
            }
    }


    private fun loadFacilityDetailsOneTime(ids: Set<String>, list: MutableList<Facility>, adapter: FacilityAdapter) {
        list.clear()

        if (ids.isEmpty()) {
            adapter.notifyDataSetChanged()
            return
        }

        HotelDetailActivity.db.collection("facilities")
            .whereIn("__name__", ids.toList())
            .get()
            .addOnSuccessListener { querySnapshot ->
                list.clear()
                for (document in querySnapshot) {
                    try {
                        val facility = document.toObject(Facility::class.java)
                        list.add(facility)
                    } catch (e: Exception) {
                        Log.e("Firestore", "Error converting Facility model: ${document.id}", e)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error loading Facilities (whereIn): ", exception)
                list.clear()
                adapter.notifyDataSetChanged()
            }
    }

    private fun showDateRangePickerDialog(roomType: RoomType) {
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

            val diff = endDate - startDate
            val numberOfNights = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)

            if (numberOfNights <= 0) {
                Log.e("Booking", "Check-out date must be after check-in date.")
                return@addOnPositiveButtonClickListener
            }

            val totalOrigin = roomType.pricePerNight * numberOfNights.toDouble()
            Log.d("Result", totalOrigin.toString())

            booking = Booking(
                customerId = customerId,
                roomTypeId = roomType.roomTypeId,
                checkInDate = Timestamp(Date(startDate)),
                checkOutDate = Timestamp(Date(endDate)),
                numberGuest = roomType.maxPeople,
                totalOrigin = totalOrigin,
                totalFinal = totalOrigin
            )
            val intent = Intent(context, GuestDetailActivity::class.java)
            intent.putExtra("BOOKING_DATA", booking)

            context.startActivity(intent)
        }

        dateRangePicker.show(fragmentManager, "DATE_RANGE_PICKER")

    }

    private fun toggleExpansion(position: Int) {
        val previouslyExpanded = expandedPositions.contains(position)

        expandedPositions.clear()

        if (!previouslyExpanded) {
            expandedPositions.add(position)
        }
        notifyDataSetChanged()
    }

    object Format {
        fun formatCurrency(price: Double): String {
            return String.format(Locale.getDefault(), "VND %,.0f", price)
        }
    }
}