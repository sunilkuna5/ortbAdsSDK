package com.example.adsdk

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.util.Locale

class MraidJsInterface(
    private val mraidWebView: MraidWebView,
    private var placementType: String // "inline" or "interstitial"
) {
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "MraidJsInterface"

    // --- MRAID Properties (Managed Natively, Exposed to JS) ---
    var state: String = MraidState.LOADING // loading, default, expanded, resized, hidden
        private set(value) {
            field = value
            mraidWebView.fireStateChangeEvent(value)
        }

    var isViewable: Boolean = false
        private set(value) {
            if (field != value) {
                field = value
                mraidWebView.fireViewableChangeEvent(value)
            }
        }
    
    // These positions are relative to the screen
    var currentPosition: MraidPosition = MraidPosition(0, 0, 0, 0)
        private set
    var defaultPosition: MraidPosition = MraidPosition(0, 0, 0, 0)
        private set
    var maxSize: MraidScreenSize = MraidScreenSize(0, 0)
        private set
    var screenSize: MraidScreenSize = MraidScreenSize(0, 0)
        private set

    // MRAID 3.0 properties
    var isAudible: Boolean = true // Assume audible by default, host app should update this
        private set(value) {
            if (field != value) {
                field = value
                mraidWebView.fireAudibleChangeEvent(value)
            }
        }
    
    val version: String = "3.0"

    // --- Methods for MraidWebView to update properties ---
    fun updateState(newState: String) {
        if (state != newState) {
            Log.d(TAG, "State changing from $state to $newState")
            state = newState
        }
    }

    fun updateViewability(viewable: Boolean) {
        isViewable = viewable
    }

    fun updateAudibility(audible: Boolean) {
        isAudible = audible
    }
    
    fun updateScreenMetrics(
        currentPos: MraidPosition,
        defaultPos: MraidPosition,
        maxSz: MraidScreenSize,
        screenSz: MraidScreenSize
    ) {
        var changed = false
        if (currentPosition != currentPos) {
            currentPosition = currentPos
            changed = true
        }
        if (defaultPosition != defaultPos) {
            defaultPosition = defaultPos
            changed = true
        }
         if (maxSize != maxSz) {
            maxSize = maxSz
             changed = true
        }
        if (screenSize != screenSz) {
            screenSize = screenSz
            changed = true
        }

        if (changed) {
            // This will trigger sizeChangeEvent if currentPosition width/height changed
            // and also ensures JS side can get up-to-date values for all metrics.
            // For MRAID, sizeChangeEvent is about the ad creative's current size.
            mraidWebView.fireSizeChangeEvent(currentPosition.width, currentPosition.height)
        }
    }


    // --- MRAID Commands (Called from JS) ---

    @JavascriptInterface
    fun open(url: String) {
        Log.d(TAG, "JS_CMD: open($url)")
        handler.post { mraidWebView.mraidListener?.onMraidOpen(mraidWebView, url) }
    }

    @JavascriptInterface
    fun close() {
        Log.d(TAG, "JS_CMD: close()")
        handler.post {
            when (state) {
                MraidState.LOADING -> { /* No specific action, but log */ Log.d(TAG, "close() called in LOADING state.")}
                MraidState.DEFAULT -> {
                    // Hide the ad container
                    updateState(MraidState.HIDDEN)
                    mraidWebView.mraidListener?.onMraidClose(mraidWebView)
                }
                MraidState.EXPANDED, MraidState.RESIZED -> {
                    // Transition back to default state
                    updateState(MraidState.DEFAULT)
                    mraidWebView.mraidListener?.onMraidClose(mraidWebView) // Listener handles UI changes
                }
                MraidState.HIDDEN -> { /* Already hidden */ Log.d(TAG, "close() called in HIDDEN state.")}
            }
        }
    }

    @JavascriptInterface
    fun expand(url: String?) {
        Log.d(TAG, "JS_CMD: expand(${url ?: ""})")
        if (state != MraidState.DEFAULT && state != MraidState.RESIZED) {
            mraidWebView.fireErrorEvent("Cannot expand from state: $state", "expand")
            return
        }
        if (placementType == "interstitial") { // Interstitials cannot call expand
             mraidWebView.fireErrorEvent("Expand is not allowed for interstitial ads.", "expand")
            return
        }
        handler.post {
            updateState(MraidState.EXPANDED)
            mraidWebView.mraidListener?.onMraidExpand(mraidWebView, url)
        }
    }

    @JavascriptInterface
    fun resize(propertiesJson: String) {
        Log.d(TAG, "JS_CMD: resize($propertiesJson)")
        if (state != MraidState.DEFAULT && state != MraidState.RESIZED) {
            mraidWebView.fireErrorEvent("Cannot resize from state: $state", "resize")
            return
        }
        if (placementType == "interstitial") {
             mraidWebView.fireErrorEvent("Resize is not allowed for interstitial ads.", "resize")
            return
        }

        try {
            val props = JSONObject(propertiesJson)
            val width = props.getInt("width")
            val height = props.getInt("height")
            val offsetX = props.optInt("offsetX", 0)
            val offsetY = props.optInt("offsetY", 0)
            val customClosePosition = props.optString("customClosePosition", "top-right")
            val allowOffscreen = props.optBoolean("allowOffscreen", false)

            // TODO: Add validation for size against maxSize, screen bounds (if not allowOffscreen)

            handler.post {
                updateState(MraidState.RESIZED)
                mraidWebView.mraidListener?.onMraidResize(
                    mraidWebView,
                    width,
                    height,
                    offsetX,
                    offsetY,
                    customClosePosition,
                    allowOffscreen
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse resize properties", e)
            mraidWebView.fireErrorEvent("Invalid resize properties: ${e.message}", "resize")
        }
    }

    @JavascriptInterface
    fun storePicture(url: String) {
        Log.d(TAG, "JS_CMD: storePicture($url)")
        // Permission check for WRITE_EXTERNAL_STORAGE should happen before calling this from MraidWebView or listener
        handler.post { mraidWebView.mraidListener?.onMraidStorePicture(mraidWebView, url) }
    }

    @JavascriptInterface
    fun createCalendarEvent(eventJson: String) {
        Log.d(TAG, "JS_CMD: createCalendarEvent($eventJson)")
        // Permission check for WRITE_CALENDAR
        handler.post { mraidWebView.mraidListener?.onMraidCreateCalendarEvent(mraidWebView, eventJson) }
    }

    @JavascriptInterface
    fun playVideo(url: String) {
        Log.d(TAG, "JS_CMD: playVideo($url)")
        handler.post { mraidWebView.mraidListener?.onMraidPlayVideo(mraidWebView, url) }
    }

    @JavascriptInterface
    fun useCustomClose(useCustomClose: Boolean) {
        Log.d(TAG, "JS_CMD: useCustomClose($useCustomClose)")
        handler.post { mraidWebView.mraidListener?.onMraidSetUseCustomClose(mraidWebView, useCustomClose) }
    }

    @JavascriptInterface
    fun setOrientationProperties(propertiesJson: String) {
        Log.d(TAG, "JS_CMD: setOrientationProperties($propertiesJson)")
        try {
            val props = JSONObject(propertiesJson)
            val allowOrientationChange = props.getBoolean("allowOrientationChange")
            val forceOrientation = props.getString("forceOrientation") // "portrait", "landscape", "none"
            handler.post {
                mraidWebView.mraidListener?.onMraidSetOrientationProperties(
                    mraidWebView,
                    allowOrientationChange,
                    forceOrientation
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse orientation properties", e)
            mraidWebView.fireErrorEvent("Invalid orientation properties: ${e.message}", "setOrientationProperties")
        }
    }
    
    @JavascriptInterface
    fun getLocation(): String {
        Log.d(TAG, "JS_CMD: getLocation()")
        // Permission check for ACCESS_FINE_LOCATION should be handled by the listener/host
        // This method is synchronous in mraid.js, but actual location fetching is async.
        // The mraid.js will have to handle the async nature or the SDK will need to cache the location.
        // For now, let's assume the listener provides it or we return a default "not available".
        // A better way is for mraid.js to call this, and native fires an event with location data.
        // However, MRAID spec implies getLocation is synchronous.
        // Let's return a cached value or error if not available.
        
        val locationData = mraidWebView.mraidListener?.onMraidLocation(mraidWebView, 0.0,0.0,0.0f,0L) // This call is a bit off, listener should provide data
        
        // This part is tricky. MRAID spec for getLocation is synchronous.
        // Real location is async. Typically, SDK would cache last known location.
        // Or, this method in mraid.js would be a wrapper that triggers an async fetch,
        // and the actual value is provided via an event or callback.
        // For now, returning a fixed value or an error state.
        // The `mraidWebView.mraidListener?.onMraidLocation` is more of a trigger to *request* location updates.
        // The actual value should be stored in MraidJsInterface or MraidWebView, updated by the listener.

        // Let's assume there's a cached location in mraidWebView or here.
        val loc = mraidWebView.getCachedLocation() // Needs to be implemented
        if (loc != null) {
            return loc.toJsonString()
        }

        mraidWebView.fireErrorEvent("Location not available or permission denied.", "getLocation")
        return "{ \"lat\": -1, \"lon\": -1, \"accuracy\": -1, \"lastfix\": 0, \"ipservice\": false }" // Indicate error/unavailable
    }


    // --- Event Listener Registration (JS to Native, but handled in mraid.js mostly) ---

    @JavascriptInterface
    fun addEventListener(event: String, listener: String) {
        Log.d(TAG, "JS_CMD: addEventListener($event, $listener)")
        // Actual listener storage and invocation is handled by mraid.js
        // Native might want to know if certain listeners are active to optimize (e.g. viewability)
        // For now, this is a no-op on the native side, as mraid.js handles it.
    }

    @JavascriptInterface
    fun removeEventListener(event: String, listener: String) {
        Log.d(TAG, "JS_CMD: removeEventListener($event, $listener)")
        // Handled by mraid.js
    }

    // --- Methods exposed for JS to query properties (called by mraid.js) ---
    @JavascriptInterface
    fun getPlacementType(): String = placementType

    @JavascriptInterface
    fun getState(): String = state
    
    @JavascriptInterface
    fun isViewable(): Boolean = isViewable

    @JavascriptInterface
    fun getVersion(): String = version

    @JavascriptInterface
    fun getCurrentPosition(): String = currentPosition.toJson().toString()

    @JavascriptInterface
    fun getDefaultPosition(): String = defaultPosition.toJson().toString()
    
    @JavascriptInterface
    fun getMaxSize(): String = maxSize.toJson().toString()

    @JavascriptInterface
    fun getScreenSize(): String = screenSize.toJson().toString()

    @JavascriptInterface
    fun supports(feature: String): Boolean {
        return when (feature.lowercase(Locale.ROOT)) {
            "sms" -> true // Assuming support, needs Manifest declaration and handling
            "tel" -> true // Assuming support, needs Manifest declaration and handling
            "calendar" -> true // Assuming support, needs Manifest declaration and handling
            "storepicture" -> true // Assuming support, needs Manifest declaration and handling
            "inlinevideo" -> true // Assuming support (HTML5 video)
            // MRAID 3.0 features
            "vpaid" -> false // VPAID support is complex, assume false for now
            "location" -> true // If permission is granted
            else -> false
        }
    }

    @JavascriptInterface
    fun getAudibility(): String { // MRAID 3.0
        return if (isAudible) MraidAudibility.AUDIBLE else MraidAudibility.NOT_AUDIBLE
        // Could also be MraidAudibility.UNKNOWN if not determined
    }
    
    // Called by mraid.js when it's fully loaded and ready to process commands from native
    @JavascriptInterface
    fun mraidJsLoaded() {
        Log.d(TAG, "MRAID.js has reported it is loaded.")
        handler.post {
            mraidWebView.onMraidJsLoaded()
        }
    }
}

object MraidState {
    const val LOADING = "loading"
    const val DEFAULT = "default"
    const val EXPANDED = "expanded"
    const val RESIZED = "resized"
    const val HIDDEN = "hidden"
}

object MraidPlacementType {
    const val INLINE = "inline"
    const val INTERSTITIAL = "interstitial"
}

object MraidAudibility { // MRAID 3.0
    const val AUDIBLE = "audible"
    const val NOT_AUDIBLE = "not_audible"
    const val UNKNOWN = "unknown"
}


data class MraidPosition(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("x", x)
            put("y", y)
            put("width", width)
            put("height", height)
        }
    }
}

data class MraidScreenSize(val width: Int, val height: Int) {
     fun toJson(): JSONObject {
        return JSONObject().apply {
            put("width", width)
            put("height", height)
        }
    }
}

// For getLocation() MRAID 3.0 - if we were to cache it
data class MraidLocationData(
    val lat: Double,
    val lon: Double,
    val accuracy: Float, // in meters
    val lastFix: Long, // timestamp in ms
    val ipservice: Boolean = false // true if location was derived from IP
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("lat", lat)
            put("lon", lon)
            put("type", if (ipservice) 2 else 1) // 1 for GPS, 2 for IP, 3 for user provided (MRAID uses 'type' for source)
            put("accuracy", accuracy.toDouble()) // MRAID spec expects 'accuracy' (double)
            put("lastfix", lastFix / 1000) // MRAID spec wants seconds
            put("ipservice", ipservice) // Added for completeness, though MRAID spec for getLocation doesn't list it
        }.toString()
    }
}
```
