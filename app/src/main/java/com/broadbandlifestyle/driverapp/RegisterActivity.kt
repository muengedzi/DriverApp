package com.broadbandlifestyle.driverapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RegisterActivity : AppCompatActivity() {

    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var nameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var btnRegister: MaterialButton
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var passwordStrengthIndicator: LinearProgressIndicator
    private lateinit var apiService: DriverApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        initViews()
        setupRetrofit()
        setupTextWatchers()
        setupClickListeners()
    }

    private fun initViews() {
        etFullName = findViewById(R.id.etRegFullname)
        etEmail = findViewById(R.id.etRegEmail)
        etPassword = findViewById(R.id.etRegPassword)
        etConfirmPassword = findViewById(R.id.etRegConfirmPassword)
        nameLayout = findViewById(R.id.nameLayout)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout)
        btnRegister = findViewById(R.id.btnRegisterSubmit)
        progressBar = findViewById(R.id.progressBar)
        passwordStrengthIndicator = findViewById(R.id.passwordStrengthIndicator)
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DriverApiService::class.java)
    }

    private fun setupTextWatchers() {
        // Clear errors when typing
        etFullName.doAfterTextChanged { nameLayout.error = null }
        etEmail.doAfterTextChanged { emailLayout.error = null }
        etPassword.doAfterTextChanged {
            passwordLayout.error = null
            checkPasswordStrength()
        }
        etConfirmPassword.doAfterTextChanged { confirmPasswordLayout.error = null }
    }

    private fun checkPasswordStrength() {
        val password = etPassword.text.toString()
        val strength = calculatePasswordStrength(password)

        when (strength) {
            0 -> {
                passwordStrengthIndicator.visibility = View.GONE
            }
            1 -> {
                passwordStrengthIndicator.visibility = View.VISIBLE
                passwordStrengthIndicator.progress = 25
                passwordStrengthIndicator.setIndicatorColor(Color.RED)
            }
            2 -> {
                passwordStrengthIndicator.visibility = View.VISIBLE
                passwordStrengthIndicator.progress = 50
                passwordStrengthIndicator.setIndicatorColor(Color.parseColor("#FFA500")) // Orange
            }
            3 -> {
                passwordStrengthIndicator.visibility = View.VISIBLE
                passwordStrengthIndicator.progress = 75
                passwordStrengthIndicator.setIndicatorColor(Color.YELLOW)
            }
            4 -> {
                passwordStrengthIndicator.visibility = View.VISIBLE
                passwordStrengthIndicator.progress = 100
                passwordStrengthIndicator.setIndicatorColor(Color.GREEN)
            }
        }
    }

    private fun calculatePasswordStrength(password: String): Int {
        if (password.isEmpty()) return 0

        var strength = 0
        if (password.length >= 8) strength++
        if (password.any { it.isDigit() }) strength++
        if (password.any { it.isUpperCase() }) strength++
        if (password.any { !it.isLetterOrDigit() }) strength++

        return strength
    }

    private fun setupClickListeners() {
        btnRegister.setOnClickListener { attemptRegistration() }

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.tvLoginLink).setOnClickListener {
            finish() // Go back to login
        }
    }

    private fun attemptRegistration() {
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        if (!validateInputs(fullName, email, password, confirmPassword)) {
            return
        }

        setLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val registrationData = mapOf(
                    "name" to fullName,
                    "email" to email,
                    "password" to password,
                    "role" to "driver"
                )

                val response = apiService.register(registrationData)

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@RegisterActivity,
                            "Registration successful! Please login.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish() // Go back to login screen
                    } else {
                        val errorMessage = when (response.code()) {
                            409 -> "Email already exists"
                            400 -> "Invalid information provided"
                            else -> "Registration failed. Please try again."
                        }
                        showError(errorMessage)
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

    private fun validateInputs(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        // Full Name validation
        if (TextUtils.isEmpty(fullName)) {
            nameLayout.error = "Full name is required"
            isValid = false
        } else if (fullName.length < 3) {
            nameLayout.error = "Name must be at least 3 characters"
            isValid = false
        } else {
            nameLayout.error = null
        }

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

        // Confirm Password validation
        when {
            TextUtils.isEmpty(confirmPassword) -> {
                confirmPasswordLayout.error = "Please confirm your password"
                isValid = false
            }
            confirmPassword != password -> {
                confirmPasswordLayout.error = "Passwords do not match"
                isValid = false
            }
            else -> {
                confirmPasswordLayout.error = null
            }
        }

        return isValid
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !isLoading
        btnRegister.text = if (isLoading) "Creating Account..." else "Create Account"
        etFullName.isEnabled = !isLoading
        etEmail.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading
        etConfirmPassword.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
