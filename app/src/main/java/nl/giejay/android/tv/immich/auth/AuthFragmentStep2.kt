package nl.giejay.android.tv.immich.auth

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionEditText
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.runBlocking
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClientFactory
import nl.giejay.android.tv.immich.api.model.LoginRequest
import nl.giejay.android.tv.immich.api.service.ApiService
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
    fun isValid(): Boolean {
        return hostName.isNotBlank() && email.isNotBlank() && password.isNotBlank()
                && (hostName.startsWith("http://") || hostName.startsWith("https://"))
    }
}

class AuthFragmentStep2 : GuidedStepSupportFragment() {
    private val ACTION_HOST = 0L
    private val ACTION_EMAIL = 1L
    private val ACTION_PASSWORD = 2L
    private val ACTION_CHECK_CERTS = 3L
    private val ACTION_DEBUG_MODE = 4L
    private val ACTION_CONTINUE = 5L

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val icon: Drawable = requireContext().getDrawable(R.drawable.icon)!!
        return GuidanceStylist.Guidance(getString(R.string.app_name), "Sign in with your Immich email and password", "", icon)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val currentHost = PreferenceManager.get(HOST_NAME)
        addEditableAction(actions, ACTION_HOST, getString(R.string.server_url_hint), currentHost.ifEmpty { "http://192.168.10.2:2283" }, InputType.TYPE_CLASS_TEXT)
        addEditableAction(actions, ACTION_EMAIL, "Email", "ndlong75@gmail.com", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        addEditableAction(actions, ACTION_PASSWORD, "Password", "nice", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
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
        val entry = AuthSettings(getState(ACTION_HOST), getState(ACTION_EMAIL), getState(ACTION_PASSWORD))
        if (!entry.isValid()) {
            Toast.makeText(activity, when { entry.hostName.isEmpty() -> getString(R.string.enter_server_url); entry.email.isEmpty() -> "Please enter your email"; entry.password.isEmpty() -> "Please enter your password"; else -> getString(R.string.enter_valid_server_url) }, Toast.LENGTH_SHORT).show()
            return
        }
        val disableSsl = findActionById(ACTION_CHECK_CERTS)?.isChecked == true
        val debugMode = findActionById(ACTION_DEBUG_MODE)?.isChecked == true
        try {
            val baseUrl = entry.hostName.trimEnd('/') + "/api/"
            val service = Retrofit.Builder().baseUrl(baseUrl).client(ApiClientFactory.getUnauthClient(disableSsl)).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            var accessToken: String? = null
            runBlocking {
                val response = service.login(LoginRequest(entry.email, entry.password))
                if (response.isSuccessful && response.body() != null) { accessToken = response.body()!!.accessToken }
                else { activity?.runOnUiThread { Toast.makeText(activity, if (response.code() == 401) "Wrong email or password" else "Login failed: ${response.code()}", Toast.LENGTH_LONG).show() } }
            }
            if (accessToken != null) {
                PreferenceManager.save(SCREENSAVER_ALBUMS, emptySet())
                PreferenceManager.save(API_KEY, "Bearer:$accessToken")
                PreferenceManager.save(HOST_NAME, entry.hostName.trimEnd('/'))
                PreferenceManager.save(DISABLE_SSL_VERIFICATION, disableSsl)
                PreferenceManager.save(DEBUG_MODE, debugMode)
                findNavController().navigate(AuthFragmentStep2Directions.actionGlobalHomeFragment(), NavOptions.Builder().setPopUpTo(R.id.authFragment, true).build())
            }
        } catch (e: Exception) { Timber.e(e, "Login failed"); Toast.makeText(activity, "Connection error: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun getState(actionId: Long): String {
        val position = findActionPositionById(actionId)
        return getActionItemView(position)?.findViewById<GuidedActionEditText>(R.id.guidedactions_item_description)?.text.toString()
    }
}
