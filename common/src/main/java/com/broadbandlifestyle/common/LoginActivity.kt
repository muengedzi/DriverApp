package com.broadbandlifestyle.common

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
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
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        prefs.edit {
            putInt("USER_ID", userId)
            putString("USER_ROLE", role)
        }

        when (role) {
            "driver" -> {
                getSharedPreferences("driver_prefs", MODE_PRIVATE).edit {
                    putBoolean("IS_ONLINE", true)
                }
            }
            "restaurant staff", "restaurant_owner", "restaurant_staff" -> {
                loginData.restaurantId?.let {
                    getSharedPreferences("restaurant_prefs", MODE_PRIVATE).edit {
                        putInt("RESTAURANT_ID", it)
                        putString("RESTAURANT_NAME", loginData.restaurantName)
                    }
                }
            }
        }
    }

    private fun navigateToMainApp(role: String, userId: Int, loginData: LoginResponse) {
        val intent = when {
            role == "driver" -> {
                Intent(this, Class.forName("com.broadbandlifestyle.driverapp.MainActivity")).apply {
                    putExtra("DRIVER_ID", userId)
                    putExtra("START_SERVICE", true)
                }
            }
            role == "restaurant staff" || role == "restaurant_owner" || role == "restaurant_staff" -> {
                Intent(this, Class.forName("com.broadbandlifestyle.restaurantapp.RestaurantDashboardActivity")).apply {
                    putExtra("RESTAURANT_ID", loginData.restaurantId ?: -1)
                    putExtra("RESTAURANT_NAME", loginData.restaurantName ?: "Restaurant")
                    putExtra("USER_ID", userId)
                }
            }
            else -> {
                Intent(this, Class.forName("com.broadbandlifestyle.customerapp.ShopActivity"))
            }
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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