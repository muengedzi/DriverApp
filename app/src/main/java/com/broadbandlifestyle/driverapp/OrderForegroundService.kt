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

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    companion object {
        const val ACTION_STOP_RINGTONE = "com.broadbandlifestyle.driverapp.STOP_RINGTONE"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        setupLocationRequests()
        setupRetrofit()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check if we are being called just to stop the ringing
        if (intent?.action == ACTION_STOP_RINGTONE) {
            resetNotification()
            return START_STICKY
        }

        val newDriverId = intent?.getIntExtra("DRIVER_ID", -1) ?: -1

        if (driverId != -1 && driverId != newDriverId) {
            stopSelf()
            return START_NOT_STICKY
        }

        driverId = newDriverId

        startForegroundServiceWithNotification()

        if (heartbeatJob == null || heartbeatJob?.isActive == false) {
            startHeartbeatLoop()
        }

        startLocationUpdates()

        return START_STICKY
    }

    private fun resetNotification() {
        // This replaces the "Ringing" notification with the quiet "Online" one
        val notification = createNotification("Driver App: Online")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d("OrderService", "Ringtone/Alert stopped via notification reset")
    }

    private fun setupLocationRequests() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000)
            .setMinUpdateDistanceMeters(50f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    sendLocationUpdate(location)
                }
            }
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                sendHeartbeat()
                delay(30000)
            }
        }
    }

    private fun sendHeartbeat() {
        if (driverId == -1) return
        val sharedPref = getSharedPreferences("driver_prefs", MODE_PRIVATE)
        val isOnline = sharedPref.getBoolean("IS_ONLINE", true)

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val heartbeatData = mapOf(
                    "driver_id" to driverId,
                    "current_lat" to (location?.latitude ?: 0.0),
                    "current_lng" to (location?.longitude ?: 0.0),
                    "is_online" to isOnline
                )

                serviceScope.launch {
                    try {
                        val response = apiService.sendHeartbeat(heartbeatData)
                        if (response.isSuccessful) {
                            Log.d("Heartbeat", "Driver $driverId checked in")
                        } else if (response.code() == 403) {
                            handleInactiveAccount()
                        }
                    } catch (e: Exception) {
                        Log.e("Heartbeat", "Error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun sendLocationUpdate(location: Location) {
        if (driverId == -1) return
        serviceScope.launch {
            try {
                val request = LocationUpdateRequest(driverId, location.latitude, location.longitude)
                apiService.updateLocation(request)
            } catch (e: Exception) {
                Log.e("Location", "Sync failed: ${e.message}")
            }
        }
    }

    private fun startForegroundServiceWithNotification() {
        val notification = createNotification("Driver App: Online")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: Exception) {
            Log.e("GPS_SERVICE", "Error: ${e.message}")
        }
    }

    private fun handleInactiveAccount() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "Account Inactive: Logging out", Toast.LENGTH_LONG).show()
            stopSelf()
            val intent = Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Driver Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Broadband Lifestyle")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        heartbeatJob?.cancel()
        serviceScope.cancel()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        super.onDestroy()
    }
}