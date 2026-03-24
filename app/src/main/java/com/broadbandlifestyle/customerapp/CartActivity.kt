package com.broadbandlifestyle.customerapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.broadbandlifestyle.common.*
import com.broadbandlifestyle.driverapp.R
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CartActivity : AppCompatActivity() {

    private lateinit var tvSubtotal: TextView
    private lateinit var tvDeliveryFee: TextView
    private lateinit var tvPlatformFee: TextView
    private lateinit var tvGrandTotal: TextView
    private lateinit var btnCheckout: MaterialButton
    private lateinit var apiService: CustomerApiService
    private lateinit var cartAdapter: CartAdapter

    private var currentSubtotal = 0.0
    private var currentDeliveryFee = 0.0
    private var currentPlatformFee = 0.0
    private var currentTotal = 0.0
    private var restaurantInfo: RestaurantInfo? = null
    private var calculationJob: Job? = null

    private val TAG = "CartActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        setupRetrofit()
        initializeViews()
        setupRecyclerView()
        calculateCartFees()
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(CustomerApiService::class.java)
    }

    private fun initializeViews() {
        tvSubtotal = findViewById(R.id.tvSubtotal)
        tvDeliveryFee = findViewById(R.id.tvDeliveryFee)
        tvPlatformFee = findViewById(R.id.tvPlatformFee)
        tvGrandTotal = findViewById(R.id.tvGrandTotal)
        btnCheckout = findViewById(R.id.btnGoToCheckout)

        btnCheckout.setOnClickListener {
            if (restaurantInfo != null && currentSubtotal < restaurantInfo!!.minOrderAmount) {
                Toast.makeText(this, "Minimum order is R${restaurantInfo!!.minOrderAmount}", Toast.LENGTH_LONG).show()
            } else {
                val intent = Intent(this, CheckoutActivity::class.java).apply {
                    putExtra("TOTAL_PRICE", currentTotal)
                    putExtra("SUBTOTAL", currentSubtotal)
                    putExtra("DELIVERY_FEE", currentDeliveryFee)
                    putExtra("PLATFORM_FEE", currentPlatformFee)
                }
                startActivity(intent)
            }
        }
    }

    private fun setupRecyclerView() {
        val rvCartItems = findViewById<RecyclerView>(R.id.rvCartItems)
        rvCartItems.layoutManager = LinearLayoutManager(this)

        cartAdapter = CartAdapter(
            products = CartManager.getProductsInCart().toMutableList(),
            isCheckoutMode = false,
            onCartChanged = {
                if (CartManager.getCartCount() == 0) finish() else calculateCartFees()
            }
        )
        rvCartItems.adapter = cartAdapter
    }

    private fun calculateCartFees() {
        calculationJob?.cancel()
        val cartList = CartManager.getCartItems().map { (id, qty) -> CartItem(id, qty) }

        if (cartList.isEmpty()) {
            updateUI(0.0, 0.0, 0.0, 0.0, null)
            return
        }

        calculationJob = lifecycleScope.launch {
            delay(300)
            try {
                val response = apiService.calculateCart(CartCalculationRequest(cartList))
                if (response.isSuccessful && response.body()?.success == true) {
                    val r = response.body()!!
                    updateUI(r.subtotal, r.deliveryFee, r.platformFee, r.total, r.restaurant)
                } else {
                    calculateLocally()
                }
            } catch (e: Exception) {
                calculateLocally()
            }
        }
    }

    private fun calculateLocally() {
        var sub = 0.0
        CartManager.getProductsInCart().forEach {
            sub += (it.price * CartManager.getQuantity(it.id))
        }
        updateUI(sub, 0.0, 0.0, sub, null)
    }

    private fun updateUI(sub: Double, del: Double, plat: Double, total: Double, rest: RestaurantInfo?) {
        currentSubtotal = sub
        currentDeliveryFee = del
        currentPlatformFee = plat
        currentTotal = total
        restaurantInfo = rest

        tvSubtotal.text = "R${String.format("%.2f", sub)}"
        tvDeliveryFee.text = "R${String.format("%.2f", del)}"
        tvPlatformFee.text = "R${String.format("%.2f", plat)}"
        tvGrandTotal.text = "R${String.format("%.2f", total)}"
        btnCheckout.isEnabled = total > 0

        val color = if (rest != null && sub < rest.minOrderAmount) android.R.color.holo_red_dark else android.R.color.black
        tvSubtotal.setTextColor(ContextCompat.getColor(this, color))
    }
}