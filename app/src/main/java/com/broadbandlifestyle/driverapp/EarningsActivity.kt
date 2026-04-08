package com.broadbandlifestyle.driverapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowInsetsController
import android.os.Build
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.broadbandlifestyle.common.BalanceResponse
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class EarningsActivity : AppCompatActivity() {

    private lateinit var apiService: DriverApiService
    private var currentDriverId: Int = -1
    private var lastBalanceInfo: BalanceResponse? = null

    private lateinit var txtTotalEarnings: TextView
    private lateinit var rvEarnings: RecyclerView
    private lateinit var earningsAdapter: WeeklyEarningsAdapter
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var btnCashOut: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_earnings)

        setupWindowInsets()

        currentDriverId = intent.getIntExtra("DRIVER_ID", -1)
        if (currentDriverId == -1) {
            currentDriverId = getSharedPreferences("user_prefs", MODE_PRIVATE).getInt("USER_ID", -1)
        }

        if (currentDriverId == -1) {
            finish()
            return
        }

        initializeViews()
        setupRetrofit()
        setupRecyclerView()
        setupBottomNavigation()
        loadData()

        swipeRefresh.setOnRefreshListener { loadData() }

        btnCashOut.setOnClickListener {
            lastBalanceInfo?.let { balance ->
                if (balance.availableBalance > 0) {
                    val dialog = WithdrawalDialog(this, currentDriverId, balance, apiService) {
                        loadData() // Refresh data after successful withdrawal
                    }
                    dialog.show()
                } else {
                    Toast.makeText(this, "No funds available for withdrawal", Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(this, "Loading balance info...", Toast.LENGTH_SHORT).show()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToDashboard()
            }
        })
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }
    }

    private fun initializeViews() {
        txtTotalEarnings = findViewById(R.id.txtTotalEarnings)
        rvEarnings = findViewById(R.id.rvEarnings)
        bottomNav = findViewById(R.id.bottomNavigation)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        btnCashOut = findViewById(R.id.btnCashOut)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
    }

    private fun setupRecyclerView() {
        earningsAdapter = WeeklyEarningsAdapter { weeklyEarning ->
            weeklyEarning.isExpanded = !weeklyEarning.isExpanded
            earningsAdapter.notifyDataSetChanged()
        }
        rvEarnings.layoutManager = LinearLayoutManager(this)
        rvEarnings.adapter = earningsAdapter
    }

    private fun setupBottomNavigation() {
        bottomNav.selectedItemId = R.id.nav_earnings
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    navigateToDashboard()
                    true
                }
                R.id.nav_earnings -> true
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("DRIVER_ID", currentDriverId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DriverApiService::class.java)
    }

    private fun loadData() {
        swipeRefresh.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val earningsDeferred = async { apiService.getDriverEarnings(currentDriverId) }
                val balanceDeferred = async { apiService.getDriverBalance(currentDriverId) }

                val earningsResponse = earningsDeferred.await()
                val balanceResponse = balanceDeferred.await()

                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    if (earningsResponse.isSuccessful) {
                        earningsResponse.body()?.let { earnings ->
                            val expandableList = earnings.weeklyEarnings.map { ExpandableWeeklyEarning(it) }
                            earningsAdapter.submitList(expandableList)
                        }
                    }
                    if (balanceResponse.isSuccessful) {
                        balanceResponse.body()?.let { balance ->
                            lastBalanceInfo = balance
                            txtTotalEarnings.text = "R%.2f".format(balance.currentBalance)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    Log.e("EarningsDebug", "Error loading data: ${e.message}")
                }
            }
        }
    }
}
