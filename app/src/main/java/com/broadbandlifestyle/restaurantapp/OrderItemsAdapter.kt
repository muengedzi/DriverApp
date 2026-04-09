package com.broadbandlifestyle.restaurantapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.broadbandlifestyle.common.OrderItem
import com.broadbandlifestyle.driverapp.R

class OrderItemsAdapter : RecyclerView.Adapter<OrderItemsAdapter.ItemViewHolder>() {

    private var items = listOf<OrderItem>()

    fun submitList(newItems: List<OrderItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_item, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtQuantity: TextView = itemView.findViewById(R.id.txtQuantity)
        private val txtItemName: TextView = itemView.findViewById(R.id.txtItemName)
        private val txtItemSubtotal: TextView = itemView.findViewById(R.id.txtItemSubtotal)

        fun bind(item: OrderItem) {
            txtQuantity.text = "${item.quantity}x"
            txtItemName.text = item.name
            txtItemSubtotal.text = "R${String.format("%.2f", item.subtotal)}"

            // Show special instructions if present
            if (!item.special_instructions.isNullOrEmpty()) {
                txtItemName.text = "${item.name} (${item.special_instructions})"
            }
        }
    }
}