package com.broadbandlifestyle.driverapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

// Import from common module
import com.broadbandlifestyle.common.WeeklyEarning
import com.broadbandlifestyle.common.DeliveryEarning

// Wrapper class for expandable state
data class ExpandableWeeklyEarning(
    val weeklyEarning: WeeklyEarning,
    var isExpanded: Boolean = false
) {
    // Delegate properties to the wrapped WeeklyEarning
    val year get() = weeklyEarning.year
    val week get() = weeklyEarning.week
    val weekStart get() = weeklyEarning.weekStart
    val weekEnd get() = weeklyEarning.weekEnd
    val deliveryCount get() = weeklyEarning.deliveryCount
    val weekTotal get() = weeklyEarning.weekTotal
    val totalDeliveryFees get() = weeklyEarning.totalDeliveryFees
    val deliveries get() = weeklyEarning.deliveries
}

class WeeklyEarningsAdapter(
    private val onWeekClick: (ExpandableWeeklyEarning) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var weeklyEarnings = listOf<ExpandableWeeklyEarning>()
    private val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

    companion object {
        private const val TYPE_WEEK_HEADER = 0
        private const val TYPE_DELIVERY_ITEM = 1
    }

    fun submitList(list: List<ExpandableWeeklyEarning>) {
        weeklyEarnings = list
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return findItemPosition(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_WEEK_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_week_header, parent, false)
                WeekHeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_delivery_earning, parent, false)
                DeliveryViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is WeekHeaderViewHolder -> {
                val (weekIndex, week) = findWeekAtPosition(position)
                holder.bind(week, weekIndex)
                holder.itemView.setOnClickListener {
                    onWeekClick(week)
                }
            }
            is DeliveryViewHolder -> {
                val (_, week, deliveryIndex) = findDeliveryAtPosition(position)
                val delivery = week.deliveries[deliveryIndex]
                holder.bind(delivery)
            }
        }
    }

    override fun getItemCount(): Int {
        var count = 0
        weeklyEarnings.forEach { week ->
            count++ // Week header
            if (week.isExpanded) {
                count += week.deliveries.size // Deliveries if expanded
            }
        }
        return count
    }

    private fun findItemPosition(position: Int): Int {
        var currentPos = 0
        weeklyEarnings.forEachIndexed { weekIndex, week ->
            if (currentPos == position) return TYPE_WEEK_HEADER
            currentPos++

            if (week.isExpanded) {
                repeat(week.deliveries.size) { deliveryIndex ->
                    if (currentPos == position) return TYPE_DELIVERY_ITEM
                    currentPos++
                }
            }
        }
        return TYPE_WEEK_HEADER
    }

    private fun findWeekAtPosition(position: Int): Pair<Int, ExpandableWeeklyEarning> {
        var currentPos = 0
        weeklyEarnings.forEachIndexed { index, week ->
            if (currentPos == position) return Pair(index, week)
            currentPos++
            if (week.isExpanded) {
                currentPos += week.deliveries.size
            }
        }
        return Pair(0, weeklyEarnings[0])
    }

    private fun findDeliveryAtPosition(position: Int): Triple<Int, ExpandableWeeklyEarning, Int> {
        var currentPos = 0
        weeklyEarnings.forEachIndexed { weekIndex, week ->
            currentPos++ // Skip week header
            if (week.isExpanded) {
                week.deliveries.forEachIndexed { deliveryIndex, _ ->
                    if (currentPos == position) {
                        return Triple(weekIndex, week, deliveryIndex)
                    }
                    currentPos++
                }
            }
        }
        return Triple(0, weeklyEarnings[0], 0)
    }

    class WeekHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtWeekRange: TextView = itemView.findViewById(R.id.txtWeekRange)
        private val txtDeliveryCount: TextView = itemView.findViewById(R.id.txtDeliveryCount)
        private val txtWeekTotal: TextView = itemView.findViewById(R.id.txtWeekTotal)
        private val expandIcon: TextView = itemView.findViewById(R.id.expandIcon)

        fun bind(week: ExpandableWeeklyEarning, index: Int) {
            val startDate = week.weekStart?.let { parseDate(it) } ?: "Unknown"
            val endDate = week.weekEnd?.let { parseDate(it) } ?: "Unknown"

            txtWeekRange.text = "$startDate - $endDate"
            txtDeliveryCount.text = "${week.deliveryCount} deliveries"
            txtWeekTotal.text = "R%.2f".format(week.weekTotal)
            expandIcon.text = if (week.isExpanded) "▼" else "▶"
        }

        private fun parseDate(dateString: String): String {
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = format.parse(dateString.split("T")[0])
                SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
            } catch (e: Exception) {
                dateString
            }
        }
    }

    class DeliveryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtRestaurant: TextView = itemView.findViewById(R.id.txtRestaurant)
        private val txtOrderNumber: TextView = itemView.findViewById(R.id.txtOrderNumber)
        private val txtDeliveryFee: TextView = itemView.findViewById(R.id.txtDeliveryFee)
        private val txtTotalEarned: TextView = itemView.findViewById(R.id.txtTotalEarned)
        private val txtDateTime: TextView = itemView.findViewById(R.id.txtDateTime)

        fun bind(delivery: DeliveryEarning) {
            txtRestaurant.text = delivery.restaurantName ?: "Unknown Restaurant"
            txtOrderNumber.text = "Order #${delivery.orderNumber ?: delivery.orderId}"
            txtDeliveryFee.text = "Delivery fee: R%.2f".format(delivery.deliveryFee)
            txtTotalEarned.text = "+R%.2f".format(delivery.totalEarned)

            delivery.createdAt?.let {
                txtDateTime.text = formatDateTime(it)
            }
        }

        private fun formatDateTime(dateString: String): String {
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = format.parse(dateString)
                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
            } catch (e: Exception) {
                dateString
            }
        }
    }
}