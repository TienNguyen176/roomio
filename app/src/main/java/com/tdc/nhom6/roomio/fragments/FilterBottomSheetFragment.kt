// FilterBottomSheetFragment.kt
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

class FilterBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetFilterBinding? = null
    private val binding get() = _binding!!

    private var amenities: List<Service> = emptyList()
    private val selectedAmenityIds = mutableSetOf<String>()

    //private var tienIch: List<Facility> = emptyList()
    private val tienIchIds = mutableSetOf<String>()

    private val DEFAULT_MIN_PRICE = 1_000_000
    private val DEFAULT_MAX_PRICE = 10_000_000
    private var currentMinPrice = DEFAULT_MIN_PRICE
    private var currentMaxPrice = DEFAULT_MAX_PRICE

    private var onApplyFilter: ((minPrice: Int, maxPrice: Int,
                                 selectedServiceIds: Set<String>,
                                 selectedFacilityIds: Set<String>) -> Unit)? = null

    fun setAmenities(list: List<Service>) { amenities = list }

    //fun setTienIch(list: List<Facility>) { tienIch = list }

    fun setOnApplyFilterListener(listener: (Int, Int, Set<String>, Set<String>) -> Unit) {
        onApplyFilter = listener
    }



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentBottomSheetFilterBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAmenityList()
        // setupTienIchList()
        setupPriceControls()

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnReset.setOnClickListener { resetFilters() }
        binding.btnApply.setOnClickListener {
            onApplyFilter?.invoke(currentMinPrice, currentMaxPrice, selectedAmenityIds, tienIchIds)
            dismiss()
        }
    }

    private fun setupAmenityList() {
        binding.rvDichVu.adapter = FilterServiceAdapter(amenities, selectedAmenityIds)
    }

    /*
    private fun setupTienIchList() {
        binding.rvTienIch.adapter = FilterTienIchAdapter(tienIch, tienIchIds)
    }
    */

    private fun setupPriceControls() {
        binding.edtMinPrice.setText(formatMoney(currentMinPrice.toDouble()))
        binding.edtMaxPrice.setText(formatMoney(currentMaxPrice.toDouble()))
        binding.doubleRangeSeekbar.currentMinValue = currentMinPrice
        binding.doubleRangeSeekbar.currentMaxValue = currentMaxPrice

        // Slider → EditText
        binding.doubleRangeSeekbar.setOnRangeSeekBarViewChangeListener(object : OnDoubleValueSeekBarChangeListener {
            override fun onValueChanged(seekBar: DoubleValueSeekBarView?, min: Int, max: Int, fromUser: Boolean) {
                currentMinPrice = min.coerceAtMost(max)
                currentMaxPrice = max.coerceAtLeast(min)
                binding.edtMinPrice.setText(formatMoney(currentMinPrice.toDouble()))
                binding.edtMaxPrice.setText(formatMoney(currentMaxPrice.toDouble()))
            }
            override fun onStartTrackingTouch(seekBar: DoubleValueSeekBarView?, min: Int, max: Int) {}
            override fun onStopTrackingTouch(seekBar: DoubleValueSeekBarView?, min: Int, max: Int) {}
        })

        // EditText → Slider
        binding.edtMinPrice.addTextChangedListener(MoneyTextWatcher(binding.edtMinPrice,
            DEFAULT_MIN_PRICE, DEFAULT_MAX_PRICE) { value ->
            currentMinPrice = value
            binding.doubleRangeSeekbar.currentMinValue = currentMinPrice
        })

        binding.edtMaxPrice.addTextChangedListener(MoneyTextWatcher(binding.edtMaxPrice,
            DEFAULT_MIN_PRICE, DEFAULT_MAX_PRICE) { value ->
            currentMaxPrice = value
            binding.doubleRangeSeekbar.currentMaxValue = currentMaxPrice
        })

    }

    private fun resetFilters() {
        currentMinPrice = DEFAULT_MIN_PRICE
        currentMaxPrice = DEFAULT_MAX_PRICE
        binding.edtMinPrice.setText(formatMoney(currentMinPrice.toDouble()))
        binding.edtMaxPrice.setText(formatMoney(currentMaxPrice.toDouble()))
        binding.doubleRangeSeekbar.currentMinValue = currentMinPrice
        binding.doubleRangeSeekbar.currentMaxValue = currentMaxPrice
        selectedAmenityIds.clear()
        tienIchIds.clear()
        (binding.rvDichVu.adapter as? FilterServiceAdapter)?.notifyDataSetChanged()
        //(binding.rvTienIch.adapter as? FilterTienIchAdapter)?.notifyDataSetChanged()
    }

    private fun parsePrice(text: String?, defaultValue: Int) =
        text?.replace("[^\\d]".toRegex(), "")?.toIntOrNull() ?: defaultValue

    private fun formatMoney(amount: Double) = String.format("%,dđ", amount.toLong()).replace(',', '.')

    private class MoneyTextWatcher(
        private val editText: android.widget.EditText,
        private val minValue: Int,
        private val maxValue: Int,
        private val onValueChanged: (Int) -> Unit
    ) : TextWatcher {
        private var current = ""
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            val str = s.toString()
            if (str != current) {
                // Lấy số từ chuỗi
                var value = str.replace("[^\\d]".toRegex(), "").toIntOrNull() ?: minValue
                // Giới hạn min/max
                if (value < minValue) value = minValue
                if (value > maxValue) value = maxValue

                current = String.format("%,dđ", value).replace(',', '.')
                editText.removeTextChangedListener(this)
                editText.setText(current)
                editText.setSelection(current.length - 1)
                editText.addTextChangedListener(this)
                onValueChanged(value)
            }
        }
    }

    //Ham nhan lua chon ban dau cua loc
    fun setSelectedServices(ids: Set<String>) {
        selectedAmenityIds.clear()
        selectedAmenityIds.addAll(ids)
    }

    fun setSelectedFacilities(ids: Set<String>) {
        tienIchIds.clear()
        tienIchIds.addAll(ids)
    }

    fun setSelectedPrice(min: Int, max: Int) {
        currentMinPrice = min
        currentMaxPrice = max
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
