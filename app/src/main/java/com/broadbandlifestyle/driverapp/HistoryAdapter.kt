package com.broadbandlifestyle.driverapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.broadbandlifestyle.common.CompletedOrder

class HistoryAdapter(private val orders: List<CompletedOrder>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtOrderNumber: TextView = view.findViewById(R.id.txtOrderNumber)
        val txtRestaurant: TextView = view.findViewById(R.id.txtRestaurant)
        val txtAmount: TextView = view.findViewById(R.id.txtAmount)
        val txtDate: TextView = view.findViewById(R.id.txtDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val order = orders[position]
        holder.txtOrderNumber.text = "Order #${order.orderNumber ?: "N/A"}"
        holder.txtRestaurant.text = order.restaurant ?: "Restaurant"
        holder.txtAmount.text = "R${"%.2f".format(order.amount ?: 0.0)}"
        holder.txtDate.text = order.date ?: "No Date"
    }

    override fun getItemCount() = orders.size
}
