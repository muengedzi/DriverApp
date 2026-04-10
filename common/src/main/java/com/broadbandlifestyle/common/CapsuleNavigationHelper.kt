package com.broadbandlifestyle.common

import android.content.res.ColorStateList
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.broadbandlifestyle.common.R

/**
 * Helper to manage the floating capsule navigation bar used across different apps.
 */
object CapsuleNavigationHelper {

    fun setupCapsuleNavigation(
        activity: AppCompatActivity,
        menuResId: Int,
        onItemSelected: (Int) -> Boolean
    ) {
        try {
            // These IDs are defined in common/src/main/res/values/ids.xml
            val capsuleContainer = activity.findViewById<FrameLayout>(R.id.capsuleNavContainer)
            val bottomNav = capsuleContainer?.findViewById<BottomNavigationView>(R.id.capsuleNavigation)

            bottomNav?.let { navigationView ->
                navigationView.menu.clear()
                navigationView.inflateMenu(menuResId)
                navigationView.setOnItemSelectedListener { menuItem ->
                    onItemSelected(menuItem.itemId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateCapsuleTheme(activity: AppCompatActivity, primaryColor: Int) {
        try {
            val capsuleContainer = activity.findViewById<FrameLayout>(R.id.capsuleNavContainer)
            val bottomNav = capsuleContainer?.findViewById<BottomNavigationView>(R.id.capsuleNavigation)

            bottomNav?.let {
                it.itemIconTintList = ColorStateList.valueOf(primaryColor)
                it.itemTextColor = ColorStateList.valueOf(primaryColor)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
