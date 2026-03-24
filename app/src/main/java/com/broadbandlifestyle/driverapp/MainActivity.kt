package com.broadbandlifestyle.driverapp

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.broadbandlifestyle.common.*
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.common.Constants.ORDER_CHANNEL_ID
import com.broadbandlifestyle.common.Constants.ORDER_CHANNEL_NAME
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URL

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private var currentOrder: OrderResponse? = null
    private lateinit var apiService: DriverApiService
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var txtOrderInfo: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var txtWelcomeName: TextView
    private lateinit var txtTodayEarnings: TextView
    private lateinit var txtTodayDeliveries: TextView
    private lateinit var txtDriverRating: TextView
    private lateinit var txtStatusBadge: TextView
    private lateinit var layoutTransitControls: LinearLayout
    private lateinit var btnNavigate: Button
    private lateinit var btnDelivered: Button
    private lateinit var btnPickedUp: Button
    private lateinit var mapCard: View
    private lateinit var txtOnlineStatus: TextView

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentPolyline: Polyline? = null

    private var currentDriverId: Int = -1
    private var currentOrderId: Int = -1
    private var lastNotifiedOrderId: Int = -1
    private var isShowingOfferDialog: Boolean = false
    private var countDownTimer: CountDownTimer? = null
    private var currentOfferDialog: AlertDialog? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isOnline: Boolean = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshOrderData()
            checkDriverStatus()
            mainHandler.postDelayed(this, 10000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawerLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        currentDriverId = intent.getIntExtra("DRIVER_ID", -1)
        if (currentDriverId == -1) {
            currentDriverId = getSharedPreferences("user_prefs", MODE_PRIVATE).getInt("USER_ID", -1)
        }

        if (currentDriverId == -1) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupUI()
        setupRetrofit()
        createNotificationChannel()
        checkNotificationPermission()
        checkLocationPermission()

        // START THE FOREGROUND SERVICE IMMEDIATELY
        startForegroundService()

        refreshAllData()
        mainHandler.post(pollRunnable)
        startStatusCheck()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
        }
        updateMapMarkers()
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DriverApiService::class.java)
    }

    private fun setupUI() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        txtWelcomeName = findViewById(R.id.txtWelcomeName)
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        txtOrderInfo = findViewById(R.id.orderStatusText)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        txtStatusBadge = findViewById(R.id.txtStatusBadge)
        txtTodayEarnings = findViewById(R.id.txtTodayEarnings)
        txtTodayDeliveries = findViewById(R.id.txtTodayDeliveries)
        txtDriverRating = findViewById(R.id.txtDriverRating)
        layoutTransitControls = findViewById(R.id.layoutTransitControls)
        btnNavigate = findViewById(R.id.btnNavigate)
        btnDelivered = findViewById(R.id.btnDelivered)
        btnPickedUp = findViewById(R.id.btnPickedUp)
        mapCard = findViewById(R.id.mapCard)
        txtOnlineStatus = findViewById(R.id.txtOnlineStatus)

        layoutTransitControls.visibility = View.GONE
        mapCard.visibility = View.GONE

        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        toggle.drawerArrowDrawable.color = Color.WHITE

        setupNavigationMenu()
        swipeRefresh.setOnRefreshListener { refreshAllData() }

        btnNavigate.setOnClickListener {
            startExternalNavigation()
        }

        btnPickedUp.setOnClickListener {
            if (currentOrderId != -1) markOrderPickedUp(currentOrderId)
        }

        btnDelivered.setOnClickListener {
            if (currentOrderId != -1) completeOrder(currentOrderId)
        }
    }

    private fun startForegroundService() {
        Log.d("MainActivity", "Starting OrderForegroundService for driver: $currentDriverId")
        val intent = Intent(this, OrderForegroundService::class.java)
        intent.putExtra("DRIVER_ID", currentDriverId)

        // Save online status to preferences
        getSharedPreferences("driver_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("IS_ONLINE", true)
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val services = manager.getRunningServices(Int.MAX_VALUE)
            for (service in services) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking service: ${e.message}")
        }
        return false
    }

    private fun setupNavigationMenu() {
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Already on home
                }
                R.id.nav_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.putExtra("DRIVER_ID", currentDriverId)
                    startActivity(intent)
                }
                R.id.nav_earnings -> {
                    val intent = Intent(this, EarningsActivity::class.java)
                    intent.putExtra("DRIVER_ID", currentDriverId)
                    startActivity(intent)
                }
                R.id.nav_history -> {
                    val intent = Intent(this, HistoryActivity::class.java)
                    intent.putExtra("DRIVER_ID", currentDriverId)
                    startActivity(intent)
                }
                R.id.nav_help -> showHelpDialog()
                R.id.nav_logout -> showLogoutConfirmation()
            }
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }
    }

    private fun checkDriverStatus() {
        Log.d("MainActivity", "checkDriverStatus called")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getDriverStatus(currentDriverId)
                Log.d("MainActivity", "Status API Response: ${response.code()}")

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val status = response.body()!!
                        Log.d("MainActivity", "Driver Status: isOnline=${status.is_online}, isAvailable=${status.is_available}, minutesSinceHeartbeat=${status.minutes_since_heartbeat}")

                        isOnline = status.is_online

                        // CRITICAL: Check if there's a pending offer even if driver is offline
                        if (status.has_pending_offer && !isShowingOfferDialog) {
                            Log.d("MainActivity", "Driver has pending offer according to status API")
                            // Force refresh order data to show offer
                            refreshOrderData()
                        }

                        if (isOnline) {
                            txtOnlineStatus.text = "● ONLINE"
                            txtOnlineStatus.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                        } else {
                            txtOnlineStatus.text = "○ OFFLINE"
                            txtOnlineStatus.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                        }

                        // Show warning if about to go offline
                        if (status.minutes_since_heartbeat > 25 && status.is_online) {
                            Toast.makeText(this@MainActivity,
                                "No activity detected. You'll be marked offline in ${30 - status.minutes_since_heartbeat} minutes.",
                                Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.e("MainActivity", "Status API failed: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainDebug", "Status check error: ${e.message}", e)
            }
        }
    }

    private fun startStatusCheck() {
        val statusCheckRunnable = object : Runnable {
            override fun run() {
                checkDriverStatus()
                mainHandler.postDelayed(this, 60000) // Check every minute
            }
        }
        mainHandler.post(statusCheckRunnable)
    }

    private fun startExternalNavigation() {
        val order = currentOrder ?: return
        val destLat = if (order.status.lowercase() == "accepted") order.resLat else order.lat
        val destLng = if (order.status.lowercase() == "accepted") order.resLng else order.lng

        if (destLat != null && destLng != null && destLat != 0.0) {
            val gmmIntentUri = Uri.parse("google.navigation:q=$destLat,$destLng")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                Toast.makeText(this, "Google Maps app not found", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Destination coordinates not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 102)
        }
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("Help & Support")
            .setMessage("For assistance, contact:\n\n📞 +27 725 138 539\n📧 support@broadbandlifestyle.co.za")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ -> performLogout() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun performLogout() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                apiService.driverLogout(mapOf("driver_id" to currentDriverId))
            } catch (e: Exception) {}
            withContext(Dispatchers.Main) {
                getSharedPreferences("user_prefs", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("driver_prefs", MODE_PRIVATE).edit().clear().apply()
                stopTimer()
                mainHandler.removeCallbacks(pollRunnable)
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun refreshAllData() {
        swipeRefresh.isRefreshing = true
        fetchDriverProfile()
        refreshOrderData()
    }

    private fun fetchDriverProfile() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getDriverProfile(currentDriverId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val profile = response.body()!!
                        txtWelcomeName.text = "Welcome, ${profile.fullName}"
                        txtTodayEarnings.text = "R${"%.2f".format(profile.walletBalance)}"
                        txtTodayDeliveries.text = profile.completedDeliveries.toString()
                        txtDriverRating.text = "${profile.rating} ⭐"
                        txtStatusBadge.text = if (profile.isAvailable) "AVAILABLE" else "OFFLINE"
                        updateNavigationHeader(profile.fullName, if (profile.isAvailable) "Online" else "Offline")
                    }
                    swipeRefresh.isRefreshing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    Log.e("MainDebug", "Profile fetch error: ${e.message}")
                }
            }
        }
    }

    private fun updateNavigationHeader(name: String, status: String) {
        try {
            val headerView = navView.getHeaderView(0)
            headerView.findViewById<TextView>(R.id.navHeaderName).text = name
            headerView.findViewById<TextView>(R.id.navHeaderStatus).text = status
        } catch (e: Exception) {}
    }

    private fun refreshOrderData() {
        Log.d("MainActivity", "refreshOrderData called - Driver ID: $currentDriverId, isOnline: $isOnline")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getMyCurrentAssignment(currentDriverId)
                Log.d("MainActivity", "API Response Code: ${response.code()}")

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val order = response.body()
                        Log.d("MainActivity", "Order received: ${order?.id}, isOffer: ${order?.isOffer}")

                        if (order != null) {
                            currentOrderId = order.id
                            currentOrder = order
                            if (!order.isOffer) {
                                Log.d("MainActivity", "Displaying active order")
                                displayActiveOrder(order)
                            } else if (order.id != lastNotifiedOrderId && !isShowingOfferDialog) {
                                Log.d("MainActivity", "Showing order offer dialog for order: ${order.id}")
                                lastNotifiedOrderId = order.id
                                showOrderOfferDialog(order)
                            } else {
                                Log.d("MainActivity", "Not showing offer - id: ${order.id}, lastNotified: $lastNotifiedOrderId, isShowing: $isShowingOfferDialog")
                            }
                        } else {
                            Log.d("MainActivity", "No orders available")
                            resetToIdleState()
                        }
                    } else {
                        Log.e("MainActivity", "API Error: ${response.code()} - ${response.message()}")
                        resetToIdleState()
                    }
                }
            } catch (e: Exception) {
                Log.e("MAIN_DEBUG", "Error: ${e.message}", e)
                withContext(Dispatchers.Main) { resetToIdleState() }
            }
        }
    }

    private fun resetToIdleState() {
        currentOrderId = -1
        currentOrder = null
        txtOrderInfo.text = "No active orders\n\nPull down to refresh..."
        layoutTransitControls.visibility = View.GONE
        mapCard.visibility = View.GONE
        googleMap?.clear()
        currentPolyline?.remove()
    }

    private fun displayActiveOrder(order: OrderResponse) {
        layoutTransitControls.visibility = View.VISIBLE
        mapCard.visibility = View.VISIBLE

        when (order.status.lowercase()) {
            "accepted" -> {
                btnPickedUp.visibility = View.VISIBLE
                btnDelivered.visibility = View.GONE
                btnNavigate.text = "NAVIGATE TO RESTAURANT"
                txtOrderInfo.text = "📦 ORDER #${order.orderNumber}\n🚩 RESTAURANT: ${order.restaurantName}\nStatus: ACCEPTED\n\nNavigate to restaurant to pick up order"
            }
            "picked_up", "on_the_way" -> {
                btnPickedUp.visibility = View.GONE
                btnDelivered.visibility = View.VISIBLE
                btnNavigate.text = "NAVIGATE TO CUSTOMER"
                txtOrderInfo.text = "📦 ORDER #${order.orderNumber}\n📍 CUSTOMER: ${order.deliveryAddress}\nStatus: PICKED UP\n\nNavigate to customer to complete delivery"
            }
            else -> resetToIdleState()
        }
        updateMapMarkers()
        drawRoute()
    }

    private fun updateMapMarkers() {
        val map = googleMap ?: return
        val order = currentOrder ?: return
        map.clear()

        val destLat = if (order.status.lowercase() == "accepted") order.resLat else order.lat
        val destLng = if (order.status.lowercase() == "accepted") order.resLng else order.lng
        val title = if (order.status.lowercase() == "accepted") "Restaurant" else "Customer"

        if (destLat != null && destLng != null && destLat != 0.0) {
            val destLatLng = LatLng(destLat, destLng)
            map.addMarker(MarkerOptions().position(destLatLng).title(title))
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(destLatLng, 15f))
        }
    }

    private fun drawRoute() {
        val order = currentOrder ?: return
        val destLat = if (order.status.lowercase() == "accepted") order.resLat else order.lat
        val destLng = if (order.status.lowercase() == "accepted") order.resLng else order.lng

        if (destLat == null || destLng == null || destLat == 0.0) return

        val destination = LatLng(destLat, destLng)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val origin = LatLng(location.latitude, location.longitude)
                fetchAndDrawRoute(origin, destination)
            }
        }
    }

    private fun fetchAndDrawRoute(origin: LatLng, dest: LatLng) {
        val apiKey = "AIzaSyCDCIbudU_dAlsz6QS-ssb6fENwd-_TXo4"
        val url = "https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}&destination=${dest.latitude},${dest.longitude}&key=$apiKey"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = URL(url).readText()
                val json = JSONObject(response)
                val routes = json.getJSONArray("routes")
                if (routes.length() > 0) {
                    val points = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                    val path = decodePolyline(points)

                    withContext(Dispatchers.Main) {
                        currentPolyline?.remove()
                        currentPolyline = googleMap?.addPolyline(PolylineOptions()
                            .addAll(path)
                            .color(Color.BLUE)
                            .width(12f))
                    }
                }
            } catch (e: Exception) {
                Log.e("RouteError", "Error fetching directions: ${e.message}")
            }
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }

    private fun showOrderOfferDialog(order: OrderResponse) {
        if (isShowingOfferDialog) return
        isShowingOfferDialog = true
        sendOrderNotification(order.restaurantName ?: "Restaurant")

        val builder = AlertDialog.Builder(this)
            .setTitle("New Delivery Offer!")
            .setMessage("Restaurant: ${order.restaurantName}\nAccept within ${order.remainingSeconds ?: 120}s")
            .setCancelable(false)
            .setPositiveButton("ACCEPT") { _, _ -> handleOrderResponse(order.id, "accepted") }
            .setNegativeButton("REJECT") { _, _ -> handleOrderResponse(order.id, "rejected") }

        currentOfferDialog = builder.create()
        currentOfferDialog?.show()
        startAcceptanceTimer(order.remainingSeconds ?: 120, currentOfferDialog, order.restaurantName ?: "Restaurant")
    }

    private fun startAcceptanceTimer(seconds: Int, dialog: AlertDialog?, restaurantName: String) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                dialog?.setMessage("Restaurant: $restaurantName\nAccept within ${millisUntilFinished / 1000}s")
            }
            override fun onFinish() {
                dialog?.dismiss()
                isShowingOfferDialog = false
                refreshOrderData()
            }
        }.start()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun handleOrderResponse(orderId: Int, action: String) {
        currentOfferDialog?.dismiss()
        isShowingOfferDialog = false
        stopTimer()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = if (action == "accepted") {
                    apiService.acceptOrder(orderId, AcceptOrderRequest(currentDriverId))
                } else {
                    apiService.rejectOrder(orderId, AcceptOrderRequest(currentDriverId))
                }
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        if (action == "accepted") {
                            Toast.makeText(this@MainActivity, "Order accepted! Navigate to restaurant.", Toast.LENGTH_LONG).show()
                        }
                        refreshOrderData()
                    } else {
                        Toast.makeText(this@MainActivity, "Action failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun markOrderPickedUp(orderId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.pickedUpOrder(orderId, AcceptOrderRequest(currentDriverId))
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "Picked up! Navigate to customer.", Toast.LENGTH_LONG).show()
                        refreshOrderData()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun completeOrder(orderId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.deliverOrder(orderId, AcceptOrderRequest(currentDriverId))
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Order Delivered")
                            .setMessage("Success! The order has been marked as delivered.\n\nEarnings will be added to your wallet.")
                            .setPositiveButton("OK") { _, _ ->
                                resetToIdleState()
                                refreshAllData()
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ORDER_CHANNEL_ID,
                ORDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "New delivery offers"
            channel.enableVibration(true)
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

        val builder = NotificationCompat.Builder(this, ORDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Delivery Offer!")
            .setContentText("Order from $restaurantName - Tap to accept")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(1001, builder.build())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(pollRunnable)
        stopTimer()
    }

    override fun onResume() {
        super.onResume()
        checkDriverStatus()
        refreshOrderData()

        // Ensure service is running when app comes to foreground
        if (!isServiceRunning(OrderForegroundService::class.java)) {
            Log.d("MainActivity", "Service not running, restarting...")
            startForegroundService()
        }
    }
}