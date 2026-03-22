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
import com.immichtv.api.LoginRequest
import com.immichtv.util.PrefsManager
import kotlinx.coroutines.launch

class SettingsActivity : FragmentActivity() {

    private lateinit var serverUrlInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var logoutButton: Button
    private lateinit var statusText: TextView
    private lateinit var serverInfoText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        serverUrlInput = findViewById(R.id.input_server_url)
        emailInput = findViewById(R.id.input_email)
        passwordInput = findViewById(R.id.input_password)
        loginButton = findViewById(R.id.btn_login)
        logoutButton = findViewById(R.id.btn_logout)
        statusText = findViewById(R.id.text_status)
        serverInfoText = findViewById(R.id.text_server_info)

        // Load existing values or defaults
        serverUrlInput.setText(
            PrefsManager.serverUrl.ifBlank { "http://192.168.10.2:2283" }
        )
        emailInput.setText(
            PrefsManager.userEmail.ifBlank { "ndlong75@gmail.com" }
        )

        // Pre-fill password if first time
        if (!PrefsManager.isConfigured()) {
            passwordInput.setText("nice")
        }
        passwordInput.setText("nice")
        if (!PrefsManager.isConfigured()) {
            passwordInput.setText("nice")
        }

        // Show current login status
        if (PrefsManager.isConfigured()) {
            showLoggedInState()
        }

        loginButton.setOnClickListener { doLogin() }
        logoutButton.setOnClickListener { doLogout() }

        // Handle Enter key navigation
        serverUrlInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                emailInput.requestFocus()
                true
            } else false
        }

        emailInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                passwordInput.requestFocus()
                true
            } else false
        }

        passwordInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                loginButton.requestFocus()
                true
            } else false
        }
    }

    private fun doLogin() {
        val url = serverUrlInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (url.isBlank() || email.isBlank() || password.isBlank()) {
            statusText.text = "❌ Please fill in all fields"
            statusText.setTextColor(Color.parseColor("#f87171"))
            return
        }

        statusText.text = "⏳ Signing in..."
        statusText.setTextColor(Color.parseColor("#fbbf24"))
        loginButton.isEnabled = false

        lifecycleScope.launch {
            try {
                // Step 1: Login to get access token
                ImmichClient.configureForLogin(url)
                val loginResponse = ImmichClient.getUnauthApi().login(
                    LoginRequest(email = email, password = password)
                )

                // Step 2: Configure authenticated client
                ImmichClient.configure(url, loginResponse.accessToken)

                // Step 3: Verify by fetching server info
                val version = ImmichClient.getApi().getServerVersion()

                // Step 4: Save credentials
                PrefsManager.serverUrl = url
                PrefsManager.accessToken = loginResponse.accessToken
                PrefsManager.userEmail = email
                PrefsManager.userName = loginResponse.name

                statusText.text = "✅ Signed in successfully!"
                statusText.setTextColor(Color.parseColor("#4ade80"))
                serverInfoText.text = "Server: v${version.version}\nUser: ${loginResponse.name} (${email})"
                serverInfoText.visibility = View.VISIBLE

                showLoggedInState()

                // Auto-close after successful login
                Toast.makeText(this@SettingsActivity, "Welcome, ${loginResponse.name}!", Toast.LENGTH_SHORT).show()

                // Small delay then go back
                window.decorView.postDelayed({ finish() }, 1500)

            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("401") == true -> "Wrong email or password"
                    e.message?.contains("Unable to resolve") == true -> "Cannot reach server"
                    e.message?.contains("timeout") == true -> "Connection timed out"
                    else -> e.message ?: "Unknown error"
                }
                statusText.text = "❌ Login failed: $errorMsg"
                statusText.setTextColor(Color.parseColor("#f87171"))
                serverInfoText.visibility = View.GONE
            } finally {
                loginButton.isEnabled = true
            }
        }
    }

    private fun doLogout() {
        PrefsManager.clear()
        statusText.text = "Signed out"
        statusText.setTextColor(Color.parseColor("#9CA3AF"))
        serverInfoText.visibility = View.GONE
        logoutButton.visibility = View.GONE
        passwordInput.setText("")
        emailInput.setText("")
        loginButton.text = "Sign In"
        serverUrlInput.requestFocus()
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
    }

    private fun showLoggedInState() {
        val name = PrefsManager.userName
        val email = PrefsManager.userEmail
        statusText.text = "✅ Signed in as $name"
        statusText.setTextColor(Color.parseColor("#4ade80"))
        serverInfoText.text = email
        serverInfoText.visibility = View.VISIBLE
        logoutButton.visibility = View.VISIBLE
        loginButton.text = "Re-login"
    }
}
