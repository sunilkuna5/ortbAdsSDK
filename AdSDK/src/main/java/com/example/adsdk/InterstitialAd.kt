package com.example.adsdk

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import java.lang.ref.WeakReference

class InterstitialAd(
    private val context: Context,
    initialAdUnitId: String? = null // Optional adUnitId in constructor
) {

    var adUnitId: String? = initialAdUnitId
        private set
    var interstitialAdListener: InterstitialAdListener? = null
        private set

    private var mraidWebView: MraidWebView? = null
    private val adLoader: AdLoader = AdLoader()
    private var isLoaded: Boolean = false
    private var adDialog: Dialog? = null
    private var adResponsePayload: AdResponse? = null

    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "InterstitialAd"

    fun setAdUnitId(id: String): InterstitialAd {
        this.adUnitId = id
        return this
    }

    fun setInterstitialAdListener(listener: InterstitialAdListener): InterstitialAd {
        this.interstitialAdListener = listener
        return this
    }

    fun isLoaded(): Boolean = isLoaded

    fun loadAd() {
        Log.d(TAG, "loadAd() called. AdUnitId: $adUnitId")
        if (AdSdk.getApiKey() == null || AdSdk.getApplicationContext() == null) {
            Log.e(TAG, "AdSDK not initialized. Call AdSdk.initialize() first.")
            interstitialAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.SDK_NOT_INITIALIZED, "SDK not initialized"))
            return
        }

        val currentAdUnitId = adUnitId
        if (currentAdUnitId == null) {
            interstitialAdListener?.onAdFailedToLoad(
                AdError(AdErrorCodes.INVALID_REQUEST, "Ad unit ID is not set.")
            )
            return
        }

        isLoaded = false // Reset loaded state
        adResponsePayload = null // Clear previous response

        // AdRequest format for interstitial, AdLoader handles imp.instl=1 and banner w/h derivation
        val adRequest = AdRequest(
            adUnitId = currentAdUnitId,
            format = AdFormat.INTERSTITIAL
            // For interstitials, widthPx/heightPx are often derived by AdLoader to be full-screen
            // Or the ad server provides a full-screen creative.
        )

        adLoader.loadAd(adRequest, object : AdLoader.AdLoadListener {
            override fun onAdLoaded(adResponse: AdResponse) {
                handler.post {
                    if (adResponse.creativePayload.isBlank()) {
                        Log.e(TAG, "Ad server returned empty creative payload for interstitial.")
                        interstitialAdListener?.onAdFailedToLoad(
                            AdError(AdErrorCodes.NO_FILL, "Empty creative payload")
                        )
                        return@post
                    }
                    // For interstitials, we expect HTML or a format MRAID can handle.
                    // AdType.NATIVE is not directly handled by InterstitialAd in this basic SDK.
                     if (adResponse.adType == AdType.NATIVE) {
                         Log.e(TAG, "Received Native ad type for InterstitialAd. This is not supported here.")
                        val error = AdError(
                            AdErrorCodes.SERVER_ERROR,
                            "Unsupported ad type: NATIVE for InterstitialAd"
                        )
                        interstitialAdListener?.onAdFailedToLoad(error)
                        return@post
                    }

                    adResponsePayload = adResponse
                    isLoaded = true
                    Log.i(TAG, "Interstitial ad loaded successfully for AdUnit: $currentAdUnitId")
                    interstitialAdListener?.onAdLoaded(this@InterstitialAd)
                }
            }

            override fun onError(adError: AdError) {
                handler.post {
                    isLoaded = false
                    adResponsePayload = null
                    Log.e(TAG, "Interstitial ad failed to load for AdUnit: $currentAdUnitId. Error: ${adError.errorMessage}")
                    interstitialAdListener?.onAdFailedToLoad(adError)
                }
            }
        })
    }

    fun show() {
        Log.d(TAG, "show() called. AdUnitId: $adUnitId")
        if (!isLoaded || adResponsePayload == null) {
            val errorMsg = "Interstitial ad not ready to be shown. Load ad first."
            Log.e(TAG, errorMsg)
            // Notify listener of failure to show, if desired.
            // For now, InterstitialAdListener doesn't have onAdFailedToShow.
            // We can use onAdFailedToLoad with a specific error, or just log.
            // Let's consider this a "Not Loaded" error.
            interstitialAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.AD_NOT_READY, errorMsg))
            return
        }

        if (adDialog?.isShowing == true) {
            Log.w(TAG, "Interstitial ad is already showing.")
            return
        }
        
        if (context !is Activity) {
            Log.e(TAG, "Context is not an Activity. Cannot show interstitial dialog. Please provide an Activity context.")
            interstitialAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.INTERNAL_ERROR, "Context is not an Activity."))
            return
        }


        mraidWebView = MraidWebView(context, placementType = MraidPlacementType.INTERSTITIAL).apply {
            this.mraidListener = MraidInterstitialListener(this@InterstitialAd)
            // For interstitials, layout params are typically MATCH_PARENT for the WebView within the Dialog
            this.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Create Dialog
        adDialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen).apply {
            setOwnerActivity(context as Activity) // Important for dialog behavior
            setContentView(mraidWebView!!)
            setOnDismissListener {
                Log.d(TAG, "Interstitial Dialog Dismissed.")
                // This is called when dialog is dismissed via back button or programmatically
                // MRAID close() should also lead here.
                handleAdClosed()
            }
            setOnCancelListener { // Usually called on back press
                Log.d(TAG, "Interstitial Dialog Cancelled.")
                handleAdClosed()
            }
        }

        Log.d(TAG, "Loading creative into MRAID WebView for interstitial.")
        mraidWebView?.loadCreative(adResponsePayload!!.creativePayload, MraidPlacementType.INTERSTITIAL)
        
        try {
            adDialog?.show()
            Log.i(TAG, "Interstitial ad shown successfully for AdUnit: $adUnitId")
            // Impression is typically counted when the ad is shown for interstitial
            interstitialAdListener?.onAdImpression() 
            interstitialAdListener?.onAdShown() // Keep distinct onAdShown if it has other specific meaning
        } catch (e: android.view.WindowManager.BadTokenException) {
            Log.e(TAG, "Error showing interstitial dialog (BadTokenException): ${e.message}", e)
            interstitialAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.INTERNAL_ERROR, "Failed to show dialog (BadTokenException): ${e.message}"))
        } catch (e: RuntimeException) {
            Log.e(TAG, "Error showing interstitial dialog (RuntimeException): ${e.message}", e)
            interstitialAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.INTERNAL_ERROR, "Failed to show dialog (RuntimeException): ${e.message}"))
            cleanupAfterShow() // Clean up resources if show failed
            return
        }


        isLoaded = false // Interstitial ads are typically one-time use
        // adResponsePayload = null; // Keep payload for MRAIDWebView, clear it on close
    }

    private fun handleAdClosed() {
        if (adDialog != null) { // Ensure this is only called once
            interstitialAdListener?.onAdClosed()
            cleanupAfterShow()
        }
    }
    
    private fun cleanupAfterShow() {
        adDialog?.dismiss() // Dismiss again just in case it's still showing but listener was called
        adDialog = null
        mraidWebView?.destroyMraid()
        mraidWebView = null
        adResponsePayload = null // Clear the payload after it's done
        isLoaded = false // Ensure it's marked as not loaded
        Log.d(TAG, "Interstitial ad resources cleaned up.")
    }

    fun destroy() {
        Log.d(TAG, "destroy() called for InterstitialAd.")
        cleanupAfterShow()
        interstitialAdListener = null // Clear listener
    }

    // MRAID Listener Implementation for Interstitial
    private class MraidInterstitialListener(interstitialAd: InterstitialAd) : MraidListener {
        private val interstitialAdRef: WeakReference<InterstitialAd> = WeakReference(interstitialAd)
        private val TAG = "MraidInterstitial"

        override fun onMraidReady(mraidWebView: MraidWebView) {
            Log.d(TAG, "MRAID Ready for Interstitial AdUnit: ${interstitialAdRef.get()?.adUnitId}")
            // Ad is considered "ready to show" when AdLoader.onAdLoaded is called.
            // This event means the creative within the WebView is initialized.
            // No direct action needed here for InterstitialAd logic, as `show()` controls display.
        }

        override fun onMraidOpen(mraidWebView: MraidWebView, url: String) {
            Log.d(TAG, "MRAID Open: $url")
            interstitialAdRef.get()?.let { ad ->
                ad.interstitialAdListener?.onAdClicked()
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Necessary if context is not activity
                    ad.context.startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    Log.e(TAG, "Failed to open URL via MRAID (ActivityNotFound): $url", e)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to open URL via MRAID (SecurityException): $url", e)
                }
            }
        }

        override fun onMraidClose(mraidWebView: MraidWebView) {
            Log.d(TAG, "MRAID Close event received.")
            // This is the primary way an MRAID interstitial should be closed.
            interstitialAdRef.get()?.let { ad ->
                ad.adDialog?.dismiss() // This will trigger onDismissListener which calls handleAdClosed()
                // If ad.handleAdClosed() isn't called by dismiss, call it directly:
                // ad.handleAdClosed()
            }
        }

        override fun onMraidExpand(mraidWebView: MraidWebView, url: String?) {
            Log.w(TAG, "MRAID Expand called on an interstitial ad. This is unusual. Ignoring.")
            mraidWebView.fireErrorEvent("Expand is not applicable to interstitial ads.", "expand")
        }

        override fun onMraidResize(
            mraidWebView: MraidWebView, width: Int, height: Int, offsetX: Int, offsetY: Int,
            customClosePosition: String, allowOffscreen: Boolean
        ) {
            Log.w(TAG, "MRAID Resize called on an interstitial ad. This is unusual. Ignoring.")
            mraidWebView.fireErrorEvent("Resize is not applicable to interstitial ads.", "resize")
        }

        override fun onMraidPlayVideo(mraidWebView: MraidWebView, url: String) {
            Log.d(TAG, "MRAID PlayVideo: $url")
            // Similar to mraid.open, attempt to play video using an intent.
            // Some creatives might use this to play their main video content if not auto-playing.
            interstitialAdRef.get()?.let { ad ->
                 ad.interstitialAdListener?.onAdClicked() // Video playing can be considered a click-like interaction
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(Uri.parse(url), "video/*")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ad.context.startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    Log.e(TAG, "Failed to play video via MRAID (ActivityNotFound): $url", e)
                    mraidWebView.fireErrorEvent("Failed to play video (ActivityNotFound): ${e.message}", "playVideo")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to play video via MRAID (SecurityException): $url", e)
                    mraidWebView.fireErrorEvent("Failed to play video (SecurityException): ${e.message}", "playVideo")
                }
            }
        }

        override fun onMraidStorePicture(mraidWebView: MraidWebView, url: String) {
            Log.d(TAG, "MRAID StorePicture: $url")
            // Host app should handle permissions and download.
             interstitialAdRef.get()?.interstitialAdListener?.onAdClicked()
            mraidWebView.fireErrorEvent("Storing pictures is not implemented by the host.", "storePicture")
        }

        override fun onMraidCreateCalendarEvent(mraidWebView: MraidWebView, eventJson: String) {
            Log.d(TAG, "MRAID CreateCalendarEvent: $eventJson")
            // Host app should handle permissions and parsing.
            interstitialAdRef.get()?.interstitialAdListener?.onAdClicked()
            mraidWebView.fireErrorEvent("Creating calendar events is not implemented by the host.", "createCalendarEvent")
        }

        override fun onMraidSetUseCustomClose(mraidWebView: MraidWebView, useCustomClose: Boolean) {
            Log.d(TAG, "MRAID SetUseCustomClose: $useCustomClose")
            // Interstitial usually has its own close button or relies on MRAID creative's close.
            // If useCustomClose is false, the SDK might need to show a native close button on the dialog.
            // For simplicity, this SDK currently doesn't add its own native close button to the dialog.
            // It relies on the MRAID creative providing one, or the system back button.
        }

        override fun onMraidSetOrientationProperties(mraidWebView: MraidWebView, allowOrientationChange: Boolean, forceOrientation: String) {
            Log.d(TAG, "MRAID SetOrientationProperties: allow=$allowOrientationChange, force=$forceOrientation")
            // Host activity should handle orientation changes.
            interstitialAdRef.get()?.let { ad ->
                if (ad.context is Activity) {
                    // Example: (ad.context as Activity).requestedOrientation = ...
                    // This is complex and needs careful handling of activity lifecycle.
                    Log.i(TAG, "Orientation properties received, host activity should handle.")
                }
            }
        }
        
        override fun onMraidLocation(mraidWebView: MraidWebView, lat: Double, lon: Double, accuracy: Float, lastFix: Long) {
            Log.d(TAG, "MRAID action: onMraidLocation (lat=$lat, lon=$lon)")
            // Not typically initiated by interstitial creative this way. Location is usually passed in request.
        }

        override fun onMraidError(mraidWebView: MraidWebView, message: String, action: String) {
            Log.e(TAG, "MRAID Error in interstitial: $message for action: $action")
            // Depending on severity, might dismiss the ad
            // interstitialAdRef.get()?.adDialog?.dismiss()
            // interstitialAdRef.get()?.interstitialAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.CREATIVE_ERROR, "MRAID execution error: $message"))
        }
    }
}
```
