package com.broadbandlifestyle.driverapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.common.UpdateProfileRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ProfileActivity : AppCompatActivity() {

    private lateinit var apiService: DriverApiService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentDriverId: Int = -1
    private var isOnline: Boolean = false

    private lateinit var etProfileName: TextInputEditText
    private lateinit var etProfileVehicle: TextInputEditText
    private lateinit var etProfilePhone: TextInputEditText
    private lateinit var switchAvailable: MaterialSwitch
    private lateinit var btnUpdateProfile: MaterialButton
    private lateinit var btnToggleOnline: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private lateinit var tvSessionInfo: TextView
    private lateinit var tvOnlineStatus: TextView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        setupWindowInsets()

        currentDriverId = intent.getIntExtra("DRIVER_ID", -1)
        if (currentDriverId == -1) {
            currentDriverId = getSharedPreferences("user_prefs", MODE_PRIVATE).getInt("USER_ID", -1)
        }

        if (currentDriverId == -1) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        initializeViews()
        setupRetrofit()
        setupBottomNavigation()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        loadProfile()
        setupOnlineToggle()
        checkDriverStatus()

        btnUpdateProfile.setOnClickListener { saveProfile() }
        btnLogout.setOnClickListener { showLogoutConfirmation() }
        swipeRefresh.setOnRefreshListener { 
            loadProfile()
            checkDriverStatus()
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
        etProfileName = findViewById(R.id.etProfileName)
        etProfileVehicle = findViewById(R.id.etProfileVehicle)
        etProfilePhone = findViewById(R.id.etProfilePhone)
        switchAvailable = findViewById(R.id.switchAvailable)
        btnUpdateProfile = findViewById(R.id.btnUpdateProfile)
        btnToggleOnline = findViewById(R.id.btnToggleOnline)
        btnLogout = findViewById(R.id.btnLogout)
        tvSessionInfo = findViewById(R.id.tvSessionInfo)
        tvOnlineStatus = findViewById(R.id.tvOnlineStatus)
        bottomNav = findViewById(R.id.bottomNavigation)
        swipeRefresh = findViewById(R.id.swipeRefresh)
    }

    private fun setupBottomNavigation() {
        bottomNav.selectedItemId = R.id.nav_profile
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    navigateToDashboard()
                    true
                }
                R.id.nav_earnings -> {
                    startActivity(Intent(this, EarningsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
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

    private fun loadProfile() {
        swipeRefresh.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getDriverProfile(currentDriverId)
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    if (response.isSuccessful) {
                        response.body()?.let { p ->
                            etProfileName.setText(p.fullName ?: "")
                            etProfileVehicle.setText(p.vehicleType ?: "")
                            etProfilePhone.setText(p.phone ?: "")
                            switchAvailable.isChecked = p.isAvailable
                        }
                    } else {
                        Toast.makeText(this@ProfileActivity, "Failed to load profile", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    Log.e("ProfileDebug", "Error loading profile: ${e.message}")
                    Toast.makeText(this@ProfileActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkDriverStatus() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getDriverStatus(currentDriverId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val status = response.body()!!
                        isOnline = status.is_online
                        updateOnlineButton(isOnline)

                        status.session?.let { session ->
                            if (session.active) {
                                val hours = session.online_minutes / 60
                                val minutes = session.online_minutes % 60
                                tvSessionInfo.text = "Online for: ${hours}h ${minutes}m"
                                tvSessionInfo.visibility = android.view.View.VISIBLE
                            } else {
                                tvSessionInfo.visibility = android.view.View.GONE
                            }
                        } ?: run {
                            tvSessionInfo.visibility = android.view.View.GONE
                        }

                        if (status.is_online) {
                            tvOnlineStatus.text = "● ONLINE"
                            tvOnlineStatus.setTextColor(ContextCompat.getColor(this@ProfileActivity, android.R.color.holo_green_dark))
                        } else {
                            tvOnlineStatus.text = "○ OFFLINE"
                            tvOnlineStatus.setTextColor(ContextCompat.getColor(this@ProfileActivity, android.R.color.holo_red_dark))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileDebug", "Error checking status: ${e.message}")
            }
        }
    }

    private fun setupOnlineToggle() {
        isOnline = getSharedPreferences("driver_prefs", MODE_PRIVATE)
            .getBoolean("IS_ONLINE", false)
        updateOnlineButton(isOnline)
        btnToggleOnline.setOnClickListener {
            if (isOnline) showOfflineConfirmation() else goOnline()
        }
    }

    private fun goOnline() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1002)
            return
        }
        btnToggleOnline.isEnabled = false
        btnToggleOnline.text = "Going Online..."
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                toggleOnlineStatus(true, location.latitude, location.longitude)
            } else {
                runOnUiThread {
                    btnToggleOnline.isEnabled = true
                    btnToggleOnline.text = "Go Online"
                    Toast.makeText(this, "Unable to get location. Please enable GPS.", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener { exception ->
            runOnUiThread {
                btnToggleOnline.isEnabled = true
                btnToggleOnline.text = "Go Online"
                Toast.makeText(this, "Unable to get location: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showOfflineConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Go Offline")
            .setMessage("You won't receive new delivery offers while offline. Continue?")
            .setPositiveButton("Go Offline") { _, _ -> toggleOnlineStatus(false) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleOnlineStatus(online: Boolean, lat: Double? = null, lng: Double? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val toggleData = mutableMapOf<String, Any>("driver_id" to currentDriverId, "is_online" to online)
                if (online && lat != null && lng != null) {
                    toggleData["current_lat"] = lat
                    toggleData["current_lng"] = lng
                }
                val response = apiService.toggleOnline(toggleData)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        isOnline = online
                        updateOnlineButton(online)
                        getSharedPreferences("driver_prefs", MODE_PRIVATE).edit().putBoolean("IS_ONLINE", online).apply()
                        if (online) startForegroundService() else stopForegroundService()
                    }
                    btnToggleOnline.isEnabled = true
                    btnToggleOnline.text = if (isOnline) "Go Offline" else "Go Online"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnToggleOnline.isEnabled = true
                    btnToggleOnline.text = if (isOnline) "Go Offline" else "Go Online"
                }
            }
        }
    }

    private fun startForegroundService() {
        val intent = Intent(this, OrderForegroundService::class.java)
        intent.putExtra("DRIVER_ID", currentDriverId)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopForegroundService() {
        val intent = Intent(this, OrderForegroundService::class.java)
        stopService(intent)
    }

    private fun updateOnlineButton(online: Boolean) {
        if (online) {
            btnToggleOnline.text = "Go Offline"
            btnToggleOnline.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tvOnlineStatus.text = "● ONLINE"
            tvOnlineStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            btnToggleOnline.text = "Go Online"
            btnToggleOnline.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            tvOnlineStatus.text = "○ OFFLINE"
            tvOnlineStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun saveProfile() {
        val phone = etProfilePhone.text?.toString() ?: ""
        val isAvailable = switchAvailable.isChecked
        btnUpdateProfile.isEnabled = false
        btnUpdateProfile.text = "Saving..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.updateProfile(UpdateProfileRequest(currentDriverId, phone, isAvailable))
                withContext(Dispatchers.Main) {
                    btnUpdateProfile.isEnabled = true
                    btnUpdateProfile.text = "Save Changes"
                    Toast.makeText(this@ProfileActivity, if(response.isSuccessful) "Profile Updated" else "Update Failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnUpdateProfile.isEnabled = true
                    btnUpdateProfile.text = "Save Changes"
                }
            }
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Logout") { _, _ ->
                getSharedPreferences("user_prefs", MODE_PRIVATE).edit().clear().apply()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
