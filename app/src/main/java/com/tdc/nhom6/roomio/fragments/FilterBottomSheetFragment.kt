package com.tdc.nhom6.roomio.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tdc.nhom6.roomio.adapters.FilterServiceAdapter
import com.tdc.nhom6.roomio.adapters.FilterTienIchAdapter
import com.tdc.nhom6.roomio.databinding.FragmentBottomSheetFilterBinding
import com.tdc.nhom6.roomio.models.Facility
import com.tdc.nhom6.roomio.models.Service
import com.mohammedalaa.seekbar.DoubleValueSeekBarView
import com.mohammedalaa.seekbar.OnDoubleValueSeekBarChangeListener
import com.mohammedalaa.seekbar.OnRangeSeekBarChangeListener

class FilterBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetFilterBinding? = null
    private val binding get() = _binding!!

    private var amenities: List<Service> = emptyList()
    private val selectedAmenityIds = mutableSetOf<String>()

    private var tienIch: List<Facility> = emptyList()
    private val tienIchIds = mutableSetOf<String>()

    // Giá mặc định
    private val DEFAULT_MIN_PRICE = 1_000_000
    private val DEFAULT_MAX_PRICE = 10_000_000
    private var currentMinPrice = DEFAULT_MIN_PRICE
    private var currentMaxPrice = DEFAULT_MAX_PRICE

    // Callback gửi filter ra ngoài
    private var onApplyFilter: ((minPrice: Int, maxPrice: Int,
                                 selectedServiceIds: Set<String>,
                                 selectedFacilityIds: Set<String>) -> Unit)? = null

    fun setAmenities(list: List<Service>) {
        amenities = list
    }

    fun setTienIch(list: List<Facility>) {
        tienIch = list
    }

    fun setOnApplyFilterListener(
        listener: (minPrice: Int, maxPrice: Int,
                   selectedServiceIds: Set<String>,
                   selectedFacilityIds: Set<String>) -> Unit
    ) {
        onApplyFilter = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBottomSheetFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAmenityList()
        //setupTienIchList()
        setupPriceControls()

        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnReset.setOnClickListener {
            resetFilters()
        }

        binding.btnApply.setOnClickListener {
            val min = parsePrice(binding.edtMinPrice.text.toString(), DEFAULT_MIN_PRICE)
            val max = parsePrice(binding.edtMaxPrice.text.toString(), DEFAULT_MAX_PRICE)

            currentMinPrice = min
            currentMaxPrice = max

            onApplyFilter?.invoke(
                min,
                max,
                selectedAmenityIds,
                tienIchIds
            )
            dismiss()
        }
    }

    // --------------------------
    // List Dịch vụ & Tiện ích
    // --------------------------
    private fun setupAmenityList() {
        val adapter = FilterServiceAdapter(amenities, selectedAmenityIds)
        binding.rvDichVu.adapter = adapter
    }
    //
//    private fun setupTienIchList() {
//        val adapter = FilterTienIchAdapter(tienIch, tienIchIds)
//        binding.rvTienIch.adapter = adapter
//    }

    // --------------------------
    // Giá + Slider
    // --------------------------
    private fun setupPriceControls() {
        // Set giá mặc định vào EditText
        binding.edtMinPrice.setText(DEFAULT_MIN_PRICE.toString())
        binding.edtMaxPrice.setText(DEFAULT_MAX_PRICE.toString())

        // 1) Lắng nghe sự thay đổi từ thanh trượt → cập nhật EditText
        binding.doubleRangeSeekbar.setOnRangeSeekBarViewChangeListener(object : OnDoubleValueSeekBarChangeListener {
            override fun onValueChanged(seekBar: DoubleValueSeekBarView?, min: Int, max: Int, fromUser: Boolean) {
                // Cập nhật giá trị vào EditText khi thanh trượt thay đổi
                var newMin = min
                var newMax = max

                // Nếu kéo min vượt max → đẩy max theo
                if (newMin > newMax) {
                    newMax = newMin
                    binding.doubleRangeSeekbar.currentMaxValue = newMax
                }

                // Nếu kéo max xuống dưới min → đẩy min theo
                if (newMax < newMin) {
                    newMin = newMax
                    binding.doubleRangeSeekbar.currentMinValue = newMin
                }

                currentMinPrice = newMin
                currentMaxPrice = newMax

                binding.edtMinPrice.setText(currentMinPrice.toString())
                binding.edtMaxPrice.setText(currentMaxPrice.toString())

            }

            override fun onStartTrackingTouch(seekBar: DoubleValueSeekBarView?, min: Int, max: Int) {
                // Bạn có thể làm gì đó khi người dùng bắt đầu kéo thanh trượt
            }

            override fun onStopTrackingTouch(seekBar: DoubleValueSeekBarView?, min: Int, max: Int) {
                // Bạn có thể làm gì đó khi người dùng dừng kéo thanh trượt
            }
        })

        // 2) Lắng nghe sự thay đổi từ EditText → cập nhật thanh trượt
        binding.edtMinPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val value = parsePrice(s?.toString(), DEFAULT_MIN_PRICE)
                currentMinPrice = value

                // Cập nhật thanh trượt với giá trị mới
                binding.doubleRangeSeekbar.currentMinValue = currentMinPrice
            }
        })

        binding.edtMaxPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val value = parsePrice(s?.toString(), DEFAULT_MAX_PRICE)
                currentMaxPrice = value

                // Cập nhật thanh trượt với giá trị mới
                binding.doubleRangeSeekbar.currentMaxValue = currentMaxPrice
            }
        })

    }


    private fun resetFilters() {
        currentMinPrice = DEFAULT_MIN_PRICE
        currentMaxPrice = DEFAULT_MAX_PRICE

        binding.edtMinPrice.setText(DEFAULT_MIN_PRICE.toString())
        binding.edtMaxPrice.setText(DEFAULT_MAX_PRICE.toString())

        // ⚠️ Reset slider cũng phải dùng đúng hàm của lib:
        // Ví dụ:
        // binding.doubleRangeSeekbar.setCurrentMinValue(DEFAULT_MIN_PRICE.toFloat())
        // binding.doubleRangeSeekbar.setCurrentMaxValue(DEFAULT_MAX_PRICE.toFloat())

        selectedAmenityIds.clear()
        tienIchIds.clear()

        (binding.rvDichVu.adapter as? FilterServiceAdapter)?.notifyDataSetChanged()
        //(binding.rvTienIch.adapter as? FilterTienIchAdapter)?.notifyDataSetChanged()
    }

    private fun parsePrice(text: String?, defaultValue: Int): Int {
        if (text.isNullOrBlank()) return defaultValue
        return text.replace(".", "").replace(",", "")
            .filter { it.isDigit() }
            .toIntOrNull() ?: defaultValue
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
