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
import com.broadbandlifestyle.common.CartManager
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.common.Product
import com.broadbandlifestyle.common.CapsuleNavigationHelper
import com.broadbandlifestyle.driverapp.R

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
        setupCapsuleNavigation()

        fabCheckout.setOnClickListener {
            if (CartManager.getCartCount() > 0) {
                val intent = Intent(this@ShopActivity, CartActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Your cart is empty!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCapsuleNavigation() {
        CapsuleNavigationHelper.setupCapsuleNavigation(
            activity = this,
            menuResId = R.menu.bottom_nav_menu_customer,
            onItemSelected = { itemId ->
                when (itemId) {
                    R.id.nav_shop -> {
                        // Already on shop
                        true
                    }
                    R.id.nav_cart -> {
                        if (CartManager.getCartCount() > 0) {
                            startActivity(Intent(this, CartActivity::class.java))
                        } else {
                            Toast.makeText(this, "Your cart is empty!", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    R.id.nav_orders -> {
                        Toast.makeText(this, "Order History - Coming Soon", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.nav_profile -> {
                        Toast.makeText(this, "Profile - Coming Soon", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
        )
    }

    private fun loadProducts() {
        activityScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getProducts()
                }

                if (response.isSuccessful) {
                    val productList: List<Product> = response.body() ?: emptyList()
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

    fun updateCartFab() {
        val count = CartManager.getCartCount()
        fabCheckout.text = if (count > 0) "Checkout ($count)" else "Checkout"
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
