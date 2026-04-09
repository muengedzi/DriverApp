package com.broadbandlifestyle.driverapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.broadbandlifestyle.common.*
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.common.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import android.os.VibrationEffect
import android.os.Vibrator

class MainActivity : AppCompatActivity() {

    private lateinit var apiService: DriverApiService
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var txtWelcomeName: TextView
    private lateinit var txtOnlineStatus: TextView
    private lateinit var txtTodayEarnings: TextView
    private lateinit var txtDriverRating: TextView
    private lateinit var txtOrderInfo: TextView
    private lateinit var layoutTransitControls: LinearLayout
    private lateinit var btnNavigate: Button
    private lateinit var btnDelivered: Button
    private lateinit var btnPickedUp: Button

    private var currentDriverId: Int = -1
    private var currentOrder: OrderResponse? = null
    private var currentOrderId: Int = -1
    private var lastNotifiedOrderId: Int = -1
    private var isShowingOfferDialog: Boolean = false
    private var countDownTimer: CountDownTimer? = null
    private var currentOfferDialog: AlertDialog? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshOrderData()
            mainHandler.postDelayed(this, 10000) // Poll every 10 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupWindowInsets()
        initDriverId()
        setupUI()
        setupRetrofit()
        createNotificationChannel()
        refreshAllData()

        // Start polling for orders
        mainHandler.post(pollRunnable)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }
    }

    private fun initDriverId() {
        currentDriverId = intent.getIntExtra("DRIVER_ID", -1)
        if (currentDriverId == -1) {
            currentDriverId = getSharedPreferences("user_prefs", MODE_PRIVATE).getInt("USER_ID", -1)
        }
        if (currentDriverId == -1) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setupUI() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        bottomNav = findViewById(R.id.bottomNavigation)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        txtWelcomeName = findViewById(R.id.txtWelcomeName)
        txtOnlineStatus = findViewById(R.id.txtOnlineStatus)
        txtTodayEarnings = findViewById(R.id.txtTodayEarnings)
        txtDriverRating = findViewById(R.id.txtDriverRating)
        txtOrderInfo = findViewById(R.id.orderStatusText)
        layoutTransitControls = findViewById(R.id.layoutTransitControls)
        btnNavigate = findViewById(R.id.btnNavigate)
        btnDelivered = findViewById(R.id.btnDelivered)
        btnPickedUp = findViewById(R.id.btnPickedUp)

        // Bottom Navigation Logic
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { /* Already here */ }
                R.id.nav_earnings -> startActivity(Intent(this, EarningsActivity::class.java))
                R.id.nav_history -> startActivity(Intent(this, HistoryActivity::class.java))
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
            }
            true
        }

        swipeRefresh.setOnRefreshListener { refreshAllData() }

        btnNavigate.setOnClickListener {
            val order = currentOrder ?: return@setOnClickListener
            val destinationLat = if (order.status.lowercase() == "accepted") order.resLat else order.lat
            val destinationLng = if (order.status.lowercase() == "accepted") order.resLng else order.lng

            if (destinationLat != null && destinationLat != 0.0) {
                val gmmIntentUri = Uri.parse("google.navigation:q=$destinationLat,$destinationLng")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            }
        }

        btnPickedUp.setOnClickListener {
            if (currentOrderId != -1) {
                markOrderPickedUp(currentOrderId)
            }
        }

        btnDelivered.setOnClickListener {
            if (currentOrderId != -1) {
                completeOrder(currentOrderId)
            }
        }
    }

    private fun setupRetrofit() {
        apiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DriverApiService::class.java)
    }

    private fun refreshAllData() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val response = apiService.getDriverProfile(currentDriverId)
                if (response.isSuccessful) {
                    val profile = response.body()
                    txtWelcomeName.text = "Hello, ${profile?.fullName ?: "Driver"}"
                    txtTodayEarnings.text = "R${String.format("%.2f", profile?.walletBalance ?: 0.0)}"
                    txtDriverRating.text = "${profile?.rating ?: 0.0} ⭐"
                    txtOnlineStatus.text = if (profile?.isAvailable == true) "ONLINE" else "OFFLINE"
                    txtOnlineStatus.setTextColor(if (profile?.isAvailable == true) Color.parseColor("#4CAF50") else Color.GRAY)
                }
            } catch (e: Exception) {
                Log.e("Main", "Error: ${e.message}")
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun refreshOrderData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.getMyCurrentAssignment(currentDriverId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val order = response.body()
                        if (order != null) {
                            currentOrderId = order.id
                            currentOrder = order

                            if (!order.isOffer) {
                                // Active delivery
                                displayActiveOrder(order)
                            } else {
                                // Offer - show dialog
                                if (order.id != lastNotifiedOrderId && !isShowingOfferDialog) {
                                    lastNotifiedOrderId = order.id
                                    showOrderOfferDialog(order)
                                }
                            }
                        } else {
                            // No orders
                            txtOrderInfo.text = "No active orders\n\nWaiting for offers..."
                            layoutTransitControls.visibility = View.GONE
                            currentOrderId = -1
                            currentOrder = null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Main", "Order fetch failed: ${e.message}")
            }
        }
    }

    private fun displayActiveOrder(order: OrderResponse) {
        layoutTransitControls.visibility = View.VISIBLE

        when (order.status.lowercase()) {
            "accepted" -> {
                btnPickedUp.visibility = View.VISIBLE
                btnDelivered.visibility = View.GONE
            }
            "picked_up", "on_the_way" -> {
                btnPickedUp.visibility = View.GONE
                btnDelivered.visibility = View.VISIBLE
            }
            else -> {
                btnPickedUp.visibility = View.GONE
                btnDelivered.visibility = View.GONE
            }
        }

        val sb = StringBuilder()
        sb.append("📦 ORDER #${order.orderNumber}\n")
        sb.append("────────────────────\n\n")
        sb.append("🚩 PICKUP FROM:\n")
        sb.append("${order.restaurantName}\n")
        sb.append("${order.restaurantAddress ?: "Address not available"}\n\n")
        sb.append("📍 DELIVER TO:\n")
        sb.append("${order.deliveryAddress ?: "Address not available"}\n\n")
        sb.append("Status: ${order.status.uppercase()}")
        txtOrderInfo.text = sb.toString()
    }

    private fun showOrderOfferDialog(order: OrderResponse) {
        if (isShowingOfferDialog) return

        isShowingOfferDialog = true
        sendOrderNotification(order.restaurantName ?: "Restaurant")
        triggerNewOrderVibration()

        val builder = AlertDialog.Builder(this)
        builder.setTitle("New Delivery Offer!")
        builder.setMessage("Restaurant: ${order.restaurantName}\n" +
                "Delivery to: ${order.deliveryAddress}\n" +
                "Time Remaining: ${order.remainingSeconds ?: 120}s")
        builder.setCancelable(false)

        builder.setPositiveButton("ACCEPT") { _, _ ->
            isShowingOfferDialog = false
            handleOrderResponse(order.id, "accepted")
        }

        builder.setNegativeButton("REJECT") { _, _ ->
            isShowingOfferDialog = false
            handleOrderResponse(order.id, "rejected")
        }

        currentOfferDialog = builder.create()
        currentOfferDialog?.setOnDismissListener {
            isShowingOfferDialog = false
        }
        currentOfferDialog?.show()

        startAcceptanceTimer(order.remainingSeconds ?: 120, currentOfferDialog, order.restaurantName ?: "Restaurant", order.id)
    }

    private fun startAcceptanceTimer(seconds: Int, dialog: AlertDialog?, restaurantName: String, orderId: Int) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secRemaining = millisUntilFinished / 1000
                dialog?.setMessage("Restaurant: $restaurantName\nAccept within ${secRemaining}s")
            }
            override fun onFinish() {
                dialog?.dismiss()
                isShowingOfferDialog = false
                refreshOrderData()
            }
        }.start()
    }

    private fun handleOrderResponse(orderId: Int, action: String) {
        currentOfferDialog?.dismiss()
        currentOfferDialog = null
        countDownTimer?.cancel()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = if (action == "accepted") {
                    apiService.acceptOrder(orderId, AcceptOrderRequest(currentDriverId))
                } else {
                    apiService.rejectOrder(orderId, AcceptOrderRequest(currentDriverId))
                }

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, if (action == "accepted") "Order Accepted!" else "Order Rejected", Toast.LENGTH_SHORT).show()
                        refreshOrderData()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to ${action} order", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun markOrderPickedUp(orderId: Int) {
        btnPickedUp.isEnabled = false
        btnPickedUp.text = "Updating..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.pickedUpOrder(orderId, AcceptOrderRequest(currentDriverId))
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "Order picked up! Head to customer.", Toast.LENGTH_LONG).show()
                        refreshOrderData()
                    } else {
                        btnPickedUp.isEnabled = true
                        btnPickedUp.text = "MARK PICKED UP"
                        Toast.makeText(this@MainActivity, "Failed to update status", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnPickedUp.isEnabled = true
                    btnPickedUp.text = "MARK PICKED UP"
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun completeOrder(orderId: Int) {
        btnDelivered.isEnabled = false
        btnDelivered.text = "Closing Order..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.deliverOrder(orderId, AcceptOrderRequest(currentDriverId))
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "Order completed successfully!", Toast.LENGTH_LONG).show()
                        currentOrderId = -1
                        currentOrder = null
                        txtOrderInfo.text = "No active orders\n\nPull down to refresh..."
                        layoutTransitControls.visibility = View.GONE
                        refreshOrderData()
                    } else {
                        btnDelivered.isEnabled = true
                        btnDelivered.text = "MARK DELIVERED"
                        Toast.makeText(this@MainActivity, "Failed to complete order", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnDelivered.isEnabled = true
                    btnDelivered.text = "MARK DELIVERED"
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Order Alerts"
            val desc = "Notifications for new delivery offers"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel("ORDER_CHANNEL", name, importance).apply {
                description = desc
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendOrderNotification(restaurantName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(this, "ORDER_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Order Available!")
            .setContentText("Offer from: $restaurantName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(1001, builder.build())
        }
    }

    private fun triggerNewOrderVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
        } else {
            vibrator.vibrate(1000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(pollRunnable)
        countDownTimer?.cancel()
        currentOfferDialog?.dismiss()
    }
}
