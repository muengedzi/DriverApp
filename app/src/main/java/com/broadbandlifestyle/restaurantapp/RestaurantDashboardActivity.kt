package com.broadbandlifestyle.restaurantapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.broadbandlifestyle.common.CapsuleNavigationHelper
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.common.LoginActivity
import com.broadbandlifestyle.common.RestaurantStats
import com.broadbandlifestyle.driverapp.R
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RestaurantDashboardActivity : AppCompatActivity() {

    private lateinit var apiService: RestaurantApiService
    private var restaurantId: Int = -1
    private var restaurantName: String = ""
    private var userId: Int = -1

    // UI Elements
    private lateinit var toolbar: Toolbar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var mainContent: LinearLayout

    // Dashboard views
    private var txtRestaurantName: TextView? = null
    private var txtTodayOrders: TextView? = null
    private var txtTodayRevenue: TextView? = null
    private var txtPendingOrders: TextView? = null
    private var txtPreparingOrders: TextView? = null
    private var txtReadyOrders: TextView? = null

    // Orders views
    private var recyclerActiveOrders: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private var txtEmptyState: TextView? = null
    private lateinit var ordersAdapter: RestaurantOrdersAdapter

    private var currentTab = "dashboard"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restaurant_dashboard)

        // 1. Get data from intent first
        restaurantId = intent.getIntExtra("RESTAURANT_ID", -1)
        restaurantName = intent.getStringExtra("RESTAURANT_NAME") ?: ""
        userId = intent.getIntExtra("USER_ID", -1)

        // 2. Fallback to prefs if needed
        if (restaurantId == -1) {
            val prefs = getSharedPreferences("restaurant_prefs", MODE_PRIVATE)
            restaurantId = prefs.getInt("RESTAURANT_ID", -1)
            if (restaurantName.isEmpty()) {
                restaurantName = prefs.getString("RESTAURANT_NAME", "Restaurant") ?: "Restaurant"
            }
        }

        if (restaurantId == -1) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupCapsuleNavigation()
        setupRetrofit()

        // Initialize adapter once
        ordersAdapter = RestaurantOrdersAdapter { order ->
            val intent = Intent(this, OrderDetailActivity::class.java)
            intent.putExtra("ORDER_ID", order.id)
            intent.putExtra("RESTAURANT_ID", restaurantId)
            startActivity(intent)
        }

        // Load dashboard by default
        showDashboard()
        loadStats()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        mainContent = findViewById(R.id.mainContent)

        swipeRefresh.setOnRefreshListener {
            when (currentTab) {
                "dashboard" -> loadStats()
                "orders" -> loadOrders()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.title = restaurantName
    }

    private fun setupCapsuleNavigation() {
        CapsuleNavigationHelper.setupCapsuleNavigation(
            activity = this,
            menuResId = R.menu.bottom_nav_menu_restaurant,
            onItemSelected = { itemId ->
                when (itemId) {
                    R.id.nav_dashboard -> {
                        currentTab = "dashboard"
                        showDashboard()
                        loadStats()
                        true
                    }
                    R.id.nav_active_orders -> {
                        currentTab = "orders"
                        showActiveOrders()
                        loadOrders()
                        true
                    }
                    R.id.nav_history -> {
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

    private fun showDashboard() {
        mainContent.removeAllViews()
        val dashboardView = layoutInflater.inflate(R.layout.content_dashboard, mainContent, false)
        mainContent.addView(dashboardView)

        txtRestaurantName = dashboardView.findViewById(R.id.txtRestaurantName)
        txtTodayOrders = dashboardView.findViewById(R.id.txtTodayOrders)
        txtTodayRevenue = dashboardView.findViewById(R.id.txtTodayRevenue)
        txtPendingOrders = dashboardView.findViewById(R.id.txtPendingOrders)
        txtPreparingOrders = dashboardView.findViewById(R.id.txtPreparingOrders)
        txtReadyOrders = dashboardView.findViewById(R.id.txtReadyOrders)

        txtRestaurantName?.text = restaurantName
    }

    private fun showActiveOrders() {
        mainContent.removeAllViews()
        val ordersView = layoutInflater.inflate(R.layout.content_active_orders, mainContent, false)
        mainContent.addView(ordersView)

        recyclerActiveOrders = ordersView.findViewById(R.id.recyclerActiveOrders)
        progressBar = ordersView.findViewById(R.id.progressBar)
        txtEmptyState = ordersView.findViewById(R.id.txtEmptyState)

        recyclerActiveOrders?.layoutManager = LinearLayoutManager(this)
        recyclerActiveOrders?.adapter = ordersAdapter
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(RestaurantApiService::class.java)
    }

    private fun loadStats() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getStats(restaurantId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val stats = response.body()
                        stats?.let { updateStatsUI(it) }
                    }
                    swipeRefresh.isRefreshing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun updateStatsUI(stats: RestaurantStats) {
        txtTodayOrders?.text = stats.today_orders.toString()
        txtTodayRevenue?.text = "R${String.format("%.2f", stats.today_revenue)}"
        txtPendingOrders?.text = stats.pending_orders.toString()
        txtPreparingOrders?.text = stats.preparing_orders.toString()
        txtReadyOrders?.text = stats.ready_orders.toString()
    }

    private fun loadOrders() {
        progressBar?.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getOrders(restaurantId)
                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    swipeRefresh.isRefreshing = false

                    if (response.isSuccessful) {
                        val data = response.body()
                        if (data != null && data.active_orders.isNotEmpty()) {
                            txtEmptyState?.visibility = View.GONE
                            recyclerActiveOrders?.visibility = View.VISIBLE
                            ordersAdapter.submitList(data.active_orders)
                        } else {
                            txtEmptyState?.visibility = View.VISIBLE
                            recyclerActiveOrders?.visibility = View.GONE
                            txtEmptyState?.text = "No active orders"
                        }
                    } else {
                        showError("Failed to load orders")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    showError("Network error: ${e.message}")
                }
            }
        }
    }

    private fun showError(message: String) {
        txtEmptyState?.visibility = View.VISIBLE
        txtEmptyState?.text = message
        recyclerActiveOrders?.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                getSharedPreferences("restaurant_prefs", MODE_PRIVATE).edit().clear().apply()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
