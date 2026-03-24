package com.broadbandlifestyle.customerapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Core Imports
import com.broadbandlifestyle.common.CartManager
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.common.Product
import com.broadbandlifestyle.driverapp.ProductAdapter
import com.broadbandlifestyle.driverapp.R

// 1. THIS IS CRITICAL: Ensure this matches your project folder structure
import com.broadbandlifestyle.customerapp.CartActivity

class ShopActivity : AppCompatActivity() {

    private lateinit var rvProducts: RecyclerView
    private lateinit var fabCheckout: ExtendedFloatingActionButton
    private lateinit var apiService: CustomerApiService
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        rvProducts = findViewById(R.id.rvProducts)
        fabCheckout = findViewById(R.id.fabCheckout)
        rvProducts.layoutManager = GridLayoutManager(this, 2)

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(CustomerApiService::class.java)

        loadProducts()

        fabCheckout.setOnClickListener {
            if (CartManager.getCartCount() > 0) {
                // 2. THIS SYNTAX FIXES THE "TYPE PARAMETER T" ERROR
                val intent = Intent(this@ShopActivity, CartActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Your cart is empty!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadProducts() {
        activityScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getProducts()
                }

                if (response.isSuccessful) {
                    val productList: List<Product> = response.body() ?: emptyList()
                    // 3. This matches the new ProductAdapter constructor
                    rvProducts.adapter = ProductAdapter(productList) {
                        updateCartFab()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@ShopActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateCartFab()
    }

    // Changed to public so it can be accessed if needed, though callback is better
    fun updateCartFab() {
        val count = CartManager.getCartCount()
        fabCheckout.text = if (count > 0) "Checkout ($count)" else "Checkout"
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}