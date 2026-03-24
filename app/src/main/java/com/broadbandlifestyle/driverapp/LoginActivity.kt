package com.broadbandlifestyle.driverapp

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.broadbandlifestyle.common.LoginResponse
import com.broadbandlifestyle.customerapp.ShopActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var apiService: DriverApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DriverApiService::class.java)

        btnLogin.setOnClickListener { attemptLogin() }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            try {
                val intent = Intent(this, ForgotPasswordActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Forgot Password screen not ready", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun attemptLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
            return
        }

        btnLogin.isEnabled = false
        btnLogin.text = "Signing In..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val credentials = mapOf("email" to email, "password" to password)
                val response = apiService.login(credentials)

                withContext(Dispatchers.Main) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "Login"

                    if (response.isSuccessful && response.body() != null) {
                        val loginData = response.body()!!
                        val role = loginData.role?.trim()?.lowercase() ?: "user"
                        val userId = loginData.userId ?: -1

                        // Save to Prefs
                        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
                        prefs.edit().putInt("USER_ID", userId).apply()

                        // Also save to driver_prefs
                        val driverPrefs = getSharedPreferences("driver_prefs", MODE_PRIVATE)
                        driverPrefs.edit().putBoolean("IS_ONLINE", true).apply()

                        val intent = if (role == "driver") {
                            Intent(this@LoginActivity, MainActivity::class.java).apply {
                                putExtra("DRIVER_ID", userId)
                                putExtra("START_SERVICE", true) // Flag to start service
                            }
                        } else {
                            Intent(this@LoginActivity, ShopActivity::class.java)
                        }

                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "Invalid login credentials", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "Login"
                    Toast.makeText(this@LoginActivity, "Network Error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}