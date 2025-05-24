package com.example.adsdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowMetrics // MRAID 3.0
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.Locale

class MraidWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private var placementType: String = MraidPlacementType.INLINE // Can be updated via loadCreative
) : WebView(context, attrs, defStyleAttr) {

    var mraidListener: MraidListener? = null
    private lateinit var mraidJsInterface: MraidJsInterface
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "MraidWebView"

    private var mraidScriptContent: String? = null // To store mraid.js content

    // For position and size calculations
    private val locationOnScreen = IntArray(2)
    private val windowVisibleDisplayFrame = Rect()

    private var isMraidJsInjected = false
    private var isPageLoaded = false

    // Cached location data for MRAID 3.0 getLocation()
    private var cachedLocation: MraidLocationData? = null


    init {
        setupWebViewSettings()
        setupClients()
        setBackgroundColor(Color.TRANSPARENT) // Important for overlays
        // Load mraid.js content (example, replace with actual asset loading)
        // In a real SDK, this would be loaded from assets or a string resource.
        mraidScriptContent = """
            // Basic mraid.js structure - This needs to be fully implemented
            (function() {
                var mraid = window.mraid = {};
                mraid.listeners = {}; // event: [callbacks]

                // --- MRAID Properties (Getters exposed to creative) ---
                mraid.getVersion = function() { return mraidBridge.getVersion(); };
                mraid.getState = function() { return mraidBridge.getState(); };
                mraid.getPlacementType = function() { return mraidBridge.getPlacementType(); };
                mraid.isViewable = function() { return mraidBridge.isViewable(); };
                
                // MRAID 2.0 Properties
                mraid.getCurrentPosition = function() { return JSON.parse(mraidBridge.getCurrentPosition()); };
                mraid.getDefaultPosition = function() { return JSON.parse(mraidBridge.getDefaultPosition()); };
                mraid.getMaxSize = function() { return JSON.parse(mraidBridge.getMaxSize()); };
                mraid.getScreenSize = function() { return JSON.parse(mraidBridge.getScreenSize()); };
                
                mraid.supports = function(feature) { return mraidBridge.supports(feature); };
                mraid.getExpandProperties = function() { /* TODO */ return { width: mraid.getMaxSize().width, height: mraid.getMaxSize().height, useCustomClose: false, isModal: true }; };
                mraid.getResizeProperties = function() { /* TODO */ return { width: 0, height: 0, customClosePosition: 'top-right', offsetX: 0, offsetY: 0, allowOffscreen: false }; };
                mraid.getOrientationProperties = function() { /* TODO */ return { allowOrientationChange: true, forceOrientation: 'none' }; };

                // MRAID 3.0 Properties
                mraid.getLocation = function() { 
                    var locString = mraidBridge.getLocation();
                    try { return JSON.parse(locString); } catch(e) { console.error('MRAID_getLocation: Error parsing location JSON', e); return null; }
                };
                mraid.getAudibility = function() { 
                    // This should return an object like { audible: true/false, reason: "..." }
                    // For now, matching the simplified string from MraidJsInterface
                    var audibilityStatus = mraidBridge.getAudibility(); // This returns "audible" or "not_audible"
                    return { audible: audibilityStatus === 'audible' }; // Simplified mapping
                };


                // --- MRAID Methods (Actions called by creative) ---
                mraid.open = function(url) { mraidBridge.open(url); };
                mraid.close = function() { mraidBridge.close(); };
                mraid.expand = function(url) { mraidBridge.expand(url); };
                mraid.resize = function(properties) { // properties is an object, not string here
                    var propertiesString = JSON.stringify(properties || mraid.getResizeProperties());
                    mraidBridge.resize(propertiesString); 
                };
                mraid.storePicture = function(url) { mraidBridge.storePicture(url); };
                mraid.createCalendarEvent = function(params) { mraidBridge.createCalendarEvent(JSON.stringify(params)); };
                mraid.playVideo = function(url) { mraidBridge.playVideo(url); };
                mraid.useCustomClose = function(useCustomClose) { mraidBridge.useCustomClose(useCustomClose); };
                
                mraid.setOrientationProperties = function(properties) { 
                    mraidBridge.setOrientationProperties(JSON.stringify(properties)); 
                };
                mraid.setExpandProperties = function(properties) { /* TODO: Store and use for expand */ }; // MRAID 2.0
                mraid.setResizeProperties = function(properties) { /* TODO: Store for resize */ Log.d(TAG, "JS: setResizeProperties"); }; // MRAID 2.0


                // --- MRAID Event Handling ---
                mraid.addEventListener = function(event, listener) {
                    if (!mraid.listeners[event]) {
                        mraid.listeners[event] = [];
                    }
                    // Ensure listener is not already added
                    if (mraid.listeners[event].indexOf(listener) === -1) {
                         mraid.listeners[event].push(listener);
                    }
                    // Inform native side if it needs to know (e.g. for viewability optimization)
                    // mraidBridge.addEventListener(event, String(listener)); // Not directly passing function string
                };

                mraid.removeEventListener = function(event, listener) {
                    if (mraid.listeners[event]) {
                        var listeners = mraid.listeners[event];
                        var index = listeners.indexOf(listener);
                        if (index !== -1) {
                            listeners.splice(index, 1);
                        }
                        // If no listeners left for an event, can notify native
                        // mraidBridge.removeEventListener(event, String(listener));
                    }
                };

                mraid.fireEvent = function(event, ...args) {
                    console.log("MRAID JS: Firing event " + event + " with args: " + JSON.stringify(args));
                    var eventListeners = mraid.listeners[event];
                    if (eventListeners) {
                        eventListeners.forEach(function(listener) {
                            try {
                                listener.apply(null, args);
                            } catch (e) {
                                console.error('MRAID: Error in event listener for ' + event + ':', e);
                                mraid.fireErrorEvent('Listener error for ' + event + ': ' + e.message, 'event:' + event);
                            }
                        });
                    }
                };

                // --- Native to JS Event Triggers ---
                // These are called by the native MraidWebView using evaluateJavascript
                mraid.fireReadyEvent = function() { mraid.fireEvent('ready'); };
                mraid.fireErrorEvent = function(message, action) { mraid.fireEvent('error', message, action); };
                mraid.fireStateChangeEvent = function(newState) { mraid.fireEvent('stateChange', newState); };
                mraid.fireViewableChangeEvent = function(isViewable) { mraid.fireEvent('viewableChange', isViewable); };
                mraid.fireSizeChangeEvent = function(width, height) { mraid.fireEvent('sizeChange', width, height); };
                // MRAID 3.0 events
                mraid.fireAudibleChangeEvent = function(isAudible) { 
                    // MRAID 3.0 spec says the argument is an object: { "audible": true/false }
                    // However, the simplified MraidJsInterface.getAudibility returns a string.
                    // For consistency, let's assume isAudible is a boolean here.
                    // The spec for the event argument is { "isAudible": boolean, "reasons": ["reason1", "reason2"] }
                    // This is a simplified version for now.
                    mraid.fireEvent('audioVolumeChange', { "currentVolumePercentage": isAudible ? 100 : 0, "muted": !isAudible }); // Placeholder event data
                }; 
                 mraid.fireExposureChangeEvent = function(exposedPercentage, visibleRectangle, occlusionRectangles) {
                    mraid.fireEvent('exposureChange', exposedPercentage, visibleRectangle, occlusionRectangles);
                };
                mraid.fireLocationChangeEvent = function(locationData) { // locationData is a JSON string from native
                     try {
                        var parsedLocation = JSON.parse(locationData);
                        mraid.fireEvent('locationChange', parsedLocation);
                     } catch(e) {
                        mraid.fireErrorEvent('Failed to parse location data for event: ' + e.message, 'locationChange');
                     }
                };


                // Inform native that mraid.js is loaded and ready
                if (typeof mraidBridge !== 'undefined' && typeof mraidBridge.mraidJsLoaded === 'function') {
                    mraidBridge.mraidJsLoaded();
                } else {
                     console.error("MRAID.js: mraidBridge not available or mraidJsLoaded not a function at init.");
                }
            })();
        """.trimIndent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        val settings = this.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false // For security, disallow file access by default
        settings.allowContentAccess = false
        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false // Important for auto-play video ads (if policy allows)

        // For debugging in Chrome DevTools (optional, remove for release)
        // WebView.setWebContentsDebuggingEnabled(true)
    }

    private fun setupClients() {
        this.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "onPageFinished: $url. MRAID JS Injected: $isMraidJsInjected")
                isPageLoaded = true
                if (isMraidJsInjected) { // If mraid.js was injected before page load (e.g. loadDataWithBaseURL)
                    // Handled by MraidJsInterface.mraidJsLoaded() callback now
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                val msg = "WebView Error: ${error?.description} for URL: ${request?.url}"
                Log.e(TAG, msg)
                // Don't fire MRAID error here directly, as it might not be an MRAID specific issue.
                // Creative itself should handle its resource loading errors.
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                if (url == null) return false

                // MRAID 'open' command is handled by MraidJsInterface.open()
                // This handles cases where the creative directly tries to navigate the top frame.
                // For MRAID ads, this should ideally not happen, as 'mraid.open' should be used.
                // If it does, we can treat it as an implicit open, or block it.
                Log.d(TAG, "shouldOverrideUrlLoading: $url")
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    // This behavior might need to be configurable.
                    // For an inline ad, changing its own content URL might be undesirable.
                    // For an interstitial, it's less of an issue.
                    // Let's delegate to mraidListener.onMraidOpen for now.
                    mraidListener?.onMraidOpen(this@MraidWebView, url)
                    return true // Indicate we've handled the URL loading
                }
                // Handle other schemes like tel:, sms:, etc.
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Could not handle URL: $url", e)
                    return false // Let WebView try to handle it or fail
                }
            }
        }

        this.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("MraidCreativeConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return true
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                Log.w(TAG, "JS Alert: $message (URL: $url)")
                // Suppress alerts by default in production, or handle them appropriately
                result?.confirm() // Or cancel() to dismiss
                return true
            }
            // TODO: Handle onJsConfirm, onJsPrompt if necessary

            // For HTML5 video fullscreen support (optional)
            // override fun onShowCustomView(view: View?, callback: CustomViewCallback?) { ... }
            // override fun onHideCustomView() { ... }
        }
    }

    fun loadCreative(htmlContent: String, placementType: String) {
        this.placementType = placementType
        this.mraidJsInterface = MraidJsInterface(this, placementType)
        addJavascriptInterface(mraidJsInterface, "mraidBridge")
        isMraidJsInjected = true // mraid.js is part of the injected content

        val fullHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />
                <style>
                    body { margin:0; padding:0; overflow:hidden; background-color:transparent; }
                    html { margin:0; padding:0; overflow:hidden; background-color:transparent; }
                    /* Ensure the ad content can fill the container */
                    #ad-container { width:100%; height:100%; display:flex; justify-content:center; align-items:center; }
                </style>
                <script type="text/javascript">
                    ${mraidScriptContent ?: "// MRAID SCRIPT NOT LOADED"}
                </script>
            </head>
            <body>
                <div id="ad-container">
                    $htmlContent
                </div>
            </body>
            </html>
        """.trimIndent()

        Log.d(TAG, "Loading creative with placement: $placementType")
        mraidJsInterface.updateState(MraidState.LOADING)
        // Using a base URL of "https://ads.example.com" to allow XHR requests from creative if needed.
        // Adjust if creative needs to access specific local resources (not recommended for security).
        loadDataWithBaseURL("https://ads.example.com/", fullHtml, "text/html", "UTF-8", null)
    }

    // Called from MraidJsInterface when mraid.js signals it's ready
    internal fun onMraidJsLoaded() {
         Log.d(TAG, "onMraidJsLoaded callback received. Page loaded: $isPageLoaded")
         handler.post { // Ensure on main thread
            // Both mraid.js and the HTML page need to be ready
            // isPageLoaded is set in onPageFinished
            if (isPageLoaded) {
                initializeMraidProperties()
                mraidJsInterface.updateState(MraidState.DEFAULT)
                fireReadyEvent()
                mraidListener?.onMraidReady(this)
                updateViewability(visibility == View.VISIBLE && hasWindowFocus()) // Initial viewability check
            } else {
                 Log.d(TAG, "MRAID.js loaded, but page is not yet finished. Waiting for onPageFinished.")
                 // onPageFinished will call initializeMraidProperties and fireReadyEvent if mraid.js is loaded
            }
        }
    }
    
    private fun initializeMraidProperties() {
        calculateScreenMetrics() // Ensure all metrics are up-to-date before JS gets them
    }


    // --- MRAID Event Firing Methods (Native to JS) ---
    fun fireReadyEvent() {
        Log.d(TAG, "Firing Ready Event")
        safeEvaluateJavascript("mraid.fireReadyEvent();")
    }

    fun fireErrorEvent(message: String, action: String) {
        Log.d(TAG, "Firing Error Event: $message for action $action")
        safeEvaluateJavascript("mraid.fireErrorEvent('${escapeJsString(message)}', '${escapeJsString(action)}');")
    }

    fun fireStateChangeEvent(newState: String) {
        Log.d(TAG, "Firing State Change Event: $newState")
        safeEvaluateJavascript("mraid.fireStateChangeEvent('${escapeJsString(newState)}');")
    }

    fun fireViewableChangeEvent(isViewable: Boolean) {
        Log.d(TAG, "Firing Viewable Change Event: $isViewable")
        safeEvaluateJavascript("mraid.fireViewableChangeEvent($isViewable);")
    }

    fun fireSizeChangeEvent(width: Int, height: Int) {
        Log.d(TAG, "Firing Size Change Event: w=$width, h=$height")
        // These are DP values usually, MRAID expects them in density-independent pixels
        val density = resources.displayMetrics.density
        val dpWidth = (width / density).toInt()
        val dpHeight = (height / density).toInt()
        safeEvaluateJavascript("mraid.fireSizeChangeEvent($dpWidth, $dpHeight);")
    }
    
    fun fireAudibleChangeEvent(isAudible: Boolean, reason: String = "") { // MRAID 3.0
        Log.d(TAG, "Firing Audible Change Event: $isAudible, Reason: $reason")
        // MRAID 3.0 spec: mraid.fireAudioVolumeChangeEvent({ currentVolumePercentage: number, muted: boolean, reasons: [string] });
        // Simplified for now based on mraid.js fireAudibleChangeEvent
        val volumePercent = if (isAudible) 100 else 0
        val muted = !isAudible
        val reasonsArray = if (reason.isNotEmpty()) "['${escapeJsString(reason)}']" else "[]"
        // TODO: This event is actually audioVolumeChange in MRAID 3.0
        // safeEvaluateJavascript("mraid.fireAudibleChangeEvent($isAudible);") // Old mraid.js event
        safeEvaluateJavascript("mraid.fireEvent('audioVolumeChange', { currentVolumePercentage: $volumePercent, muted: $muted, reasons: $reasonsArray });")
    }

    fun fireExposureChangeEvent(exposedPercentage: Float, visibleRectangle: Rect, occlusionRectangles: List<Rect>?) { // MRAID 3.0
        val visibleRectJson = rectToJson(visibleRectangle).toString()
        var occlusionRectsJson = "null"
        if (occlusionRectangles != null) {
            occlusionRectsJson = "[" + occlusionRectangles.joinToString(",") { rectToJson(it).toString() } + "]"
        }
        Log.d(TAG, "Firing Exposure Change: $exposedPercentage%, $visibleRectJson, $occlusionRectsJson")
        safeEvaluateJavascript("mraid.fireExposureChangeEvent($exposedPercentage, $visibleRectJson, $occlusionRectsJson);")
    }

    fun fireLocationChangeEvent(location: MraidLocationData) { // MRAID 3.0
        Log.d(TAG, "Firing Location Change Event: ${location.toJsonString()}")
        safeEvaluateJavascript("mraid.fireLocationChangeEvent('${location.toJsonString()}');")
    }


    private fun safeEvaluateJavascript(script: String) {
        if (isPageLoaded && isMraidJsInjected) { // Ensure page and mraid.js are ready
            handler.post { // Ensure on main thread
                evaluateJavascript(script, null)
            }
        } else {
            Log.w(TAG, "Skipping JS evaluation: Page or MRAID JS not ready. Script: $script")
        }
    }

    private fun escapeJsString(value: String?): String {
        return value?.replace("'", "\\'")?.replace("\"", "\\\"")?.replace("\n", "\\n") ?: ""
    }

    // --- MRAID State and Property Management ---
    fun updateViewability(viewable: Boolean) {
        if (::mraidJsInterface.isInitialized) { // Check if mraidJsInterface has been initialized
            mraidJsInterface.updateViewability(viewable)
        }
    }
    
    fun updateAudibility(audible: Boolean) { // MRAID 3.0
        if (::mraidJsInterface.isInitialized) {
            mraidJsInterface.updateAudibility(audible)
            // Note: MRAID 3.0 has specific reasons for audibility change.
            // This is a simplified version. The host app should provide reasons.
            fireAudibleChangeEvent(audible, if (audible) "volume_unmuted" else "volume_muted_by_user")
        }
    }

    fun updateCurrentLocation(location: MraidLocationData?) { // MRAID 3.0
        this.cachedLocation = location
        if (location != null && ::mraidJsInterface.isInitialized && mraidJsInterface.state != MraidState.LOADING) {
            fireLocationChangeEvent(location)
        }
    }

    internal fun getCachedLocation(): MraidLocationData? = cachedLocation


    // --- Position and Size Calculations ---
    // Call this when the view's layout or position might change, or on MRAID init
    fun calculateScreenMetrics() {
        if (!isAttachedToWindow || !::mraidJsInterface.isInitialized) return // Ensure view is attached and MRAID ready

        val activity = context as? Activity ?: return

        // Get screen size (physical screen)
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val screenWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        val screenHeight = (displayMetrics.heightPixels / displayMetrics.density).toInt()
        val screenSize = MraidScreenSize(screenWidth, screenHeight)

        // Get max size (size available to the ad, usually same as screen for interstitials)
        // For inline ads, this could be the full screen if allowed to expand, or a portion.
        // For simplicity, let's assume it's the screen size.
        val maxSize = MraidScreenSize(screenWidth, screenHeight)


        // Get default position (initial position and size of the ad container)
        this.getLocationOnScreen(locationOnScreen)
        val defaultX = (locationOnScreen[0] / displayMetrics.density).toInt()
        val defaultY = (locationOnScreen[1] / displayMetrics.density).toInt()
        val defaultWidth = (this.width / displayMetrics.density).toInt()
        val defaultHeight = (this.height / displayMetrics.density).toInt()
        val defaultPosition = MraidPosition(defaultX, defaultY, defaultWidth, defaultHeight)

        // Current position is initially same as default, updated on resize/expand
        // If the state is resized or expanded, currentPosition would differ.
        // For now, let's assume currentPosition is the same as defaultPosition unless state changes.
        val currentPosition = if (mraidJsInterface.state == MraidState.RESIZED || mraidJsInterface.state == MraidState.EXPANDED) {
            // In a real scenario, these would be the actual expanded/resized dimensions
            mraidJsInterface.currentPosition // Keep existing if already resized/expanded
        } else {
            defaultPosition
        }
        
        mraidJsInterface.updateScreenMetrics(currentPosition, defaultPosition, maxSize, screenSize)
        Log.d(TAG, "Screen Metrics Updated: Screen($screenWidth,$screenHeight), Max($screenWidth,$screenHeight), Default($defaultWidth,$defaultHeight at $defaultX,$defaultY)")
    }


    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) {
            calculateScreenMetrics()
             // If size changed, fire sizeChange event
            if (::mraidJsInterface.isInitialized && mraidJsInterface.state != MraidState.LOADING) {
                 // fireSizeChangeEvent expects pixel dimensions of the ad view
                 fireSizeChangeEvent(this.width, this.height)
            }
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) {
            updateViewability(visibility == View.VISIBLE && hasWindowFocus())
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        updateViewability(visibility == View.VISIBLE && hasWindowFocus)
    }

    // --- Utility and Cleanup ---
    fun destroyMraid() {
        // Important: remove JavascriptInterface to prevent calls after destroy
        removeJavascriptInterface("mraidBridge")
        // Standard WebView cleanup
        stopLoading()
        onPause() // Pauses JavaScript execution, timers, etc.
        loadUrl("about:blank") // Clear content
        clearHistory()
        clearCache(true)
        removeAllViews() // Remove any child views (e.g. custom close button if added directly)
        destroyDrawingCache()
        // Finally, call the WebView's destroy method
        super.destroy()
        Log.d(TAG, "MRAID WebView destroyed.")
    }

    private fun rectToJson(rect: Rect): JSONObject {
        val density = resources.displayMetrics.density
        return JSONObject().apply {
            put("x", (rect.left / density).toInt())
            put("y", (rect.top / density).toInt())
            put("width", (rect.width() / density).toInt())
            put("height", (rect.height() / density).toInt())
        }
    }
}
```
