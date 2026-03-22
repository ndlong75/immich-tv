package com.immichtv

import android.app.Application
import com.immichtv.api.ImmichClient
import com.immichtv.util.PrefsManager

class ImmichTVApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PrefsManager.init(this)

        // Initialize API client if already logged in
        if (PrefsManager.isConfigured()) {
            ImmichClient.configure(PrefsManager.serverUrl, PrefsManager.accessToken)
        }
    }
}
