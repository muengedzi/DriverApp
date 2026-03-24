package com.broadbandlifestyle.driverapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.broadbandlifestyle.common.BalanceResponse
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.common.EarningsResponse
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class EarningsActivity : AppCompatActivity() {

    private lateinit var apiService: DriverApiService
    private var currentDriverId: Int = -1

    private lateinit var txtTotalEarnings: TextView
    private lateinit var rvEarnings: RecyclerView
    private lateinit var earningsAdapter: WeeklyEarningsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_earnings)

        currentDriverId = intent.getIntExtra("DRIVER_ID", -1)
        if (currentDriverId == -1) {
            finish()
            return
        }

        initializeViews()
        setupRetrofit()
        setupRecyclerView()
        loadData()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun initializeViews() {
        txtTotalEarnings = findViewById(R.id.txtTotalEarnings)
        rvEarnings = findViewById(R.id.rvEarnings)
    }

    private fun setupRecyclerView() {
        earningsAdapter = WeeklyEarningsAdapter { weeklyEarning ->
            weeklyEarning.isExpanded = !weeklyEarning.isExpanded
            earningsAdapter.notifyDataSetChanged()
        }
        rvEarnings.layoutManager = LinearLayoutManager(this)
        rvEarnings.adapter = earningsAdapter
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DriverApiService::class.java)
    }

    private fun loadData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val earningsDeferred = async { apiService.getDriverEarnings(currentDriverId) }
                val balanceDeferred = async { apiService.getDriverBalance(currentDriverId) }

                val earningsResponse = earningsDeferred.await()
                val balanceResponse = balanceDeferred.await()

                withContext(Dispatchers.Main) {
                    if (earningsResponse.isSuccessful) {
                        earningsResponse.body()?.let { earnings ->
                            val expandableList = earnings.weeklyEarnings.map { ExpandableWeeklyEarning(it) }
                            earningsAdapter.submitList(expandableList)
                        }
                    }
                    if (balanceResponse.isSuccessful) {
                        balanceResponse.body()?.let { balance ->
                            txtTotalEarnings.text = "R%.2f".format(balance.currentBalance)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("EarningsDebug", "Error loading data: ${e.message}")
                }
            }
        }
    }
}
