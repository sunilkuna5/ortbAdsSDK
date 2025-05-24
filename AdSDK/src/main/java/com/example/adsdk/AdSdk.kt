package com.example.adsdk

import android.content.Context
import android.util.Log

object AdSdk {

    private var config: AdSdkConfig? = null
    private var applicationContext: Context? = null
    private var initialized: Boolean = false
    private const val TAG = "AdSdkInternal"
    private const val SDK_VERSION = "1.0.0-alpha"
    
    // Static mutable field for log level, initialized to a default.
    // AdSdkConfig can override it during init, and setLogLevel can override it anytime.
    private var currentLogLevel: LogLevel = LogLevel.INFO 

    /**
     * Initializes the AdSDK. This method should be called once, typically in the Application's onCreate method.
     *
     * @param context The application context.
     * @param sdkConfig Configuration for the AdSDK.
     */
    @JvmStatic
    fun initialize(context: Context, sdkConfig: AdSdkConfig) {
        if (initialized) {
            log(LogLevel.WARNING, TAG, "AdSDK is already initialized. Ignoring subsequent initialization call.")
            return
        }
        this.applicationContext = context.applicationContext
        this.config = sdkConfig
        this.currentLogLevel = sdkConfig.logLevel // Set log level from config at init
        this.initialized = true

        log(LogLevel.INFO, "AdSdk", "AdSDK v$SDK_VERSION initialized successfully. Config: $sdkConfig")
        if (sdkConfig.testMode) {
            log(LogLevel.INFO, "AdSdk", "AdSDK is running in Test Mode.")
        }
    }

    /**
     * Checks if the AdSDK has been initialized.
     * @return True if initialize() has been called, false otherwise.
     */
    @JvmStatic
    fun isInitialized(): Boolean = initialized

    /**
     * Gets the current version of the AdSDK.
     * @return The SDK version string.
     */
    @JvmStatic
    fun getSdkVersion(): String = SDK_VERSION
    
    /**
     * Sets the global log level for the SDK.
     * This can be called before or after initialization.
     */
    @JvmStatic
    fun setLogLevel(level: LogLevel) {
        this.currentLogLevel = level
        // Update config if already initialized, so getCurrentConfig() reflects the change.
        this.config?.let {
            this.config = it.copy(logLevel = level)
        }
        log(LogLevel.INFO, "AdSdk", "AdSDK LogLevel changed to: $level")
    }


    // Internal getters for SDK components
    internal fun getApiKey(): String? = config?.apiKey

    internal fun getApplicationContext(): Context? = applicationContext

    internal fun getCurrentConfig(): AdSdkConfig? = config
    
    internal fun isTestMode(): Boolean = config?.testMode ?: false

    // Internal logging helper (Example - a real SDK would have a dedicated LogUtil)
    internal fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        // Allow ERROR logs even before initialization for critical issues.
        val isLogLevelSufficientForPreInit = this::currentLogLevel.isInitialized &&
                level.ordinal <= this.currentLogLevel.ordinal &&
                this.currentLogLevel != LogLevel.NONE

        if (!initialized && level != LogLevel.ERROR && level != LogLevel.WARNING) { // Allow WARNING too
            // For pre-init logs, if we want to show them if setLogLevel was called first:
            if (!isLogLevelSufficientForPreInit) {
                return
            }
        }
        
        if (level.ordinal <= this.currentLogLevel.ordinal && this.currentLogLevel != LogLevel.NONE) {
            when (level) {
                LogLevel.ERROR -> Log.e(tag, message, throwable)
                LogLevel.WARNING -> Log.w(tag, message, throwable)
                LogLevel.INFO -> Log.i(tag, message, throwable)
                LogLevel.DEBUG -> Log.d(tag, message, throwable)
                LogLevel.VERBOSE -> Log.v(tag, message, throwable)
                LogLevel.NONE -> { /* No-op */ }
            }
        }
    }
}
