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

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val etIdentifier = findViewById<EditText>(R.id.etForgotIdentifier)
        val btnReset = findViewById<MaterialButton>(R.id.btnResetSubmit)

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val apiService = retrofit.create(DriverApiService::class.java)

        btnReset.setOnClickListener {
            val identifier = etIdentifier.text.toString().trim()
            if (identifier.isEmpty()) return@setOnClickListener

            lifecycleScope.launch {
                try {
                    // Your API expects a Map with "phone" (per your Flask logic)
                    val response = apiService.forgotPassword(mapOf("phone" to identifier))
                    if (response.isSuccessful) {
                        Toast.makeText(this@ForgotPasswordActivity, "Reset link sent to your email!", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        Toast.makeText(this@ForgotPasswordActivity, "Account not found", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ForgotPasswordActivity, "Error connecting to server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}