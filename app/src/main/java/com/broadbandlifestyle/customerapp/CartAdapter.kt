package com.broadbandlifestyle.customerapp

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.broadbandlifestyle.common.CartManager
import com.broadbandlifestyle.common.Product
import com.broadbandlifestyle.driverapp.R
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

class CartAdapter(
    private var products: MutableList<Product>,
    private val isCheckoutMode: Boolean = false,
    private val onCartChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    class CartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgProduct: ImageView = view.findViewById(R.id.cartImgProduct)
        val tvName: TextView = view.findViewById(R.id.cartTvName)
        val tvPrice: TextView = view.findViewById(R.id.cartTvPrice)
        val tvQty: TextView = view.findViewById(R.id.cartTvQty)
        val btnPlus: MaterialButton = view.findViewById(R.id.cartBtnPlus)
        val btnMinus: MaterialButton = view.findViewById(R.id.cartBtnMinus)
        val btnDelete: ImageView = view.findViewById(R.id.cartBtnDelete)
        val editContainer: LinearLayout = view.findViewById(R.id.cartEditContainer)
        val tvCheckoutQty: TextView = view.findViewById(R.id.tvCheckoutQty)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val product = products[position]
        val qty = CartManager.getQuantity(product.id)

        holder.tvName.text = product.name
        holder.tvPrice.text = "R${String.format("%.2f", product.price * qty)}"

        Glide.with(holder.itemView.context)
            .load(product.imageUrl)
            .placeholder(R.drawable.ic_launcher_background)
            .into(holder.imgProduct)

        if (isCheckoutMode) {
            holder.editContainer.visibility = View.GONE
            holder.tvCheckoutQty.visibility = View.VISIBLE
            holder.tvCheckoutQty.text = "Qty: $qty"
        } else {
            holder.editContainer.visibility = View.VISIBLE
            holder.tvCheckoutQty.visibility = View.GONE
            holder.tvQty.text = qty.toString()

            holder.btnPlus.setOnClickListener {
                updateRunnable?.let { handler.removeCallbacks(it) }
                CartManager.addItem(product)
                notifyItemChanged(position)
                updateRunnable = Runnable { onCartChanged?.invoke() }
                handler.postDelayed(updateRunnable!!, 500)
            }

            holder.btnMinus.setOnClickListener {
                updateRunnable?.let { handler.removeCallbacks(it) }
                CartManager.removeItem(product.id)
                if (CartManager.getQuantity(product.id) <= 0) {
                    products.removeAt(position)
                    notifyDataSetChanged()
                } else {
                    notifyItemChanged(position)
                }
                updateRunnable = Runnable { onCartChanged?.invoke() }
                handler.postDelayed(updateRunnable!!, 500)
            }

            holder.btnDelete.setOnClickListener {
                CartManager.deleteItem(product.id)
                products.removeAt(position)
                notifyDataSetChanged()
                onCartChanged?.invoke()
            }
        }
    }

    override fun getItemCount() = products.size
}