package com.example.adsdk

object AdErrorCodes {
    // General SDK Errors
    const val SDK_NOT_INITIALIZED = 1001
    const val INTERNAL_ERROR = 1006      // Generic internal SDK error
    const val INVALID_REQUEST = 1005     // Request parameters are invalid (e.g., missing ad unit ID)
    const val CONFIGURATION_ERROR = 1009 // Problem with SDK configuration

    // Network & Server Errors
    const val NETWORK_ERROR = 1002       // Problem with network connectivity
    const val SERVER_ERROR = 1003        // Error reported by the ad server
    const val TIMEOUT_ERROR = 1010       // Request to server timed out
    const val NO_FILL = 1004             // Ad server successfully processed request but had no ad to return
    
    // Ad Content & Display Errors
    const val CREATIVE_ERROR = 1008      // Error within the ad creative itself (e.g., MRAID error)
    const val AD_NOT_READY = 1007        // Ad is not loaded or ready to be shown (e.g. calling show() before loadAd() completes)
    const val UNSUPPORTED_AD_TYPE = 1011 // Ad type returned is not supported by the requested ad unit (e.g. Native for BannerAdView)
    const val RENDER_ERROR = 1012        // Error occurred while trying to render the ad

    // Permission & Device Feature Errors
    const val PERMISSION_DENIED = 1013   // Required permission not granted (e.g. for calendar, store picture)
    const val FEATURE_NOT_SUPPORTED = 1014 // Device does not support a feature required by the ad (e.g. specific MRAID feature)

    // Specific MRAID related errors (can be sub-codes of CREATIVE_ERROR)
    const val MRAID_INVALID_STATE_TRANSITION = 2001 // e.g. expand() called when not in default state
    const val MRAID_ACTION_NOT_SUPPORTED = 2002     // e.g. resize() for interstitial

    // Add more as needed
}
