package com.tdc.nhom6.roomio.adapters

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.drawable.DrawableCompat
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.ServiceExtraFeeActivity
import com.tdc.nhom6.roomio.models.HeaderColor
import com.tdc.nhom6.roomio.models.ReservationStatus
import com.tdc.nhom6.roomio.models.ReservationUi

class ReservationAdapter(private val items: MutableList<ReservationUi>) :
    RecyclerView.Adapter<ReservationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reservation_card, parent, false)
        return ReservationViewHolder(view) { position ->
            val current = items[position]
            if (current.status == ReservationStatus.CANCELED || current.status == ReservationStatus.COMPLETED) {
                return@ReservationViewHolder
            }
            when (current.action.lowercase()) {
                "check-in" -> showCheckInDialog(view, position)
                "check-out" -> showCheckOutDialog(view, position)
                "payment" -> showPaymentDialog(view, position)
                else -> advanceState(position)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ReservationViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun updateData(newItems: List<ReservationUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun advanceState(position: Int) {
        val current = items[position]
        val next = when (current.action.lowercase()) {
            "check-in" -> current.copy(
                badge = current.badge.ifBlank { "" },
                action = "Check-out",
                headerColor = HeaderColor.GREEN,
                status = ReservationStatus.UNCOMPLETED
            )

            "check-out" -> current.copy(
                badge = "Pending payment",
                action = "Payment",
                headerColor = HeaderColor.YELLOW,
                status = ReservationStatus.PENDING
            )

            else -> current
        }
        if (next !== current) {
            items[position] = next
            notifyItemChanged(position)
        }
    }

    private fun showCheckInDialog(anchorView: View, position: Int) {
        val context = anchorView.context
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_check_in, null, false)
        val etDateTime = dialogView.findViewById<android.widget.EditText>(R.id.etDateTime)
        val etGuests = dialogView.findViewById<android.widget.EditText>(R.id.etGuests)
        val btnMinus = dialogView.findViewById<android.widget.Button>(R.id.btnMinus)
        val btnPlus = dialogView.findViewById<android.widget.Button>(R.id.btnPlus)
        val btnOk = dialogView.findViewById<android.widget.Button>(R.id.btnOk)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        val formatter = java.text.SimpleDateFormat("HH:mm:ss/dd/MM/yyyy", java.util.Locale.getDefault())
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                etDateTime.setText(formatter.format(java.util.Date()))
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tick)
        val reservedGuests = items.getOrNull(position)?.numberGuest?.coerceAtLeast(1) ?: 1
        etGuests.setText(reservedGuests.toString())

        btnMinus.setOnClickListener {
            val value = (etGuests.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
            etGuests.setText((value - 1).coerceAtLeast(1).toString())
        }
        btnPlus.setOnClickListener {
            val value = etGuests.text.toString().toIntOrNull() ?: 1
            etGuests.setText((value + 1).toString())
        }

        val alert = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        alert.setOnDismissListener {
            try {
                handler.removeCallbacksAndMessages(null)
            } catch (_: Exception) {
            }
        }

        btnOk.setOnClickListener {
            val dateTimeText = etDateTime.text.toString()
            val actualCheckInTime = try {
                val formatterInstance = java.text.SimpleDateFormat("HH:mm:ss/dd/MM/yyyy", java.util.Locale.getDefault())
                val date = formatterInstance.parse(dateTimeText)
                date?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }

            advanceState(position)
            try {
                val docId = items[position].documentId
                Firebase.firestore.collection("bookings").document(docId)
                    .update(
                        mapOf(
                            "status" to "checked_in",
                            "checkInDateActual" to actualCheckInTime
                        )
                    )
            } catch (_: Exception) {
            }
            alert.dismiss()
        }
        btnCancel.setOnClickListener {
            alert.dismiss()
        }

        alert.show()
    }

    private fun showCheckOutDialog(anchorView: View, position: Int) {
        val context = anchorView.context
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_check_out, null, false)
        val btnConfirm = dialogView.findViewById<android.widget.Button>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        val actualCheckOutTime = System.currentTimeMillis()

        val alert = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        btnConfirm.setOnClickListener {
            val current = items[position]
            val next = current.copy(
                badge = "Pending payment",
                action = "Payment",
                headerColor = HeaderColor.YELLOW,
                status = ReservationStatus.PENDING
            )
            items[position] = next
            notifyItemChanged(position)
            try {
                val docId = items[position].documentId
                Firebase.firestore.collection("bookings").document(docId)
                    .update(
                        mapOf(
                            "status" to "pending_payment",
                            "checkOutDateActual" to actualCheckOutTime
                        )
                    )
                    .addOnSuccessListener {
                        android.util.Log.d("Reception", "Checkout updated for $docId")
                    }
                    .addOnFailureListener { error ->
                        android.util.Log.e("Reception", "Failed to update checkout for $docId", error)
                        Toast.makeText(context, "Failed to update checkout", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                android.util.Log.e("Reception", "Exception updating checkout", e)
            }
            alert.dismiss()
        }
        btnCancel.setOnClickListener {
            alert.dismiss()
        }

        alert.show()
    }

    private fun showPaymentDialog(anchorView: View, position: Int) {
        val context = anchorView.context
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_payment, null, false)
        val btnConfirm = dialogView.findViewById<android.widget.Button>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        val alert = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        btnConfirm.setOnClickListener {
            advanceState(position)
            try {
                val current = items[position]
                val intent = Intent(context, ServiceExtraFeeActivity::class.java).apply {
                    putExtra("ROOM_ID", current.reservationId)
                    putExtra("RESERVATION_ID", current.reservationId)
                    val guestName = current.line3.substringAfter(":", current.line3).trim()
                    putExtra("GUEST_NAME", guestName)
                    putExtra("GUEST_PHONE", current.guestPhone ?: "")
                    putExtra("GUEST_EMAIL", current.guestEmail ?: "")
                    putExtra("CHECK_IN_TEXT", current.checkInText)
                    putExtra("CHECK_OUT_TEXT", current.checkOutText)
                    putExtra("CHECK_IN", current.checkInText)
                    putExtra("CHECK_OUT", current.checkOutText)
                    val checkInMillis = current.checkInMillis?.toDate()?.time ?: -1L
                    val checkOutMillis = current.checkOutMillis?.toDate()?.time ?: -1L
                    putExtra("CHECK_IN_MILLIS", checkInMillis)
                    putExtra("CHECK_OUT_MILLIS", checkOutMillis)
                    putExtra("RESERVATION_AMOUNT", current.totalFinalAmount)
                    putExtra("BOOKING_ID", current.documentId)
                    putExtra("ROOM_TYPE_ID", current.roomTypeId ?: "")
                    putExtra("HOTEL_ID", current.hotelId ?: "")
                    putExtra("ROOM_TYPE_NAME", current.roomType)
                    putExtra("DISCOUNT_LABEL", current.discountLabel)
                    putExtra("GUESTS_COUNT", current.numberGuest)
                }
                context.startActivity(intent)
                val docId = items[position].documentId
                Firebase.firestore.collection("bookings").document(docId)
                    .update("status", "pending_payment")
            } catch (_: Exception) {
            }
            alert.dismiss()
        }
        btnCancel.setOnClickListener {
            alert.dismiss()
        }

        alert.show()
    }
}

class ReservationViewHolder(view: View, private val onActionClick: (Int) -> Unit) :
    RecyclerView.ViewHolder(view) {

    private val header: View = view.findViewById(R.id.header)
    private val tvReservationId: android.widget.TextView = view.findViewById(R.id.tvReservationId)
    private val tvStatusBadge: android.widget.TextView = view.findViewById(R.id.tvStatusBadge)
    private val tvLine1: android.widget.TextView = view.findViewById(R.id.tvLine1)
    private val tvLine3: android.widget.TextView = view.findViewById(R.id.tvLine3)
    private val tvNumberGuest: android.widget.TextView = view.findViewById(R.id.tvNumberGuest)
    private val tvRoomType: android.widget.TextView = view.findViewById(R.id.tvRoomType)
    private val btnAction: android.widget.TextView = view.findViewById(R.id.btnAction)

    fun bind(item: ReservationUi) {
        tvReservationId.text = "Reservation ID: ${item.displayReservationCode}"
        tvStatusBadge.text = item.badge
        tvLine1.text = item.line1
        tvLine3.text = item.line3
        tvNumberGuest.text = "Number of guests: ${item.numberGuest}"
        tvRoomType.text = "Room type: ${item.roomType.takeIf { it.isNotBlank() } ?: "Room"}"

        val isCanceled = item.status == ReservationStatus.CANCELED
        val isCompleted = item.status == ReservationStatus.COMPLETED
        btnAction.text = when {
            isCanceled -> "Cancelled"
            isCompleted -> "Completed"
            else -> item.action
        }

        if (isCanceled || isCompleted) {
            btnAction.isEnabled = false
            btnAction.isClickable = false
            btnAction.alpha = 0.5f
            btnAction.setOnClickListener(null)
        } else {
            btnAction.isEnabled = true
            btnAction.isClickable = true
            btnAction.alpha = 1.0f
            btnAction.setOnClickListener {
                onActionClick(bindingAdapterPosition)
            }
        }

        val headerColorValue = when (item.headerColor) {
            HeaderColor.BLUE -> android.graphics.Color.parseColor("#D3E7F6")
            HeaderColor.GREEN -> android.graphics.Color.parseColor("#CDEFD7")
            HeaderColor.YELLOW -> android.graphics.Color.parseColor("#F7E7A8")
            HeaderColor.RED -> android.graphics.Color.parseColor("#841919")
        }

        header.setBackgroundColor(headerColorValue)
        applyActionButtonColor(headerColorValue)
    }

    private fun applyActionButtonColor(color: Int) {
        val background = btnAction.background ?: run {
            btnAction.setBackgroundColor(color)
            return
        }
        val wrapped = DrawableCompat.wrap(background.mutate())
        DrawableCompat.setTint(wrapped, color)
        btnAction.background = wrapped
    }
}

