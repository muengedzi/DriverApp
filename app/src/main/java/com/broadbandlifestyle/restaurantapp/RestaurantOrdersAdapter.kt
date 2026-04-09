package com.broadbandlifestyle.restaurantapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.broadbandlifestyle.common.RestaurantOrder
import com.broadbandlifestyle.driverapp.R
import java.text.SimpleDateFormat
import java.util.*

class RestaurantOrdersAdapter(
    private val onOrderClick: (RestaurantOrder) -> Unit
) : RecyclerView.Adapter<RestaurantOrdersAdapter.OrderViewHolder>() {

    private var orders = listOf<RestaurantOrder>()
    private val timeFormat = SimpleDateFormat("HH:mm, MMM dd", Locale.getDefault())

    fun submitList(newOrders: List<RestaurantOrder>) {
        orders = newOrders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_restaurant_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardOrder: CardView = itemView.findViewById(R.id.cardOrder)
        private val txtOrderNumber: TextView = itemView.findViewById(R.id.txtOrderNumber)
        private val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        private val txtStatus: TextView = itemView.findViewById(R.id.txtStatus)
        private val txtItemCount: TextView = itemView.findViewById(R.id.txtItemCount)
        private val txtTotal: TextView = itemView.findViewById(R.id.txtTotal)
        private val txtCustomerName: TextView = itemView.findViewById(R.id.txtCustomerName)

        fun bind(order: RestaurantOrder) {
            txtOrderNumber.text = order.order_number
            order.created_at?.let {
                try {
                    val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(it)
                    txtTime.text = timeFormat.format(date)
                } catch (e: Exception) {
                    txtTime.text = it
                }
            }
            txtStatus.text = order.status.uppercase()
            txtItemCount.text = "${order.item_count} items"
            txtTotal.text = "R${String.format("%.2f", order.final_amount)}"
            txtCustomerName.text = order.customer_name ?: "Customer"

            // Set status color
            when (order.status.lowercase()) {
                "pending" -> txtStatus.setBackgroundResource(R.drawable.bg_status_pending)
                "preparing" -> txtStatus.setBackgroundResource(R.drawable.bg_status_preparing)
                "ready" -> txtStatus.setBackgroundResource(R.drawable.bg_status_ready)
                else -> txtStatus.setBackgroundResource(R.drawable.bg_status_pending)
            }

            cardOrder.setOnClickListener {
                onOrderClick(order)
            }
        }
    }
}