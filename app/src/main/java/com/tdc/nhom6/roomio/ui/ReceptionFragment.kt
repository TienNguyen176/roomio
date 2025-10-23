package com.tdc.nhom6.roomio.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R

class ReceptionFragment : Fragment() {
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
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = ReservationAdapter(placeholderItems())
    }

    private fun placeholderItems(): List<ReservationUi> = listOf(
        ReservationUi("R8ZZPQR7", "Deposit paid", "Check-in: 20/09/1025 - Check-out: 22/09/2025", "Payment method: Online", "Guest name: Harper", "Check-in", HeaderColor.BLUE),
        ReservationUi("R8ZZPQR7", "Paid", "Check-in: 20/09/1025 - check-out: 22/09/2025", "Payment method: Online", "Guest name: Lily\nNumber of guest: 2", "Check-out", HeaderColor.GREEN),
        ReservationUi("R8ZZPQR7", "", "Check-in: 20/09/1025 - check-out: 22/09/2025", "Payment method: Online", "Guest name: Cap", "Payment", HeaderColor.YELLOW),
        ReservationUi("R8ZZPQR7", "Paid", "Check-in: 20/09/1025 - check-out: 22/09/2025", "Payment method: Online", "Guest name: Ahri", "Check-in", HeaderColor.GREEN)
    )
}

data class ReservationUi(
    val reservationId: String,
    val badge: String,
    val line1: String,
    val line2: String,
    val line3: String,
    val action: String,
    val headerColor: HeaderColor
)

enum class HeaderColor { BLUE, GREEN, YELLOW }

class ReservationAdapter(private val items: List<ReservationUi>) : RecyclerView.Adapter<ReservationViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reservation_card, parent, false)
        return ReservationViewHolder(v)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: ReservationViewHolder, position: Int) = holder.bind(items[position])
}

class ReservationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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
        btnAction.text = item.action
        header.setBackgroundColor(
            when (item.headerColor) {
                HeaderColor.BLUE -> android.graphics.Color.parseColor("#D3E7F6")
                HeaderColor.GREEN -> android.graphics.Color.parseColor("#CDEFD7")
                HeaderColor.YELLOW -> android.graphics.Color.parseColor("#F7E7A8")
            }
        )
    }
}



