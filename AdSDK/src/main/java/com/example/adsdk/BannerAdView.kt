package com.example.adsdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.lang.ref.WeakReference

class BannerAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var adUnitId: String? = null
        private set
    var adSize: AdSize = AdSize.BANNER // Default size
        private set
    var bannerAdListener: BannerAdListener? = null
        private set

    private var mraidWebView: MraidWebView? = null
    private lateinit var adLoader: AdLoader

    private var refreshRateSeconds: Int = DEFAULT_REFRESH_RATE_SECONDS
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    private val TAG = "BannerAdView"

    companion object {
        private const val DEFAULT_REFRESH_RATE_SECONDS = 30
        private const val MILLISECONDS_IN_SECOND = 1000L
    }

    private var isLoaded = false // To track if an ad is currently loaded

    init {
        if (!isInEditMode) {
            adLoader = AdLoader() // Initialize AdLoader
        }
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.BannerAdView)
            adUnitId = typedArray.getString(R.styleable.BannerAdView_adUnitId)
            // Parse adSize string from XML and convert to AdSize object
            val adSizeString = typedArray.getString(R.styleable.BannerAdView_adSize)
            if (adSizeString != null) {
                try {
                    adSize = AdSize.fromString(adSizeString)
                } catch (e: IllegalArgumentException) {
                    val logMessage = "Invalid adSize specified in XML: $adSizeString. " +
                        "Defaulting to BANNER. ${e.message}"
                    AdSdk.log(LogLevel.WARNING, TAG, logMessage)
                    // Default to BANNER if string is invalid
                    adSize = AdSize.BANNER 
                }
            }
            refreshRateSeconds = typedArray.getInt(R.styleable.BannerAdView_refreshRate, DEFAULT_REFRESH_RATE_SECONDS)
            typedArray.recycle()
        }
    }

    fun setAdUnitId(id: String) {
        this.adUnitId = id
    }

    fun setAdSize(size: AdSize) {
        this.adSize = size
        // Update layout params if size changes while view is visible
        mraidWebView?.let {
            val lp = it.layoutParams as FrameLayout.LayoutParams
            lp.width = adSize.getWidthPx(context)
            lp.height = adSize.getHeightPx(context)
            it.layoutParams = lp
        }
        requestLayout()
    }

    fun setBannerAdListener(listener: BannerAdListener) {
        this.bannerAdListener = listener
    }

    fun setRefreshRate(seconds: Int) {
        this.refreshRateSeconds = seconds
        if (isLoaded) { // If an ad is already loaded, reschedule refresh
            cancelRefresh()
            if (seconds > 0) {
                scheduleRefresh()
            }
        }
    }

    fun loadAd() {
        Log.d(TAG, "loadAd() called. AdUnitId: $adUnitId, AdSize: ${adSize.widthDp}x${adSize.heightDp}")
        cancelRefresh() // Cancel any pending refresh before loading a new ad

        if (AdSdk.getApiKey() == null || AdSdk.getApplicationContext() == null) {
            Log.e(TAG, "AdSDK not initialized. Call AdSdk.initialize() first.")
            bannerAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.SDK_NOT_INITIALIZED, "SDK not initialized"))
            return
        }

        val currentAdUnitId = adUnitId
        if (currentAdUnitId == null) {
            bannerAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.INVALID_REQUEST, "Ad unit ID is not set."))
            return
        }

        // AdRequest now takes width and height in Px for OpenRTB
        val adRequest = AdRequest(
            adUnitId = currentAdUnitId,
            format = AdFormat.BANNER,
            // Pass size info to AdRequest, AdLoader will use this for OpenRTB imp.banner.w/h and format
            // AdLoader's buildOpenRTBRequest was already modified to use adRequest.widthPx and adRequest.heightPx
            // if they are provided in AdRequest. Let's ensure AdRequest can carry this.
            widthPx = adSize.getWidthPx(context),
            heightPx = adSize.getHeightPx(context)
        )

        // Make sure the BannerAdView itself has a size, otherwise ad might not be visible
        if (layoutParams.width == ViewGroup.LayoutParams.WRAP_CONTENT ||
            layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT
        ) {
            Log.w(
                TAG,
                "BannerAdView layout_width or layout_height is wrap_content. " +
                    "This might result in an ad not being displayed. " +
                    "Consider using a fixed size or match_parent with a fixed parent size."
            )
        }
        // If the BannerAdView's own size is 0, MRAID viewability might be an issue.
        // It's best if the AdSize also dictates the BannerAdView's measured size.
        // This can be done by setting minimumWidth/Height or by overriding onMeasure.
        // For now, we assume the layout provides enough space.


        adLoader.loadAd(adRequest, object : AdLoader.AdLoadListener {
            override fun onAdLoaded(adResponse: AdResponse) {
                handler.post { // Ensure UI operations are on the main thread
                    if (context == null || (context is Activity && context.isFinishing)) {
                        Log.w(TAG, "Context is null or activity is finishing, cannot load ad.")
                        return@post
                    }

                    if (adResponse.creativePayload.isBlank()) {
                        Log.e(TAG, "Ad server returned empty creative payload.")
                        bannerAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.NO_FILL, "Empty creative payload"))
                        return@post
                    }

                    // For banners, we expect HTML or a format MRAID can handle (like a URL to an MRAID ad)
                    // AdType.NATIVE is not directly handled by BannerAdView, it would need a NativeAdView.
                    if (adResponse.adType == AdType.NATIVE) {
                         Log.e(TAG, "Received Native ad type for BannerAdView. This is not supported here.")
                        bannerAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.SERVER_ERROR, "Unsupported ad type: NATIVE for BannerAdView"))
                        return@post
                    }


                    // Clean up previous WebView if any
                    mraidWebView?.destroyMraid()
                    this@BannerAdView.removeAllViews() // Remove old WebView from FrameLayout

                    mraidWebView = MraidWebView(context, placementType = MraidPlacementType.INLINE).apply {
                        this.mraidListener = MraidBannerListener(this@BannerAdView)
                        val layoutParams = FrameLayout.LayoutParams(
                            adSize.getWidthPx(context), // Use AdSize for consistency
                            adSize.getHeightPx(context)
                        ).apply {
                            gravity = Gravity.CENTER // Center the ad within the BannerAdView
                        }
                        this@BannerAdView.addView(this, layoutParams)
                        loadCreative(adResponse.creativePayload, MraidPlacementType.INLINE)
                    }
                    isLoaded = true
                    bannerAdListener?.onAdLoaded(this@BannerAdView)
                    scheduleRefresh()
                }
            }

            override fun onError(adError: AdError) {
                handler.post {
                    isLoaded = false
                    bannerAdListener?.onAdFailedToLoad(adError)
                    scheduleRefresh() // Schedule a retry even on error, with backoff this could be smarter
                }
            }
        })
    }

    private fun scheduleRefresh() {
        cancelRefresh()
        if (refreshRateSeconds > 0 && isAttachedToWindow) {
            Log.d(TAG, "Scheduling ad refresh in $refreshRateSeconds seconds.")
            refreshRunnable = Runnable {
                if (isAttachedToWindow && hasWindowFocus()) { // Only refresh if visible and focused
                    Log.d(TAG, "Refreshing ad...")
                    loadAd()
                } else {
                    Log.d(TAG, "Skipping refresh: View not attached, not focused, or refresh disabled.")
                    // Optionally, reschedule if skipped due to not being focused/visible
                    // This could lead to frequent checks, so handle with care. For now, it just skips.
                    // If skipped, the next refresh will be scheduled when an ad successfully loads again.
                }
            }.also {
                refreshHandler.postDelayed(it, refreshRateSeconds * MILLISECONDS_IN_SECOND)
            }
        }
    }

    private fun cancelRefresh() {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        refreshRunnable = null
        Log.d(TAG, "Ad refresh cancelled.")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "onAttachedToWindow")
        mraidWebView?.updateViewability(visibility == View.VISIBLE && hasWindowFocus())
        // If an ad was loaded and refresh was enabled, but runnable was cancelled due to detach,
        // we might want to reschedule it here. However, loadAd() handles scheduling on success.
        // If auto-refresh is desired as soon as view becomes visible and refresh was due,
        // some additional logic might be needed here. For now, refresh is driven by loadAd success.
        if (isLoaded && refreshRateSeconds > 0 && refreshRunnable == null) {
            // This means an ad is loaded, but refresh is not scheduled (e.g. after re-attaching)
            // Let's schedule it.
             scheduleRefresh()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "onDetachedFromWindow")
        cancelRefresh()
        // Don't destroy mraidWebView here directly, as the view might be re-attached.
        // Let destroy() method be explicit.
        // However, MRAID viewability should be false.
        mraidWebView?.updateViewability(false)
    }

    fun destroy() {
        Log.d(TAG, "destroy() called")
        cancelRefresh()
        mraidWebView?.destroyMraid()
        mraidWebView = null
        bannerAdListener = null // Remove listener reference
        removeAllViews()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) {
            mraidWebView?.updateViewability(visibility == View.VISIBLE && hasWindowFocus())
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        mraidWebView?.updateViewability(visibility == View.VISIBLE && hasWindowFocus)
         if (isAttachedToWindow) {
            if (hasWindowFocus && isLoaded && refreshRateSeconds > 0 && refreshRunnable == null) {
                // If view gained focus, an ad is loaded, refresh is enabled, but not scheduled
                // (e.g. was skipped because window didn't have focus before)
                scheduleRefresh()
            } else if (!hasWindowFocus) {
                // If window lost focus, we might want to temporarily pause refresh if a more aggressive
                // refresh-when-visible strategy is desired. For now, refresh runnable keeps running
                // but checks hasWindowFocus() before loading.
            }
        }
    }

    // MRAID Listener Implementation
    private class MraidBannerListener(bannerAdView: BannerAdView) : MraidListener {
        private val bannerViewRef: WeakReference<BannerAdView> = WeakReference(bannerAdView)
        private val TAG = "MraidBannerListener"

        override fun onMraidReady(mraidWebView: MraidWebView) {
            Log.d(TAG, "onMraidReady for AdUnit: ${bannerViewRef.get()?.adUnitId}")
            bannerViewRef.get()?.bannerAdListener?.onAdImpression()
        }

        override fun onMraidOpen(mraidWebView: MraidWebView, url: String) {
            Log.d(TAG, "onMraidOpen: $url")
            bannerViewRef.get()?.let { banner ->
                banner.bannerAdListener?.onAdClicked()
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    banner.context.startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    Log.e(TAG, "Failed to open URL (ActivityNotFound): $url", e)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to open URL (SecurityException): $url", e)
                }
            }
        }

        override fun onMraidClose(mraidWebView: MraidWebView) {
            Log.d(TAG, "onMraidClose")
            // For inline banners, "close" usually means the ad is hiding itself (if it was expanded)
            // or if its internal logic decides to close.
            // If the banner was expanded, this should bring it back to default size.
            // MraidWebView's MraidJsInterface handles state changes back to DEFAULT.
            // The BannerAdView itself doesn't usually get "closed" unless by its parent.
            bannerViewRef.get()?.bannerAdListener?.onAdClosed()
        }

        override fun onMraidExpand(mraidWebView: MraidWebView, url: String?) {
            Log.d(TAG, "onMraidExpand. URL: $url")
            // Banner expansion might require the host app to provide more space.
            // For a simple implementation, we might just log it or treat it as an "opened" event.
            // The MraidWebView itself will attempt to resize to fullscreen if not handled.
            // The host (BannerAdView) should ideally manage the layout change.
            // This is a complex MRAID feature. For now, we'll call onAdOpened for banner expansion.
            bannerViewRef.get()?.bannerAdListener?.onAdOpened()
            // TODO: Implement actual layout changes for expansion if needed.
            // This might involve communicating with the parent Activity/Fragment.
        }

        override fun onMraidResize(
            mraidWebView: MraidWebView,
            width: Int, height: Int, offsetX: Int, offsetY: Int,
            customClosePosition: String, allowOffscreen: Boolean
        ) {
            Log.d(TAG, "onMraidResize: ${width}x${height} at ${offsetX},${offsetY}")
            // The MraidWebView (or its parent FrameLayout in BannerAdView) needs to adjust its LayoutParams.
            // The width/height are in DP.
            bannerViewRef.get()?.let { banner ->
                val density = banner.context.resources.displayMetrics.density
                val widthPx = (width * density).toInt()
                val heightPx = (height * density).toInt()
                val offsetXpx = (offsetX * density).toInt()
                val offsetYpx = (offsetY * density).toInt()

                val lp = mraidWebView.layoutParams as FrameLayout.LayoutParams
                lp.width = widthPx
                lp.height = heightPx
                // TODO: Handle offsetX, offsetY (margins or absolute positioning if parent allows)
                // For FrameLayout, gravity and margins might be used.
                // This is simplified. True resize might need a new container or window.
                lp.leftMargin = offsetXpx 
                lp.topMargin = offsetYpx
                mraidWebView.layoutParams = lp
                
                // If resize causes the ad to effectively "take over" or act like a modal
                banner.bannerAdListener?.onAdOpened() // onAdOpened for banner resize/expansion
            }
        }

        override fun onMraidPlayVideo(mraidWebView: MraidWebView, url: String) {
            Log.d(TAG, "onMraidPlayVideo: $url")
            // Typically, this means the creative wants to play a video, often fullscreen.
            // The MRAID spec suggests the app should provide a video player.
            // For simplicity, we can try opening it with an intent, similar to mraid.open().
            bannerViewRef.get()?.let { banner ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.setDataAndType(Uri.parse(url), "video/*") // Hint it's a video
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    banner.context.startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    Log.e(TAG, "Failed to play video (ActivityNotFound): $url", e)
                    mraidWebView.fireErrorEvent("Failed to play video (ActivityNotFound): ${e.message}", "playVideo")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to play video (SecurityException): $url", e)
                    mraidWebView.fireErrorEvent("Failed to play video (SecurityException): ${e.message}", "playVideo")
                }
            }
        }

        override fun onMraidStorePicture(mraidWebView: MraidWebView, url: String) {
            Log.d(TAG, "onMraidStorePicture: $url")
            // Requires WRITE_EXTERNAL_STORAGE permission. SDK should not manage this directly.
            // Delegate to listener, host app should handle permission & download.
            bannerViewRef.get()?.bannerAdListener?.onAdClicked() // Consider this a click-like interaction
            // TODO: Add a specific listener callback for this, e.g. onStorePictureRequested(url)
            // For now, just log and fire error back to creative if not handled.
            mraidWebView.fireErrorEvent("Storing pictures is not implemented by the host.", "storePicture")
        }

        override fun onMraidCreateCalendarEvent(mraidWebView: MraidWebView, eventJson: String) {
            Log.d(TAG, "onMraidCreateCalendarEvent: $eventJson")
            // Requires WRITE_CALENDAR permission.
            // Delegate to listener.
            bannerViewRef.get()?.bannerAdListener?.onAdClicked() // Consider this a click-like interaction
            // TODO: Add a specific listener callback, e.g. onCreateCalendarEventRequested(eventJson)
            mraidWebView.fireErrorEvent("Creating calendar events is not implemented by the host.", "createCalendarEvent")
        }

        override fun onMraidSetUseCustomClose(mraidWebView: MraidWebView, useCustomClose: Boolean) {
            Log.d(TAG, "onMraidSetUseCustomClose: $useCustomClose")
            // BannerAdView should ideally show/hide its own close button if useCustomClose is false.
            // MRAID spec: if false, native UI provides close. If true, creative provides close.
            // This is more for expanded/interstitial ads. For inline banners, it's less common.
        }

        override fun onMraidSetOrientationProperties(mraidWebView: MraidWebView, allowOrientationChange: Boolean, forceOrientation: String) {
            Log.d(TAG, "onMraidSetOrientationProperties: allow=$allowOrientationChange, force=$forceOrientation")
            // Host activity should handle orientation changes.
            // BannerAdView typically doesn't control this itself.
        }
        
        override fun onMraidLocation(mraidWebView: MraidWebView, lat: Double, lon: Double, accuracy: Float, lastFix: Long) {
            Log.d(TAG, "MRAID action: onMraidLocation (lat=$lat, lon=$lon)")
            // This is the MRAID 3.0 mraid.getLocation() result if SDK were to push it.
            // The MraidJsInterface currently has a placeholder.
            // The listener callback here is more for when creative *requests* location via an event,
            // or if native wants to inform that location *is available*.
            // The MraidWebView.updateCurrentLocation() is the way to push location to the creative.
        }

        override fun onMraidError(mraidWebView: MraidWebView, message: String, action: String) {
            Log.e(TAG, "MRAID Error: $message for action: $action")
            // bannerViewRef.get()?.bannerAdListener?.onAdFailedToLoad( AdError from this? )
            // This is an error from within the MRAID creative's execution, not an ad load error.
        }
    }
}

// Need to define R.styleable.BannerAdView for XML attributes if used.
// This would typically be in res/values/attrs.xml
// <declare-styleable name="BannerAdView">
//     <attr name="adUnitId" format="string" />
//     <attr name="adSize" format="string" /> <!-- Or custom enum for sizes -->
//     <attr name="refreshRate" format="integer" />
// </declare-styleable>
// For now, these are not strictly necessary as properties are set programmatically.
// The R.styleable access will compile error without actual attrs.xml.
// I will remove the XML attribute parsing from init{} for now to avoid this dependency.
```
