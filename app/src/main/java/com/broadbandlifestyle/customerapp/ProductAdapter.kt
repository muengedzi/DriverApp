package com.broadbandlifestyle.driverapp

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
import com.broadbandlifestyle.customerapp.ShopActivity

// Added "val onProductAdded: () -> Unit" to the constructor
class ProductAdapter(
    private val products: List<Product>,
    private val onProductAdded: () -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgProduct: ImageView = view.findViewById(R.id.imgProduct)
        val tvName: TextView = view.findViewById(R.id.tvProductName)
        val tvPrice: TextView = view.findViewById(R.id.tvProductPrice)
        val btnAdd: MaterialButton = view.findViewById(R.id.btnAddToCart)
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

        holder.btnAdd.setOnClickListener {
            CartManager.addItem(product)
            Toast.makeText(holder.itemView.context, "Added: ${product.name}", Toast.LENGTH_SHORT).show()

            // Trigger the callback to update the ShopActivity FAB
            onProductAdded()
        }
    }

    override fun getItemCount() = products.size
}