package com.broadbandlifestyle.customerapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
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
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
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

    // UI Elements for price breakdown
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

        initializeViews()
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
        tvSubtotal.text = "R${String.format("%.2f", subtotal)}"
        tvDeliveryFee.text = "R${String.format("%.2f", deliveryFee)}"
        tvPlatformFee.text = "R${String.format("%.2f", platformFee)}"
        tvGrandTotal.text = "R${String.format("%.2f", total)}"

        Log.d("CHECKOUT", "Displaying fees - Subtotal: $subtotal, Delivery: $deliveryFee, Platform: $platformFee, Total: $total")
    }

    private fun setupUI() {
        val tvTotalItems = findViewById<TextView>(R.id.tvTotalItems)
        tvTotalItems.text = "Total Items: ${CartManager.getCartCount()}"
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
                fetchLocation(btnUseGPS, etAddress)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1001
                )
            }
        }
    }

    private fun fetchLocation(button: MaterialButton, addressField: TextInputEditText) {
        button.isEnabled = false
        button.text = "Fetching Location..."

        try {
            val priority = Priority.PRIORITY_HIGH_ACCURACY
            val cts = CancellationTokenSource()
            
            fusedLocationClient.getCurrentLocation(priority, cts.token).addOnSuccessListener { location ->
                button.isEnabled = true
                button.text = "📍 Use My Current Location"
                
                if (location != null) {
                    currentLat = location.latitude
                    currentLng = location.longitude
                    
                    lifecycleScope.launch {
                        val address = getAddressFromCoords(location.latitude, location.longitude)
                        addressField.setText(address)
                    }
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            currentLat = lastLoc.latitude
                            currentLng = lastLoc.longitude
                            lifecycleScope.launch {
                                val address = getAddressFromCoords(lastLoc.latitude, lastLoc.longitude)
                                addressField.setText(address)
                            }
                        } else {
                            Toast.makeText(this, "Unable to get location. Is GPS on?", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.addOnFailureListener {
                button.isEnabled = true
                button.text = "📍 Use My Current Location"
                Toast.makeText(this, "Location error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            button.isEnabled = true
            button.text = "📍 Use My Current Location"
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
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
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            findViewById<MaterialButton>(R.id.btnUseGPS).performClick()
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

    private suspend fun getAddressFromCoords(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(this@CheckoutActivity, Locale.getDefault())
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = CompletableDeferred<String>()
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        result.complete(addresses[0].getAddressLine(0))
                    } else {
                        result.complete("Lat: $lat, Lng: $lng")
                    }
                }
                result.await()
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0)
                } else {
                    "Lat: $lat, Lng: $lng"
                }
            }
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
