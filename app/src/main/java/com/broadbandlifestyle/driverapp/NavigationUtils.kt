package com.broadbandlifestyle.driverapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.drawerlayout.widget.DrawerLayout
import com.broadbandlifestyle.common.LoginActivity
import com.google.android.material.navigation.NavigationView

object NavigationUtils {

    fun setupNavigation(
        context: Context,
        drawerLayout: DrawerLayout,
        navigationView: NavigationView,
        currentDriverId: Int,
        currentActivity: String
    ) {
        // Set the navigation item selected listener
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Only navigate if not already in MainActivity
                    if (currentActivity != "MainActivity") {
                        val intent = Intent(context, MainActivity::class.java)
                        intent.putExtra("DRIVER_ID", currentDriverId)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        context.startActivity(intent)
                    }
                }

                R.id.nav_profile -> {
                    if (currentActivity != "ProfileActivity") {
                        val intent = Intent(context, ProfileActivity::class.java)
                        intent.putExtra("DRIVER_ID", currentDriverId)
                        context.startActivity(intent)
                    }
                }

                R.id.nav_earnings -> {
                    Toast.makeText(context, "Earnings - Coming Soon", Toast.LENGTH_SHORT).show()
                }

                R.id.nav_history -> {
                    Toast.makeText(context, "Delivery History - Coming Soon", Toast.LENGTH_SHORT).show()
                }

                R.id.nav_settings -> {
                    Toast.makeText(context, "Settings - Coming Soon", Toast.LENGTH_SHORT).show()
                }

                R.id.nav_help -> {
                    showHelpDialog(context)
                }

                R.id.nav_logout -> {
                    showLogoutConfirmation(context, currentDriverId)
                }
            }

            // Close the drawer
            drawerLayout.closeDrawers()
            true
        }

        // Update header with driver info
        updateNavigationHeader(context, navigationView, currentDriverId)
    }

    private fun updateNavigationHeader(context: Context, navigationView: NavigationView, driverId: Int) {
        val headerView = navigationView.getHeaderView(0)
        val navHeaderName = headerView.findViewById<TextView>(R.id.navHeaderName)
        val navHeaderStatus = headerView.findViewById<TextView>(R.id.navHeaderStatus)

        // You can fetch this from your shared preferences or API
        navHeaderName.text = "Driver #$driverId"
        navHeaderStatus.text = "Online"
    }

    private fun showHelpDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Help & Support")
            .setMessage("For assistance, please contact our support team:\n\n📞 Phone: +27 725 138 539\n📧 Email: support@broadbandlifestyle.co.za")
            .setPositiveButton("OK", null)
            .setNegativeButton("Call Support") { _, _ ->
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:+27725138539")
                context.startActivity(intent)
            }
            .show()
    }

    private fun showLogoutConfirmation(context: Context, driverId: Int) {
        AlertDialog.Builder(context)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes, Logout") { _, _ ->
                // Clear any saved preferences
                context.getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()

                // Navigate to Login
                val intent = Intent(context, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)

                if (context is MainActivity) {
                    context.finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
