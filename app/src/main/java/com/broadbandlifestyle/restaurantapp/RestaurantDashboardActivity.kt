package com.broadbandlifestyle.restaurantapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.broadbandlifestyle.common.*
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.driverapp.R
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RestaurantDashboardActivity : AppCompatActivity() {

    private lateinit var apiService: RestaurantApiService
    private var restaurantId: Int = -1
    private var restaurantName: String = ""
    private var userId: Int = -1

    // UI Elements
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var txtRestaurantName: TextView
    private lateinit var txtTodayOrders: TextView
    private lateinit var txtTodayRevenue: TextView
    private lateinit var txtPendingOrders: TextView
    private lateinit var txtPreparingOrders: TextView
    private lateinit var txtReadyOrders: TextView
    private lateinit var recyclerActiveOrders: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var txtEmptyState: TextView

    private lateinit var ordersAdapter: RestaurantOrdersAdapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restaurant_dashboard)

        // Get data from intent
        restaurantId = intent.getIntExtra("RESTAURANT_ID", -1)
        restaurantName = intent.getStringExtra("RESTAURANT_NAME") ?: "Restaurant"
        userId = intent.getIntExtra("USER_ID", -1)

        // If not from intent, try shared preferences
        if (restaurantId == -1) {
            val prefs = getSharedPreferences("restaurant_prefs", MODE_PRIVATE)
            // Fix the key name here:
            restaurantId = prefs.getInt("RESTAURANT_ID", -1)
            restaurantName = prefs.getString("RESTAURANT_NAME", "Restaurant") ?: "Restaurant"
        }

        if (restaurantId == -1) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        initializeViews()
        setupToolbarAndNavigation()
        setupRetrofit()
        setupRecyclerView()
        loadStats()
        loadOrders()

        // Start polling for new orders every 10 seconds
        startPolling()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        txtRestaurantName = findViewById(R.id.txtRestaurantName)
        txtTodayOrders = findViewById(R.id.txtTodayOrders)
        txtTodayRevenue = findViewById(R.id.txtTodayRevenue)
        txtPendingOrders = findViewById(R.id.txtPendingOrders)
        txtPreparingOrders = findViewById(R.id.txtPreparingOrders)
        txtReadyOrders = findViewById(R.id.txtReadyOrders)
        recyclerActiveOrders = findViewById(R.id.recyclerActiveOrders)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        txtEmptyState = findViewById(R.id.txtEmptyState)

        txtRestaurantName.text = restaurantName
    }

    private fun setupToolbarAndNavigation() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.open, R.string.close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        toggle.drawerArrowDrawable.color = Color.WHITE

        setupNavigationMenu()
    }

    private fun setupNavigationMenu() {
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    showLogoutConfirmation()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Update navigation header with restaurant name
        val headerView = navView.getHeaderView(0)
        val navHeaderName = headerView.findViewById<TextView>(R.id.navHeaderName)
        val navHeaderStatus = headerView.findViewById<TextView>(R.id.navHeaderStatus)
        navHeaderName.text = restaurantName
        navHeaderStatus.text = "Restaurant Staff"
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(RestaurantApiService::class.java)
    }

    private fun setupRecyclerView() {
        ordersAdapter = RestaurantOrdersAdapter { order ->
            val intent = Intent(this, OrderDetailActivity::class.java)
            intent.putExtra("ORDER_ID", order.id)
            intent.putExtra("RESTAURANT_ID", restaurantId)
            startActivity(intent)
        }
        recyclerActiveOrders.layoutManager = LinearLayoutManager(this)
        recyclerActiveOrders.adapter = ordersAdapter
    }

    private fun startPolling() {
        pollRunnable = object : Runnable {
            override fun run() {
                loadOrders()
                mainHandler.postDelayed(this, 10000)
            }
        }
        mainHandler.post(pollRunnable!!)
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
                }
            } catch (e: Exception) {
                // Silent fail - stats will update on next refresh
            }
        }
    }

    private fun updateStatsUI(stats: RestaurantStats) {
        txtTodayOrders.text = stats.today_orders.toString()
        txtTodayRevenue.text = "R${String.format("%.2f", stats.today_revenue)}"
        txtPendingOrders.text = stats.pending_orders.toString()
        txtPreparingOrders.text = stats.preparing_orders.toString()
        txtReadyOrders.text = stats.ready_orders.toString()
    }

    private fun loadOrders() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getOrders(restaurantId)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false

                    if (response.isSuccessful) {
                        val data = response.body()
                        if (data != null && data.active_orders.isNotEmpty()) {
                            txtEmptyState.visibility = View.GONE
                            recyclerActiveOrders.visibility = View.VISIBLE
                            ordersAdapter.submitList(data.active_orders)
                        } else {
                            txtEmptyState.visibility = View.VISIBLE
                            recyclerActiveOrders.visibility = View.GONE
                            txtEmptyState.text = "No active orders"
                        }
                    } else {
                        showError("Failed to load orders")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    showError("Network error: ${e.message}")
                }
            }
        }
    }

    private fun showError(message: String) {
        txtEmptyState.visibility = View.VISIBLE
        txtEmptyState.text = message
        recyclerActiveOrders.visibility = View.GONE
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

    override fun onDestroy() {
        super.onDestroy()
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
    }
}