package nl.giejay.android.tv.immich.auth

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionEditText
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClientFactory
import nl.giejay.android.tv.immich.api.model.LoginRequest
import nl.giejay.android.tv.immich.api.service.ApiService
import nl.giejay.android.tv.immich.security.ApiKeyCipher
import nl.giejay.android.tv.immich.shared.guidedstep.GuidedStepUtil.addAction
import nl.giejay.android.tv.immich.shared.guidedstep.GuidedStepUtil.addCheckedAction
import nl.giejay.android.tv.immich.shared.guidedstep.GuidedStepUtil.addEditableAction
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

data class AuthSettings(val hostName: String, val email: String, val password: String) {
    fun isValid(): Boolean = hostName.isNotBlank() && email.isNotBlank() && password.isNotBlank()
            && (hostName.startsWith("http://") || hostName.startsWith("https://"))
}

class AuthFragmentStep2 : GuidedStepSupportFragment() {
    private val ACTION_HOST = 0L
    private val ACTION_EMAIL = 1L
    private val ACTION_PASSWORD = 2L
    private val ACTION_CHECK_CERTS = 3L
    private val ACTION_DEBUG_MODE = 4L
    private val ACTION_CONTINUE = 5L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT in 23..28 && !LoginBackup.hasPermission(requireContext())) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            refillFromBackup()
        }
    }

    private fun refillFromBackup() {
        val backup = LoginBackup.load(requireContext()) ?: return
        mapOf(ACTION_HOST to backup.host, ACTION_EMAIL to backup.email, ACTION_PASSWORD to backup.password).forEach { (id, value) ->
            if (value.isBlank()) return@forEach
            if (id == ACTION_PASSWORD) storedPassword = value
            findActionById(id)?.let {
                it.description = if (id == ACTION_PASSWORD) PASSWORD_MASK else value
                notifyActionChanged(findActionPositionById(id))
            }
        }
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val icon: Drawable = requireContext().getDrawable(R.drawable.icon)!!
        return GuidanceStylist.Guidance(getString(R.string.app_name), "Sign in with your Immich email and password", "", icon)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val backup = LoginBackup.load(requireContext())
        val host = backup?.host?.takeIf { it.isNotBlank() } ?: PreferenceManager.hostName.ifEmpty { "http://192.168.10.2:2283" }
        val email = backup?.email?.takeIf { it.isNotBlank() } ?: PreferenceManager.sharedPreference.getString("last_login_email", "") ?: ""
        val password = backup?.password?.takeIf { it.isNotBlank() } ?: savedPassword()
        addEditableAction(actions, ACTION_HOST, getString(R.string.server_url_hint), host, InputType.TYPE_CLASS_TEXT)
        addEditableAction(actions, ACTION_EMAIL, "Email", email, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        // Never show a saved password in clear text; the real value is kept in storedPassword.
        storedPassword = password.takeIf { it.isNotEmpty() }
        addEditableAction(actions, ACTION_PASSWORD, "Password", if (password.isEmpty()) "" else PASSWORD_MASK, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        addCheckedAction(actions, ACTION_CHECK_CERTS, getString(R.string.disable_ssl_verification), getString(R.string.disable_ssl_verification_desc), PreferenceManager.get(DISABLE_SSL_VERIFICATION))
        addCheckedAction(actions, ACTION_DEBUG_MODE, getString(R.string.debug_mode), getString(R.string.debug_mode_desc), PreferenceManager.get(DEBUG_MODE))
    }

    override fun onCreateButtonActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        super.onCreateButtonActions(actions, savedInstanceState)
        addAction(actions, ACTION_CONTINUE, "Sign In", "")
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        super.onGuidedActionClicked(action)
        if (action.id != ACTION_CONTINUE) return
        val typedPassword = getState(ACTION_PASSWORD)
        val password = if (typedPassword == PASSWORD_MASK) storedPassword ?: "" else typedPassword
        val entry = AuthSettings(getState(ACTION_HOST).trim(), getState(ACTION_EMAIL).trim(), password)
        if (!entry.isValid()) {
            Toast.makeText(activity, when {
                entry.hostName.isEmpty() -> getString(R.string.enter_server_url)
                entry.email.isEmpty() -> "Please enter your email"
                entry.password.isEmpty() -> "Please enter your password"
                else -> getString(R.string.enter_valid_server_url)
            }, Toast.LENGTH_SHORT).show()
            return
        }
        LoginBackup.save(requireContext(), LoginBackup.Login(entry.hostName.trimEnd('/'), entry.email, entry.password))
        val disableSsl = findActionById(ACTION_CHECK_CERTS)?.isChecked == true
        val debugMode = findActionById(ACTION_DEBUG_MODE)?.isChecked == true
        val host = entry.hostName.trimEnd('/')
        lifecycleScope.launch {
            try {
                val service = Retrofit.Builder()
                    .baseUrl("$host/api/")
                    .client(ApiClientFactory.getUnauthClient(disableSsl))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
                val response = service.login(LoginRequest(entry.email, entry.password))
                val token = response.body()?.accessToken
                if (response.isSuccessful && token != null) {
                    PreferenceManager.save(SCREENSAVER_ALBUMS, emptySet())
                    PreferenceManager.sharedPreference.edit()
                        .putString("last_login_email", entry.email)
                        .putString("last_login_password", ApiKeyCipher.encrypt(entry.password))
                        .apply()
                    PreferenceManager.save(API_KEY, "Bearer:$token")
                    PreferenceManager.save(HOST_NAME, host)
                    PreferenceManager.save(DISABLE_SSL_VERIFICATION, disableSsl)
                    PreferenceManager.save(DEBUG_MODE, debugMode)
                    findNavController().navigate(AuthFragmentStep2Directions.actionGlobalHomeFragment(), NavOptions.Builder().setPopUpTo(R.id.authFragment, true).build())
                } else {
                    Toast.makeText(activity, if (response.code() == 401) "Wrong email or password" else "Login failed: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Login failed")
                Toast.makeText(activity, "Connection error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onGuidedActionEditedAndProceed(action: GuidedAction): Long {
        if (action.id == ACTION_PASSWORD) {
            val text = action.description?.toString().orEmpty()
            // Typing after the mask starts a new password; clearing the field forgets the old one.
            if (text != PASSWORD_MASK) {
                storedPassword = null
                if (text.startsWith(PASSWORD_MASK)) action.description = text.removePrefix(PASSWORD_MASK)
            }
        }
        return super.onGuidedActionEditedAndProceed(action)
    }

    private fun savedPassword(): String =
        ApiKeyCipher.decryptOrAdoptLegacy(PreferenceManager.sharedPreference.getString("last_login_password", "") ?: "")

    private fun getState(actionId: Long): String {
        // there really does not seem to be a better way to handle hardware keyboard input or for example: adb shell input text myText
        // https://github.com/giejay/Immich-Android-TV/issues/4
        val position = findActionPositionById(actionId)
        return getActionItemView(position)?.findViewById<GuidedActionEditText>(R.id.guidedactions_item_description)?.text.toString()
    }

    private var storedPassword: String? = null

    private companion object {
        const val PASSWORD_MASK = "••••••••"
        const val REQUEST_STORAGE = 1001
    }
}
