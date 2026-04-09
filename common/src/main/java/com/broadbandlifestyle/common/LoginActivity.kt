package com.broadbandlifestyle.common

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var btnLogin: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var apiService: CommonApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutResourceId())

        initViews()
        setupRetrofit()
        setupTextWatchers()
        setupClickListeners()
    }

    protected open fun getLayoutResourceId(): Int {
        return getResourceId("activity_login", "layout")
    }

    private fun getResourceId(name: String, type: String): Int {
        return resources.getIdentifier(name, type, packageName)
    }

    private fun initViews() {
        etEmail = findViewById(getResourceId("etEmail", "id"))
        etPassword = findViewById(getResourceId("etPassword", "id"))
        emailLayout = findViewById(getResourceId("emailLayout", "id"))
        passwordLayout = findViewById(getResourceId("passwordLayout", "id"))
        btnLogin = findViewById(getResourceId("btnLogin", "id"))
        progressBar = findViewById(getResourceId("progressBar", "id"))
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(CommonApiService::class.java)
    }

    private fun setupTextWatchers() {
        etEmail.doAfterTextChanged { emailLayout.error = null }
        etPassword.doAfterTextChanged { passwordLayout.error = null }
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener { attemptLogin() }

        val tvForgotPassword = findViewById<TextView>(getResourceId("tvForgotPassword", "id"))
        tvForgotPassword?.setOnClickListener {
            try {
                val intent = Intent(this, Class.forName("com.broadbandlifestyle.driverapp.ForgotPasswordActivity"))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Forgot Password screen not ready", Toast.LENGTH_SHORT).show()
            }
        }

        val tvRegister = findViewById<TextView>(getResourceId("tvRegister", "id"))
        tvRegister?.setOnClickListener {
            try {
                val intent = Intent(this, Class.forName("com.broadbandlifestyle.driverapp.RegisterActivity"))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Register screen not ready", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun attemptLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (!validateInputs(email, password)) return

        setLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val credentials = mapOf("email" to email, "password" to password)
                val response = apiService.login(credentials)

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    if (response.isSuccessful && response.body() != null) {
                        val loginData = response.body()!!

                        // CRITICAL DEBUG - This will show exactly what was received
                        Log.d("Login", "========== RAW RESPONSE ==========")
                        Log.d("Login", "Response code: ${response.code()}")
                        Log.d("Login", "LoginData: ${loginData.toString()}")
                        Log.d("Login", "role: ${loginData.role}")
                        Log.d("Login", "restaurantId: ${loginData.restaurantId}")
                        Log.d("Login", "restaurantName: ${loginData.restaurantName}")
                        Log.d("Login", "userId: ${loginData.userId}")
                        Log.d("Login", "driverId: ${loginData.driverId}")
                        Log.d("Login", "===================================")

                        val role = loginData.role?.trim()?.lowercase() ?: "user"
                        val userId = loginData.userId ?: -1

                        saveUserData(userId, role, loginData)
                        navigateToMainApp(role, userId, loginData)
                    } else {
                        showError("Invalid login credentials")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    showError("Network Error. Please check your connection.")
                }
            }
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true
        if (TextUtils.isEmpty(email)) {
            emailLayout.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Please enter a valid email address"
            isValid = false
        }

        if (TextUtils.isEmpty(password)) {
            passwordLayout.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordLayout.error = "Password must be at least 6 characters"
            isValid = false
        }
        return isValid
    }

    private fun saveUserData(userId: Int, role: String, loginData: LoginResponse) {
        // 1. Save general user info
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        prefs.edit {
            putInt("USER_ID", userId)
            putString("USER_ROLE", role)
        }

        Log.d("Login", "========================================")
        Log.d("Login", "Role from server: '$role'")
        Log.d("Login", "Is restaurant role? ${isRestaurantRole(role)}")
        Log.d("Login", "restaurantId from response: ${loginData.restaurantId}")
        Log.d("Login", "restaurantName from response: ${loginData.restaurantName}")
        Log.d("Login", "========================================")

        // 2. Save role-specific info
        if (isRestaurantRole(role)) {
            val restaurantIdValue = loginData.restaurantId
            if (restaurantIdValue != null && restaurantIdValue != -1) {
                getSharedPreferences("restaurant_prefs", MODE_PRIVATE).edit(commit = true) {
                    putInt("RESTAURANT_ID", restaurantIdValue)
                    putString("RESTAURANT_NAME", loginData.restaurantName ?: "Restaurant")
                }
                Log.d("Login", "✅ Saved Restaurant ID: $restaurantIdValue")
            } else {
                Log.e("Login", "❌ ERROR: Restaurant ID was null or invalid! Value: $restaurantIdValue")
            }
        } else if (role == "driver") {
            getSharedPreferences("driver_prefs", MODE_PRIVATE).edit {
                putBoolean("IS_ONLINE", true)
            }
            Log.d("Login", "Saved driver prefs")
        } else {
            Log.d("Login", "Role '$role' is not driver or restaurant")
        }
    }

    private fun navigateToMainApp(role: String, userId: Int, loginData: LoginResponse) {
        Log.d("Login", "navigateToMainApp called with role: $role")
        Log.d("Login", "restaurantId: ${loginData.restaurantId}")
        Log.d("Login", "restaurantName: ${loginData.restaurantName}")

        val intent = when {
            role == "driver" -> {
                Intent(this, Class.forName("com.broadbandlifestyle.driverapp.MainActivity")).apply {
                    putExtra("DRIVER_ID", userId)
                    putExtra("START_SERVICE", true)
                }
            }
            isRestaurantRole(role) -> {
                val restaurantIdValue = loginData.restaurantId ?: -1
                Log.d("Login", "Starting RestaurantDashboardActivity with ID: $restaurantIdValue")

                Intent(this, Class.forName("com.broadbandlifestyle.restaurantapp.RestaurantDashboardActivity")).apply {
                    putExtra("RESTAURANT_ID", restaurantIdValue)
                    putExtra("RESTAURANT_NAME", loginData.restaurantName ?: "Restaurant")
                    putExtra("USER_ID", userId)
                }
            }
            else -> {
                Log.d("Login", "Defaulting to ShopActivity for role: $role")
                Intent(this, Class.forName("com.broadbandlifestyle.customerapp.ShopActivity"))
            }
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun isRestaurantRole(role: String): Boolean {
        val r = role.lowercase().trim()
        Log.d("Login", "Checking if role '$r' is a restaurant role")

        // Include ALL possible restaurant role variations
        val result = r == "restaurant owner" ||
                r == "restaurant_staff" ||
                r == "restaurant staff" ||
                r == "restaurant_owner" ||
                r == "restaurant_manager" ||
                r == "restaurant" ||
                r.contains("restaurant")

        Log.d("Login", "Is restaurant role? $result")
        return result
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !isLoading
        btnLogin.text = if (isLoading) "Signing In..." else "Login"
        etEmail.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}