package com.broadbandlifestyle.customerapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.broadbandlifestyle.common.*
import com.broadbandlifestyle.driverapp.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.broadbandlifestyle.common.Constants.BASE_URL
import java.util.*

class CheckoutActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLat: Double? = null
    private var currentLng: Double? = null
    private lateinit var apiService: CustomerApiService
    private var currentUserId: Int = -1

    // UI Elements for price breakdown - FIXED: Use correct IDs from layout
    private lateinit var tvSubtotal: TextView
    private lateinit var tvDeliveryFee: TextView
    private lateinit var tvPlatformFee: TextView
    private lateinit var tvGrandTotal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        currentUserId = getSharedPreferences("user_prefs", MODE_PRIVATE).getInt("USER_ID", -1)

        if (currentUserId == -1) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()  // FIXED: Now initializes with correct IDs
        setupRetrofit()

        val subtotal = intent.getDoubleExtra("SUBTOTAL", 0.0)
        val deliveryFee = intent.getDoubleExtra("DELIVERY_FEE", 0.0)
        val platformFee = intent.getDoubleExtra("PLATFORM_FEE", 0.0)
        val totalPrice = intent.getDoubleExtra("TOTAL_PRICE", 0.0)

        displayPriceBreakdown(subtotal, deliveryFee, platformFee, totalPrice)
        setupUI()
        setupRecyclerView()
        setupGPSButton()
        setupSubmitButton(subtotal, deliveryFee, platformFee, totalPrice)
    }

    private fun initializeViews() {
        // FIXED: Use the correct IDs from activity_checkout.xml
        tvSubtotal = findViewById(R.id.tvCheckoutSubtotal)
        tvDeliveryFee = findViewById(R.id.tvCheckoutDeliveryFee)
        tvPlatformFee = findViewById(R.id.tvCheckoutPlatformFee)
        tvGrandTotal = findViewById(R.id.tvCheckoutGrandTotal)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(CustomerApiService::class.java)
    }

    private fun displayPriceBreakdown(subtotal: Double, deliveryFee: Double, platformFee: Double, total: Double) {
        // FIXED: Now properly sets text to the correct TextViews
        tvSubtotal.text = "R${String.format("%.2f", subtotal)}"
        tvDeliveryFee.text = "R${String.format("%.2f", deliveryFee)}"
        tvPlatformFee.text = "R${String.format("%.2f", platformFee)}"
        tvGrandTotal.text = "R${String.format("%.2f", total)}"

        Log.d("CHECKOUT", "Displaying fees - Subtotal: $subtotal, Delivery: $deliveryFee, Platform: $platformFee, Total: $total")
    }

    private fun setupUI() {
        val tvTotalItems = findViewById<TextView>(R.id.tvTotalItems)
        val totalPrice = intent.getDoubleExtra("TOTAL_PRICE", 0.0)

        tvTotalItems.text = "Total Items: ${CartManager.getCartCount()}"

        // Optional: Update the total price display if needed
        // The tvTotalPrice TextView is set to visibility="gone" in your layout, so it's hidden
        // If you want to show it, you can uncomment below
        // val tvTotalPrice = findViewById<TextView>(R.id.tvTotalPrice)
        // tvTotalPrice.text = "Estimated Total: R${String.format("%.2f", totalPrice)}"
    }

    private fun setupRecyclerView() {
        val rvCartItems = findViewById<RecyclerView>(R.id.rvCartItems)
        val cartProducts = CartManager.getCartProducts().toMutableList()

        val checkoutAdapter = CartAdapter(
            products = cartProducts,
            isCheckoutMode = true,
            onCartChanged = null
        )

        rvCartItems.layoutManager = LinearLayoutManager(this)
        rvCartItems.adapter = checkoutAdapter
        rvCartItems.isNestedScrollingEnabled = false
    }

    private fun setupGPSButton() {
        val btnUseGPS = findViewById<MaterialButton>(R.id.btnUseGPS)
        val etAddress = findViewById<TextInputEditText>(R.id.etAddress)

        btnUseGPS.setOnClickListener {
            if (checkLocationPermissions()) {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            currentLat = it.latitude
                            currentLng = it.longitude
                            etAddress.setText(getAddressFromCoords(it.latitude, it.longitude))
                        }
                    }
                } catch (e: SecurityException) {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1001
                )
            }
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            1001 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
                    findViewById<MaterialButton>(R.id.btnUseGPS).performClick()
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSubmitButton(subtotal: Double, deliveryFee: Double, platformFee: Double, total: Double) {
        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitOrder)
        val etAddress = findViewById<TextInputEditText>(R.id.etAddress)
        val etInstructions = findViewById<TextInputEditText>(R.id.etInstructions)

        btnSubmit.setOnClickListener {
            val address = etAddress.text.toString().trim()
            val instructions = etInstructions.text.toString().trim()

            if (address.isEmpty()) {
                etAddress.error = "Delivery address is required"
                return@setOnClickListener
            }

            placeOrder(address, instructions, subtotal, deliveryFee, platformFee, total)
        }
    }

    private fun getAddressFromCoords(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) addresses[0].getAddressLine(0) else "Lat: $lat, Lng: $lng"
        } catch (e: Exception) {
            "Lat: $lat, Lng: $lng"
        }
    }

    private fun placeOrder(
        address: String,
        instructions: String,
        subtotal: Double,
        deliveryFee: Double,
        platformFee: Double,
        total: Double
    ) {
        val cartList = CartManager.getCartItems().map { (id, qty) ->
            CartItem(product_id = id, quantity = qty)
        }

        val request = CreateOrderRequest(
            user_id = currentUserId,
            delivery_address = address,
            latitude = currentLat,
            longitude = currentLng,
            instructions = instructions,
            order_type = "delivery",
            cart = cartList
        )

        val btnSubmitOrder = findViewById<MaterialButton>(R.id.btnSubmitOrder)
        btnSubmitOrder.isEnabled = false
        btnSubmitOrder.text = "Processing..."

        lifecycleScope.launch {
            try {
                val response = apiService.placeOrder(request)
                if (response.isSuccessful) {
                    val orderBody = response.body()
                    if (orderBody != null && orderBody.success) {
                        CartManager.clearCart()
                        val intent = Intent(this@CheckoutActivity, OrderSuccessActivity::class.java).apply {
                            putExtra("ORDER_ID", orderBody.order_id)
                            putExtra("ORDER_NUM", orderBody.order_number)
                            putExtra("TOTAL", orderBody.final_amount ?: total)
                            putExtra("SUBTOTAL", subtotal)
                            putExtra("DELIVERY_FEE", deliveryFee)
                            putExtra("PLATFORM_FEE", platformFee)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@CheckoutActivity, orderBody?.message ?: "Failed to place order", Toast.LENGTH_LONG).show()
                        btnSubmitOrder.isEnabled = true
                        btnSubmitOrder.text = "Place Order"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("CHECKOUT_ERROR", "Server Error ${response.code()}: $errorBody")
                    Toast.makeText(this@CheckoutActivity, "Server Error: ${response.code()}", Toast.LENGTH_SHORT).show()
                    btnSubmitOrder.isEnabled = true
                    btnSubmitOrder.text = "Place Order"
                }
            } catch (e: Exception) {
                Log.e("CHECKOUT_ERROR", "Exception: ${e.message}", e)
                Toast.makeText(this@CheckoutActivity, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSubmitOrder.isEnabled = true
                btnSubmitOrder.text = "Place Order"
            }
        }
    }
}