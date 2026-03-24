package com.broadbandlifestyle.customerapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.broadbandlifestyle.driverapp.R
import com.google.android.material.button.MaterialButton

class OrderSuccessActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_success)

        findViewById<MaterialButton>(R.id.btnBackToShop).setOnClickListener {
            // Clears the activity stack so they can't "back" into the checkout again
            val intent = Intent(this, ShopActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}