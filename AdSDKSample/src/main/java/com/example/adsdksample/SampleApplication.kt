package com.example.adsdksample

import android.app.Application
import com.example.adsdk.AdSdk
import com.example.adsdk.AdSdkConfig
import com.example.adsdk.LogLevel

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize the AdSDK
        val config = AdSdkConfig(
            apiKey = "sample-api-key", // Replace with your actual API key if needed
            testMode = true,           // Use test mode for development/testing
            logLevel = LogLevel.DEBUG  // Set appropriate log level
        )
        AdSdk.initialize(this, config)
    }
}
