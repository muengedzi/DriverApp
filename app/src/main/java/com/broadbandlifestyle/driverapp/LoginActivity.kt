package com.broadbandlifestyle.driverapp

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
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.customerapp.ShopActivity
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
    private lateinit var apiService: DriverApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        setupRetrofit()
        setupTextWatchers()
        setupClickListeners()
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DriverApiService::class.java)
    }

    private fun setupTextWatchers() {
        // Clear errors when user starts typing
        etEmail.doAfterTextChanged {
            emailLayout.error = null
        }

        etPassword.doAfterTextChanged {
            passwordLayout.error = null
        }
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener { attemptLogin() }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            try {
                val intent = Intent(this, ForgotPasswordActivity::class.java)
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Forgot Password screen not ready", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.googleSignInButton).setOnClickListener {
            Toast.makeText(this, "Google Sign-In coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun attemptLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        // Validate inputs with Material Design error messages
        if (!validateInputs(email, password)) {
            return
        }

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

                        // Save to SharedPreferences
                        saveUserData(userId, role)

                        // Navigate based on role
                        navigateToMainApp(role, userId)
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

        // Email validation
        when {
            TextUtils.isEmpty(email) -> {
                emailLayout.error = "Email is required"
                isValid = false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailLayout.error = "Please enter a valid email address"
                isValid = false
            }
            else -> {
                emailLayout.error = null
            }
        }

        // Password validation
        when {
            TextUtils.isEmpty(password) -> {
                passwordLayout.error = "Password is required"
                isValid = false
            }
            password.length < 6 -> {
                passwordLayout.error = "Password must be at least 6 characters"
                isValid = false
            }
            else -> {
                passwordLayout.error = null
            }
        }

        return isValid
    }

    private fun saveUserData(userId: Int, role: String) {
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        prefs.edit {
            putInt("USER_ID", userId)
            putString("USER_ROLE", role)
        }

        // Save to driver_prefs if role is driver
        if (role == "driver") {
            val driverPrefs = getSharedPreferences("driver_prefs", MODE_PRIVATE)
            driverPrefs.edit {
                putBoolean("IS_ONLINE", true)
            }
        }
    }

    private fun navigateToMainApp(role: String, userId: Int) {
        val intent = if (role == "driver") {
            Intent(this, MainActivity::class.java).apply {
                putExtra("DRIVER_ID", userId)
                putExtra("START_SERVICE", true)
            }
        } else {
            Intent(this, ShopActivity::class.java)
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