package com.broadbandlifestyle.driverapp

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.*
import com.broadbandlifestyle.common.BalanceResponse
import com.broadbandlifestyle.common.WithdrawalRequest
import com.broadbandlifestyle.driverapp.R
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class WithdrawalDialog(
    context: Context,
    private val driverId: Int,
    private val balanceInfo: BalanceResponse,
    private val apiService: DriverApiService,
    private val onSuccess: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_withdrawal)

        val tvFeeInfo = findViewById<TextView>(R.id.tvFeeInfo)
        val rgType = findViewById<RadioGroup>(R.id.rgWithdrawalType)
        val etAmount = findViewById<TextInputEditText>(R.id.etAmount)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnConfirm = findViewById<Button>(R.id.btnConfirm)

        // Set fee info text
        val fee = balanceInfo.instantFee
        tvFeeInfo.text = String.format(
            Locale.getDefault(),
            "Instant fee: %.1f%% (min R%.2f, max R%.2f)\nAvailable: R%.2f",
            fee.feePercentage, fee.minFee, fee.maxFee, balanceInfo.availableBalance
        )

        btnCancel.setOnClickListener { dismiss() }

        btnConfirm.setOnClickListener {
            val amountStr = etAmount.text.toString()
            if (amountStr.isEmpty()) {
                etAmount.error = "Enter amount"
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull() ?: 0.0
            if (amount < balanceInfo.minWithdrawal) {
                etAmount.error = String.format("Minimum withdrawal is R%.2f", balanceInfo.minWithdrawal)
                return@setOnClickListener
            }
            if (amount > balanceInfo.availableBalance) {
                etAmount.error = "Insufficient available balance"
                return@setOnClickListener
            }

            val type = if (rgType.checkedRadioButtonId == R.id.rbInstant) "instant" else "scheduled"
            
            performWithdrawal(amount, type)
        }
    }

    private fun performWithdrawal(amount: Double, type: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = WithdrawalRequest(
                    driverId = driverId,
                    amount = amount,
                    withdrawalType = type,
                    paymentMethod = "bank_transfer" // Default
                )
                val response = apiService.requestWithdrawal(request)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Withdrawal requested successfully", Toast.LENGTH_LONG).show()
                        onSuccess()
                        dismiss()
                    } else {
                        Toast.makeText(context, "Failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
