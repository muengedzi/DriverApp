package com.broadbandlifestyle.customerapp

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.broadbandlifestyle.common.CartManager
import com.broadbandlifestyle.common.Product
import com.broadbandlifestyle.driverapp.R
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
        val isOutOfStock = product.stockQuantity <= 0
        
        when {
            isOutOfStock -> {
                holder.outOfStockOverlay.visibility = View.VISIBLE
                holder.tvOutOfStockLabel.visibility = View.VISIBLE
                holder.tvStockWarning.visibility = View.GONE
                
                val matrix = ColorMatrix()
                matrix.setSaturation(0f)
                holder.imgProduct.colorFilter = ColorMatrixColorFilter(matrix)
                holder.tvName.alpha = 0.5f
                holder.tvPrice.alpha = 0.5f
                
                holder.btnAdd.isEnabled = false
                holder.btnAdd.alpha = 0.5f
                holder.btnAdd.text = "Out of Stock"
            }
            product.stockQuantity < 5 -> {
                holder.outOfStockOverlay.visibility = View.GONE
                holder.tvOutOfStockLabel.visibility = View.GONE
                holder.tvStockWarning.visibility = View.VISIBLE
                holder.tvStockWarning.text = "Low Stock: ${product.stockQuantity} left"
                
                holder.imgProduct.colorFilter = null
                holder.tvName.alpha = 1.0f
                holder.tvPrice.alpha = 1.0f
                
                holder.btnAdd.isEnabled = true
                holder.btnAdd.alpha = 1.0f
                holder.btnAdd.text = "Add to Cart"
            }
            else -> {
                holder.outOfStockOverlay.visibility = View.GONE
                holder.tvOutOfStockLabel.visibility = View.GONE
                holder.tvStockWarning.visibility = View.GONE
                
                holder.imgProduct.colorFilter = null
                holder.tvName.alpha = 1.0f
                holder.tvPrice.alpha = 1.0f
                
                holder.btnAdd.isEnabled = true
                holder.btnAdd.alpha = 1.0f
                holder.btnAdd.text = "Add to Cart"
            }
        }

        holder.imgProduct.setOnClickListener {
            showProductDetailDialog(holder.itemView.context, product)
        }

        holder.btnAdd.setOnClickListener {
            addToCart(holder.itemView.context, product)
        }
    }

    private fun addToCart(context: android.content.Context, product: Product) {
        if (product.stockQuantity > 0) {
            CartManager.addItem(product)
            Toast.makeText(context, "Added: ${product.name}", Toast.LENGTH_SHORT).show()
            onProductAdded()
        } else {
            Toast.makeText(context, "Sorry, this item is out of stock", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProductDetailDialog(context: android.content.Context, product: Product) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_product_detail, null)
        
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        // Initialize dialog views
        val imgProduct: ImageView = dialogView.findViewById(R.id.dialogProductImage)
        val btnClose: ImageButton = dialogView.findViewById(R.id.btnCloseDialog)
        val tvName: TextView = dialogView.findViewById(R.id.dialogProductName)
        val tvPrice: TextView = dialogView.findViewById(R.id.dialogProductPrice)
        val tvDescription: TextView = dialogView.findViewById(R.id.dialogProductDescription)
        val tvStock: TextView = dialogView.findViewById(R.id.dialogProductStock)
        val btnAdd: MaterialButton = dialogView.findViewById(R.id.dialogBtnAddToCart)
        val outOfStockOverlay: View = dialogView.findViewById(R.id.dialogOutOfStockOverlay)
        val outOfStockLabel: TextView = dialogView.findViewById(R.id.dialogTvOutOfStockLabel)

        // Set data
        tvName.text = product.name
        tvPrice.text = "R${String.format("%.2f", product.price)}"
        tvDescription.text = product.description ?: "No description available for this item."
        
        Glide.with(context)
            .load(product.imageUrl)
            .placeholder(R.drawable.ic_launcher_background)
            .centerCrop()
            .into(imgProduct)

        val isOutOfStock = product.stockQuantity <= 0
        if (isOutOfStock) {
            outOfStockOverlay.visibility = View.VISIBLE
            outOfStockLabel.visibility = View.VISIBLE
            tvStock.text = "Status: Out of Stock"
            tvStock.setTextColor(Color.RED)
            btnAdd.isEnabled = false
            btnAdd.alpha = 0.5f
            btnAdd.text = "Out of Stock"
            
            val matrix = ColorMatrix()
            matrix.setSaturation(0f)
            imgProduct.colorFilter = ColorMatrixColorFilter(matrix)
        } else {
            outOfStockOverlay.visibility = View.GONE
            outOfStockLabel.visibility = View.GONE
            tvStock.text = "Status: In Stock (${product.stockQuantity} available)"
            tvStock.setTextColor(Color.parseColor("#4CAF50"))
            btnAdd.isEnabled = true
            btnAdd.alpha = 1.0f
            btnAdd.text = "Add to Cart"
            imgProduct.colorFilter = null
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        
        btnAdd.setOnClickListener {
            addToCart(context, product)
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun getItemCount() = products.size
}
