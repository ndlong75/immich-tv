package com.immichtv.ui

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.immichtv.R
import com.immichtv.util.PrefsManager

class MainActivity : FragmentActivity() {

    private var hasLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // If not configured, go to settings first
        if (!PrefsManager.isConfigured()) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, HomeFragment())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        // Only refresh on SUBSEQUENT resumes (returning from settings/other activities)
        // Skip the first one since onActivityCreated already loads data
        if (hasLoaded) {
            val fragment = supportFragmentManager.findFragmentById(R.id.main_frame)
            if (fragment is HomeFragment) {
                fragment.refreshData()
            }
        }
        hasLoaded = true
    }
}
