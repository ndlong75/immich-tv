package com.immichtv.ui

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.immichtv.R
import com.immichtv.api.ImmichClient
import com.immichtv.util.PrefsManager
import kotlinx.coroutines.launch

class SettingsActivity : FragmentActivity() {

    private lateinit var serverUrlInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var testButton: Button
    private lateinit var saveButton: Button
    private lateinit var statusText: TextView
    private lateinit var serverInfoText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        serverUrlInput = findViewById(R.id.input_server_url)
        apiKeyInput = findViewById(R.id.input_api_key)
        testButton = findViewById(R.id.btn_test)
        saveButton = findViewById(R.id.btn_save)
        statusText = findViewById(R.id.text_status)
        serverInfoText = findViewById(R.id.text_server_info)

        // Load existing values
        serverUrlInput.setText(PrefsManager.serverUrl)
        apiKeyInput.setText(PrefsManager.apiKey)

        // Show current status
        if (PrefsManager.isConfigured()) {
            statusText.text = "✅ Connected"
            statusText.setTextColor(Color.parseColor("#4ade80"))
        }

        testButton.setOnClickListener { testConnection() }
        saveButton.setOnClickListener { saveAndExit() }

        // Handle Enter key on inputs to move focus
        serverUrlInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                apiKeyInput.requestFocus()
                true
            } else false
        }

        apiKeyInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                testButton.requestFocus()
                true
            } else false
        }
    }

    private fun testConnection() {
        val url = serverUrlInput.text.toString().trim()
        val key = apiKeyInput.text.toString().trim()

        if (url.isBlank() || key.isBlank()) {
            statusText.text = "❌ Please enter both server URL and API key"
            statusText.setTextColor(Color.parseColor("#f87171"))
            return
        }

        statusText.text = "⏳ Testing connection..."
        statusText.setTextColor(Color.parseColor("#fbbf24"))
        testButton.isEnabled = false

        lifecycleScope.launch {
            try {
                ImmichClient.configure(url, key)
                val version = ImmichClient.getApi().getServerVersion()
                val user = ImmichClient.getApi().getCurrentUser()

                statusText.text = "✅ Connected successfully!"
                statusText.setTextColor(Color.parseColor("#4ade80"))
                serverInfoText.text = "Server: v${version.version}\nUser: ${user.name} (${user.email})"
                serverInfoText.visibility = View.VISIBLE

                // Auto-save on successful test
                PrefsManager.serverUrl = url
                PrefsManager.apiKey = key
                saveButton.requestFocus()

            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("401") == true -> "Invalid API key"
                    e.message?.contains("Unable to resolve") == true -> "Cannot reach server"
                    e.message?.contains("timeout") == true -> "Connection timed out"
                    else -> e.message ?: "Unknown error"
                }
                statusText.text = "❌ Connection failed: $errorMsg"
                statusText.setTextColor(Color.parseColor("#f87171"))
                serverInfoText.visibility = View.GONE
            } finally {
                testButton.isEnabled = true
            }
        }
    }

    private fun saveAndExit() {
        val url = serverUrlInput.text.toString().trim()
        val key = apiKeyInput.text.toString().trim()

        if (url.isBlank() || key.isBlank()) {
            statusText.text = "❌ Please enter both server URL and API key"
            statusText.setTextColor(Color.parseColor("#f87171"))
            return
        }

        PrefsManager.serverUrl = url
        PrefsManager.apiKey = key
        ImmichClient.configure(url, key)

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
