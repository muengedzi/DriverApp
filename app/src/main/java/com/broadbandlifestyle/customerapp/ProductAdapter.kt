package com.broadbandlifestyle.driverapp

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.broadbandlifestyle.common.CartManager
import com.broadbandlifestyle.common.Product
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

class ProductAdapter(
    private val products: List<Product>,
    private val onProductAdded: () -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgProduct: ImageView = view.findViewById(R.id.imgProduct)
        val tvName: TextView = view.findViewById(R.id.tvProductName)
        val tvPrice: TextView = view.findViewById(R.id.tvProductPrice)
        val btnAdd: MaterialButton = view.findViewById(R.id.btnAddToCart)
        val tvStockWarning: TextView = view.findViewById(R.id.tvStockWarning)
        val outOfStockOverlay: View = view.findViewById(R.id.outOfStockOverlay)
        val tvOutOfStockLabel: TextView = view.findViewById(R.id.tvOutOfStockLabel)
        val layoutProductContent: View = view.findViewById(R.id.layoutProductContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]

        holder.tvName.text = product.name
        holder.tvPrice.text = "R${String.format("%.2f", product.price)}"

        Glide.with(holder.itemView.context)
            .load(product.imageUrl)
            .placeholder(R.drawable.ic_launcher_background)
            .centerCrop()
            .into(holder.imgProduct)

        // Handle Stock UI
        when {
            product.stockQuantity <= 0 -> {
                // Out of stock: greyed out
                holder.outOfStockOverlay.visibility = View.VISIBLE
                holder.tvOutOfStockLabel.visibility = View.VISIBLE
                holder.tvStockWarning.visibility = View.GONE
                
                // Set grayscale filter to image
                val matrix = ColorMatrix()
                matrix.setSaturation(0f)
                holder.imgProduct.colorFilter = ColorMatrixColorFilter(matrix)
                holder.tvName.alpha = 0.5f
                holder.tvPrice.alpha = 0.5f
            }
            product.stockQuantity < 5 -> {
                // Low stock warning
                holder.outOfStockOverlay.visibility = View.GONE
                holder.tvOutOfStockLabel.visibility = View.GONE
                holder.tvStockWarning.visibility = View.VISIBLE
                holder.tvStockWarning.text = "Low Stock: ${product.stockQuantity} left"
                
                // Reset visual state
                holder.imgProduct.colorFilter = null
                holder.tvName.alpha = 1.0f
                holder.tvPrice.alpha = 1.0f
            }
            else -> {
                // Normal stock
                holder.outOfStockOverlay.visibility = View.GONE
                holder.tvOutOfStockLabel.visibility = View.GONE
                holder.tvStockWarning.visibility = View.GONE
                
                // Reset visual state
                holder.imgProduct.colorFilter = null
                holder.tvName.alpha = 1.0f
                holder.tvPrice.alpha = 1.0f
            }
        }

        holder.btnAdd.setOnClickListener {
            CartManager.addItem(product)
            val msg = if (product.stockQuantity <= 0) {
                "Added (Backorder): ${product.name}"
            } else {
                "Added: ${product.name}"
            }
            Toast.makeText(holder.itemView.context, msg, Toast.LENGTH_SHORT).show()
            onProductAdded()
        }
    }

    override fun getItemCount() = products.size
}
