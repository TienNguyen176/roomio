package com.tdc.nhom6.roomio.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R

class ReceptionFragment : Fragment() {
    private lateinit var reservationAdapter: ReservationAdapter
    private val allReservations = mutableListOf<ReservationUi>()
    private var currentFilter: ReservationStatus = ReservationStatus.ALL
    private var searchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reception, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvReservations)
        
        // Configure RecyclerView to prevent swap behavior issues
        val layoutManager = LinearLayoutManager(requireContext())
        rv.layoutManager = layoutManager
        rv.setItemViewCacheSize(20) // Cache more views to prevent recycling issues
        rv.itemAnimator = null // Disable animations to prevent swap behavior errors
        rv.setNestedScrollingEnabled(false) // Disable nested scrolling
        rv.isNestedScrollingEnabled = false
        
        // Initialize data
        allReservations.clear()
        allReservations.addAll(placeholderItems())
        
        reservationAdapter = ReservationAdapter(allReservations.toMutableList())
        rv.adapter = reservationAdapter
        
        // Setup tabs
        setupTabs(view)
        
        // Setup search
        setupSearch(view)
    }

    private fun setupTabs(view: View) {
        val tabAll = view.findViewById<android.widget.TextView>(R.id.tabAll)
        val tabUncompleted = view.findViewById<android.widget.TextView>(R.id.tabUncompleted)
        val tabPending = view.findViewById<android.widget.TextView>(R.id.tabPending)
        val tabCompleted = view.findViewById<android.widget.TextView>(R.id.tabCompleted)
        val tabCanceled = view.findViewById<android.widget.TextView>(R.id.tabCanceled)
        
        val tabs = listOf(tabAll, tabUncompleted, tabPending, tabCompleted, tabCanceled)
        val statuses = listOf(ReservationStatus.ALL, ReservationStatus.UNCOMPLETED, ReservationStatus.PENDING, ReservationStatus.COMPLETED, ReservationStatus.CANCELED)
        
        fun updateTabSelection(selectedIndex: Int) {
            tabs.forEachIndexed { index, tab ->
                if (index == selectedIndex) {
                    // Selected tab - highlighted style
                    tab.setBackgroundColor(android.graphics.Color.parseColor("#2D6C8C"))
                    tab.setTextColor(android.graphics.Color.WHITE)
                } else {
                    // Unselected tab - default style
                    tab.background = context?.getDrawable(R.drawable.bg_tab_chip)
                    tab.setTextColor(android.graphics.Color.parseColor("#2D6C8C"))
                }
            }
        }
        
        tabAll.setOnClickListener {
            currentFilter = ReservationStatus.ALL
            updateTabSelection(0)
            filterReservations()
        }
        
        tabUncompleted.setOnClickListener {
            currentFilter = ReservationStatus.UNCOMPLETED
            updateTabSelection(1)
            filterReservations()
        }
        
        tabPending.setOnClickListener {
            currentFilter = ReservationStatus.PENDING
            updateTabSelection(2)
            filterReservations()
        }
        
        tabCompleted.setOnClickListener {
            currentFilter = ReservationStatus.COMPLETED
            updateTabSelection(3)
            filterReservations()
        }
        
        tabCanceled.setOnClickListener {
            currentFilter = ReservationStatus.CANCELED
            updateTabSelection(4)
            filterReservations()
        }
        
        // Set initial selection
        updateTabSelection(0)
    }

    private fun setupSearch(view: View) {
        val etSearch = view.findViewById<android.widget.EditText>(R.id.etSearch)
        
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                filterReservations()
            }
        })
    }

    private fun filterReservations() {
        val filtered = allReservations.filter { reservation ->
            // Filter by status based on action
            val statusMatch = when (currentFilter) {
                ReservationStatus.ALL -> true
                ReservationStatus.PENDING -> reservation.action.equals("Payment", ignoreCase = true)
                ReservationStatus.UNCOMPLETED -> reservation.action.equals("Check-in", ignoreCase = true) || 
                                                 reservation.action.equals("Check-out", ignoreCase = true)
                ReservationStatus.COMPLETED -> reservation.status == ReservationStatus.COMPLETED
                ReservationStatus.CANCELED -> reservation.status == ReservationStatus.CANCELED
            }
            
            // Filter by search query
            val searchMatch = searchQuery.isEmpty() || 
                reservation.reservationId.lowercase().contains(searchQuery) ||
                reservation.line1.lowercase().contains(searchQuery) ||
                reservation.line2.lowercase().contains(searchQuery) ||
                reservation.line3.lowercase().contains(searchQuery)
            
            statusMatch && searchMatch
        }
        
        reservationAdapter.updateData(filtered.toMutableList())
    }

    private fun placeholderItems(): List<ReservationUi> = listOf(
        ReservationUi("R8ZZPQR7", "Deposit paid", "Check-in: 20/09/1025 - Check-out: 22/09/2025", "Payment method: Online", "Guest name: Harper", "Check-in", HeaderColor.BLUE, ReservationStatus.UNCOMPLETED),
        ReservationUi("R8ZZPQR8", "Paid", "Check-in: 20/09/1025 - check-out: 22/09/2025", "Payment method: Online", "Guest name: Lily\nNumber of guest: 2", "Check-out", HeaderColor.GREEN, ReservationStatus.UNCOMPLETED),
        ReservationUi("R8ZZPQR9", "", "Check-in: 20/09/1025 - check-out: 22/09/2025", "Payment method: Online", "Guest name: Cap", "Payment", HeaderColor.YELLOW, ReservationStatus.PENDING),
        ReservationUi("R8ZZPQR0", "Paid", "Check-in: 20/09/1025 - check-out: 22/09/2025", "Payment method: Online", "Guest name: Ahri", "Check-in", HeaderColor.GREEN, ReservationStatus.COMPLETED),
        ReservationUi("R9ABC123", "Cancelled", "Check-in: 15/09/1025 - check-out: 17/09/2025", "Payment method: Cash", "Guest name: Bob", "Payment", HeaderColor.RED, ReservationStatus.CANCELED)
    )
}

data class ReservationUi(
    val reservationId: String,
    val badge: String,
    val line1: String,
    val line2: String,
    val line3: String,
    val action: String,
    val headerColor: HeaderColor,
    val status: ReservationStatus = ReservationStatus.UNCOMPLETED
)

enum class ReservationStatus {
    ALL, UNCOMPLETED, PENDING, COMPLETED, CANCELED
}

enum class HeaderColor { BLUE, GREEN, YELLOW, RED }

class ReservationAdapter(private val items: MutableList<ReservationUi>) : RecyclerView.Adapter<ReservationViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reservation_card, parent, false)
        return ReservationViewHolder(v) { position ->
            val current = items[position]
            // Don't allow any actions on canceled reservations
            if (current.status == ReservationStatus.CANCELED) {
                return@ReservationViewHolder
            }
            when (current.action.lowercase()) {
                "check-in" -> showCheckInDialog(v, position)
                "check-out" -> showCheckOutDialog(v, position)
                "payment" -> showPaymentDialog(v, position)
                else -> advanceState(position)
            }
        }
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: ReservationViewHolder, position: Int) = holder.bind(items[position])
    
    fun updateData(newItems: List<ReservationUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun advanceState(position: Int) {
        val current = items[position]
        val next = when (current.action.lowercase()) {
            "check-in" -> current.copy(
                badge = "Paid",
                action = "Check-out",
                headerColor = HeaderColor.GREEN
            )
            "check-out" -> current.copy(
                badge = "",
                action = "Payment",
                headerColor = HeaderColor.YELLOW
            )
            else -> current
        }
        if (next !== current) {
            items[position] = next
            notifyItemChanged(position)
        }
    }

    private fun showCheckInDialog(anchorView: View, position: Int) {
        val ctx = anchorView.context
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_check_in, null, false)
        val etDateTime = dialogView.findViewById<android.widget.EditText>(R.id.etDateTime)
        val etGuests = dialogView.findViewById<android.widget.EditText>(R.id.etGuests)
        val btnMinus = dialogView.findViewById<android.widget.Button>(R.id.btnMinus)
        val btnPlus = dialogView.findViewById<android.widget.Button>(R.id.btnPlus)
        val btnOk = dialogView.findViewById<android.widget.Button>(R.id.btnOk)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        val formatter = java.text.SimpleDateFormat("HH:mm:ss/dd/MM/yyyy", java.util.Locale.getDefault())
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                etDateTime.setText(formatter.format(java.util.Date()))
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tick)
        etGuests.setText("2")

        btnMinus.setOnClickListener {
            val n = (etGuests.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
            etGuests.setText((n - 1).coerceAtLeast(1).toString())
        }
        btnPlus.setOnClickListener {
            val n = etGuests.text.toString().toIntOrNull() ?: 1
            etGuests.setText((n + 1).toString())
        }

        val alert = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        btnOk.setOnClickListener {
            // Advance state after confirmation
            advanceState(position)
            try { handler.removeCallbacksAndMessages(null) } catch (_: Exception) {}
            alert.dismiss()
        }
        btnCancel.setOnClickListener {
            try { handler.removeCallbacksAndMessages(null) } catch (_: Exception) {}
            alert.dismiss() 
        }

        alert.show()
    }

    private fun showCheckOutDialog(anchorView: View, position: Int) {
        val ctx = anchorView.context
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_check_out, null, false)
        val btnConfirm = dialogView.findViewById<android.widget.Button>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        val alert = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        btnConfirm.setOnClickListener {
            // Advance state after confirmation
            advanceState(position)
            alert.dismiss()
        }
        btnCancel.setOnClickListener {
            alert.dismiss()
        }

        alert.show()
    }

    private fun showPaymentDialog(anchorView: View, position: Int) {
        val ctx = anchorView.context
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_payment, null, false)
        val btnConfirm = dialogView.findViewById<android.widget.Button>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        val alert = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        btnConfirm.setOnClickListener {
            // Advance state after confirmation
            advanceState(position)
            alert.dismiss()
        }
        btnCancel.setOnClickListener {
            alert.dismiss()
        }

        alert.show()
    }
}

class ReservationViewHolder(view: View, private val onActionClick: (Int) -> Unit) : RecyclerView.ViewHolder(view) {
    private val header: View = view.findViewById(R.id.header)
    private val tvReservationId: android.widget.TextView = view.findViewById(R.id.tvReservationId)
    private val tvStatusBadge: android.widget.TextView = view.findViewById(R.id.tvStatusBadge)
    private val tvLine1: android.widget.TextView = view.findViewById(R.id.tvLine1)
    private val tvLine2: android.widget.TextView = view.findViewById(R.id.tvLine2)
    private val tvLine3: android.widget.TextView = view.findViewById(R.id.tvLine3)
    private val btnAction: android.widget.TextView = view.findViewById(R.id.btnAction)

    fun bind(item: ReservationUi) {
        tvReservationId.text = "Reservation ID: ${item.reservationId}"
        tvStatusBadge.text = item.badge
        tvLine1.text = item.line1
        tvLine2.text = item.line2
        tvLine3.text = item.line3
        
        // Check if reservation is canceled
        val isCanceled = item.status == ReservationStatus.CANCELED
        
        // Set button text: "Cancelled" for canceled reservations, otherwise use action
        btnAction.text = if (isCanceled) "Cancelled" else item.action
        
        // Disable button and make it look frozen for canceled reservations
        if (isCanceled) {
            btnAction.isEnabled = false
            btnAction.isClickable = false
            btnAction.alpha = 0.5f // Make it look disabled/frozen
            btnAction.setOnClickListener(null) // Remove click listener
        } else {
            btnAction.isEnabled = true
            btnAction.isClickable = true
            btnAction.alpha = 1.0f
            btnAction.setOnClickListener {
                onActionClick(bindingAdapterPosition)
            }
        }
        
        header.setBackgroundColor(
            when (item.headerColor) {
                HeaderColor.BLUE -> android.graphics.Color.parseColor("#D3E7F6")
                HeaderColor.GREEN -> android.graphics.Color.parseColor("#CDEFD7")
                HeaderColor.YELLOW -> android.graphics.Color.parseColor("#F7E7A8")
                HeaderColor.RED -> android.graphics.Color.parseColor("#841919") //
            }
        )
    }
}




