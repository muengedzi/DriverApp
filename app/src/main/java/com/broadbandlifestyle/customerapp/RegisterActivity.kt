package com.broadbandlifestyle.driverapp

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.broadbandlifestyle.common.Constants.BASE_URL
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RegisterActivity : AppCompatActivity() {

    private lateinit var apiService: DriverApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register) // You need to create this XML

        val etFullname = findViewById<EditText>(R.id.etRegFullname)
        val etEmail = findViewById<EditText>(R.id.etRegEmail)
        val etPassword = findViewById<EditText>(R.id.etRegPassword)
        val btnRegister = findViewById<MaterialButton>(R.id.btnRegisterSubmit)

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DriverApiService::class.java)

        btnRegister.setOnClickListener {
            val name = etFullname.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val regData = mapOf(
                "fullname" to name,
                "email" to email,
                "password" to pass
            )

            lifecycleScope.launch {
                try {
                    val response = apiService.register(regData)
                    if (response.isSuccessful) {
                        Toast.makeText(this@RegisterActivity, "Check your email to verify!", Toast.LENGTH_LONG).show()
                        finish() // Go back to login
                    } else {
                        Toast.makeText(this@RegisterActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@RegisterActivity, "Server Error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}