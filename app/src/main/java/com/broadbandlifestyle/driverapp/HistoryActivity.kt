package com.broadbandlifestyle.driverapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.broadbandlifestyle.common.Constants.BASE_URL
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HistoryActivity : AppCompatActivity() {

    private lateinit var apiService: DriverApiService
    private var currentDriverId: Int = -1
    private lateinit var toolbar: Toolbar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvHistory: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        currentDriverId = intent.getIntExtra("DRIVER_ID", -1)
        if (currentDriverId == -1) {
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupRetrofit()
        loadHistory()

        swipeRefresh.setOnRefreshListener {
            loadHistory()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        rvHistory = findViewById(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DriverApiService::class.java)
    }

    private fun loadHistory() {
        swipeRefresh.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getCompletedOrders(currentDriverId)
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    if (response.isSuccessful) {
                        val orders = response.body() ?: emptyList()
                        if (orders.isNotEmpty()) {
                            rvHistory.adapter = HistoryAdapter(orders)
                        } else {
                            Toast.makeText(this@HistoryActivity, "No history found", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@HistoryActivity, "Failed to load history", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    Log.e("HistoryDebug", "Error: ${e.message}")
                    Toast.makeText(this@HistoryActivity, "Connection error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
