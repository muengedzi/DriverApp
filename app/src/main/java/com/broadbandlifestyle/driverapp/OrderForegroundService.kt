package com.broadbandlifestyle.driverapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.common.LocationUpdateRequest
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class OrderForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var apiService: DriverApiService
    private var driverId: Int = -1
    private var isBusy: Boolean = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Heartbeat interval: 30 seconds
    private val heartbeatInterval = 30000L

    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            sendHeartbeat()
            heartbeatHandler.postDelayed(this, heartbeatInterval)
        }
    }

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        setupLocationRequests()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newDriverId = intent?.getIntExtra("DRIVER_ID", -1) ?: -1

        // Stop if different driver
        if (driverId != -1 && driverId != newDriverId) {
            stopSelf()
            return START_NOT_STICKY
        }

        driverId = newDriverId
        setupRetrofit()

        // Start foreground with proper type for Android 10+
        startForegroundServiceWithNotification()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Start heartbeat
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        heartbeatHandler.post(heartbeatRunnable)

        // Start location updates
        startLocationUpdates()

        return START_STICKY
    }

    private fun setupLocationRequests() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000) // 30 seconds
            .setMinUpdateDistanceMeters(50f) // Update if moved 50 meters
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    sendLocationUpdate(location)
                }
            }
        }
    }

    private fun startForegroundServiceWithNotification() {
        val notification = createNotification("Driver App: Online")

        // For Android 10+ (API 29+), we need to specify foreground service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startLocationUpdates() {
        // Check permission before requesting location updates
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("GPS_SERVICE", "Location permission not granted")
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("GPS_SERVICE", "Location permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e("GPS_SERVICE", "Error starting location updates: ${e.message}")
        }
    }

    private fun sendHeartbeat() {
        if (driverId == -1) return

        // Use GlobalScope for heartbeat to avoid cancellation issues
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val sharedPref = getSharedPreferences("driver_prefs", MODE_PRIVATE)
                val isOnline = sharedPref.getBoolean("IS_ONLINE", true)

                // Get last known location with permission check
                if (ContextCompat.checkSelfPermission(this@OrderForegroundService,
                        android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            val heartbeatData = mapOf(
                                "driver_id" to driverId,
                                "current_lat" to it.latitude,
                                "current_lng" to it.longitude,
                                "is_online" to isOnline
                            )

                            // Call API in a coroutine
                            GlobalScope.launch(Dispatchers.IO) {
                                try {
                                    val response = apiService.sendHeartbeat(heartbeatData)
                                    if (response.isSuccessful) {
                                        Log.d("Heartbeat", "Heartbeat sent successfully")
                                    } else if (response.code() == 403) {
                                        handleInactiveAccount()
                                    }
                                } catch (e: Exception) {
                                    Log.e("Heartbeat", "Failed to send heartbeat", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Heartbeat", "Error in heartbeat", e)
            }
        }
    }

    private fun sendLocationUpdate(location: Location) {
        if (driverId == -1) return

        serviceScope.launch {
            try {
                val request = LocationUpdateRequest(
                    driverId = driverId,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                val response = apiService.updateLocation(request)
                if (response.code() == 403) {
                    handleInactiveAccount()
                }
            } catch (e: Exception) {
                Log.e("Location", "Failed to update location", e)
            }
        }
    }

    private fun handleInactiveAccount() {
        // Run on main thread
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "Account Inactive: Logging out", Toast.LENGTH_LONG).show()
            stopSelf()

            val intent = Intent(this, LoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DriverApiService::class.java)
    }

    private fun createNotification(content: String): Notification {
        val channelId = "driver_service_channel"
        val channelName = "Driver Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Driver location and status service"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Broadband Lifestyle")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        try {
            if (::locationCallback.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        } catch (e: SecurityException) {
            Log.e("GPS_SERVICE", "Error removing location updates", e)
        } catch (e: Exception) {
            Log.e("GPS_SERVICE", "Error in onDestroy", e)
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}