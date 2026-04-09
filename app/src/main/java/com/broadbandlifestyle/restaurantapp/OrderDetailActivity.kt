package com.broadbandlifestyle.restaurantapp

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.broadbandlifestyle.common.*
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.driverapp.R
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class OrderDetailActivity : AppCompatActivity() {

    private lateinit var apiService: RestaurantApiService
    private var orderId: Int = -1
    private var restaurantId: Int = -1
    private var currentStatus: String = ""

    // UI Elements
    private lateinit var txtOrderNumber: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtCustomerName: TextView
    private lateinit var txtCustomerPhone: TextView
    private lateinit var txtDeliveryAddress: TextView
    private lateinit var txtInstructions: TextView
    private lateinit var txtOrderTotal: TextView
    private lateinit var txtDeliveryFee: TextView
    private lateinit var txtPlatformFee: TextView
    private lateinit var txtFinalTotal: TextView
    private lateinit var recyclerItems: RecyclerView
    private lateinit var btnUpdateStatus: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var itemsAdapter: OrderItemsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_detail)

        orderId = intent.getIntExtra("ORDER_ID", -1)
        restaurantId = intent.getIntExtra("RESTAURANT_ID", -1)

        if (orderId == -1 || restaurantId == -1) {
            Toast.makeText(this, "Invalid order data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupRetrofit()
        setupRecyclerView()
        loadOrderDetails()
    }

    private fun initializeViews() {
        txtOrderNumber = findViewById(R.id.txtOrderNumber)
        txtStatus = findViewById(R.id.txtStatus)
        txtCustomerName = findViewById(R.id.txtCustomerName)
        txtCustomerPhone = findViewById(R.id.txtCustomerPhone)
        txtDeliveryAddress = findViewById(R.id.txtDeliveryAddress)
        txtInstructions = findViewById(R.id.txtInstructions)
        txtOrderTotal = findViewById(R.id.txtOrderTotal)
        txtDeliveryFee = findViewById(R.id.txtDeliveryFee)
        txtPlatformFee = findViewById(R.id.txtPlatformFee)
        txtFinalTotal = findViewById(R.id.txtFinalTotal)
        recyclerItems = findViewById(R.id.recyclerItems)
        btnUpdateStatus = findViewById(R.id.btnUpdateStatus)
        progressBar = findViewById(R.id.progressBar)

        btnUpdateStatus.setOnClickListener {
            showStatusUpdateDialog()
        }
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(RestaurantApiService::class.java)
    }

    private fun setupRecyclerView() {
        itemsAdapter = OrderItemsAdapter()
        recyclerItems.layoutManager = LinearLayoutManager(this)
        recyclerItems.adapter = itemsAdapter
    }

    private fun loadOrderDetails() {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getOrderDetails(orderId, restaurantId)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val data = response.body()
                        data?.let { updateUI(it.order, it.items) }
                    } else {
                        Toast.makeText(this@OrderDetailActivity, "Failed to load order details", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@OrderDetailActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun updateUI(order: OrderDetail, items: List<OrderItem>) {
        currentStatus = order.status
        txtOrderNumber.text = order.order_number
        txtStatus.text = order.status.uppercase()
        txtCustomerName.text = order.customer_name ?: "N/A"
        txtCustomerPhone.text = order.customer_phone ?: "N/A"
        txtDeliveryAddress.text = order.delivery_address ?: "N/A"
        txtInstructions.text = order.instructions?.ifEmpty { "No special instructions" } ?: "No special instructions"
        txtOrderTotal.text = "R${String.format("%.2f", order.order_total)}"
        txtDeliveryFee.text = "R${String.format("%.2f", order.delivery_fee)}"
        txtPlatformFee.text = "R${String.format("%.2f", order.platform_fee)}"
        txtFinalTotal.text = "R${String.format("%.2f", order.final_amount)}"

        // Set status color
        when (order.status.lowercase()) {
            "pending" -> txtStatus.setBackgroundResource(R.drawable.bg_status_pending)
            "preparing" -> txtStatus.setBackgroundResource(R.drawable.bg_status_preparing)
            "ready" -> txtStatus.setBackgroundResource(R.drawable.bg_status_ready)
        }

        itemsAdapter.submitList(items)

        // Update button visibility based on status
        updateButtonVisibility(order.status)
    }

    private fun updateButtonVisibility(status: String) {
        btnUpdateStatus.isEnabled = when (status.lowercase()) {
            "pending", "preparing" -> true
            "ready" -> false
            else -> false
        }

        btnUpdateStatus.text = when (status.lowercase()) {
            "pending" -> "Start Preparing"
            "preparing" -> "Mark as Ready"
            else -> "Order Complete"
        }
    }

    private fun showStatusUpdateDialog() {
        val nextStatus = when (currentStatus.lowercase()) {
            "pending" -> "preparing"
            "preparing" -> "ready"
            else -> return
        }

        val message = when (currentStatus.lowercase()) {
            "pending" -> "Mark this order as PREPARING?"
            "preparing" -> "Mark this order as READY FOR PICKUP?"
            else -> ""
        }

        AlertDialog.Builder(this)
            .setTitle("Update Order Status")
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ ->
                updateOrderStatus(nextStatus)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun updateOrderStatus(newStatus: String) {
        progressBar.visibility = View.VISIBLE
        btnUpdateStatus.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = UpdateOrderStatusRequest(restaurantId, newStatus)
                val response = apiService.updateOrderStatus(orderId, request)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(this@OrderDetailActivity,
                            "Order status updated to ${newStatus.uppercase()}",
                            Toast.LENGTH_SHORT).show()
                        loadOrderDetails() // Refresh
                    } else {
                        btnUpdateStatus.isEnabled = true
                        Toast.makeText(this@OrderDetailActivity,
                            response.body()?.message ?: "Failed to update status",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnUpdateStatus.isEnabled = true
                    Toast.makeText(this@OrderDetailActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}