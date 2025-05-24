package com.example.adsdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.lang.ref.WeakReference

class NativeAd(
    private val context: Context,
    var adUnitId: String?
) {
    var nativeAdListener: NativeAdListener? = null
        private set

    private val adLoader: AdLoader = AdLoader()
    private var isLoaded: Boolean = false
    private var hasTrackedImpression: Boolean = false
    private var hasTrackedClick: Boolean = false // To ensure click is tracked only once

    // Parsed and mapped ad assets
    var nativeAdAssets: NativeAdAssets? = null
        private set

    // Core ad properties
    var landingUrl: String? = null
        private set
    private var impressionTrackers: List<String> = emptyList()
    private var clickTrackers: List<String> = emptyList()
    private var jsTracker: String? = null
    private var mraidWebViewForJsTracker: MraidWebView? = null

    private val httpClient = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "NativeAd"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val openRTBNativeMarkupAdapter = moshi.adapter(OpenRTBNativeMarkup::class.java)

    private var registeredAdView: WeakReference<View>? = null
    private var clickableViewsRefs: List<WeakReference<View>> = emptyList()

    fun setNativeAdListener(listener: NativeAdListener): NativeAd {
        this.nativeAdListener = listener
        return this
    }

    fun isLoaded(): Boolean = isLoaded

    fun loadAd() {
        Log.d(TAG, "loadAd() called. AdUnitId: $adUnitId")
        if (AdSdk.getApiKey() == null || AdSdk.getApplicationContext() == null) {
            Log.e(TAG, "AdSDK not initialized. Call AdSdk.initialize() first.")
            nativeAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.SDK_NOT_INITIALIZED, "SDK not initialized"))
            return
        }

        val currentAdUnitId = adUnitId
        if (currentAdUnitId == null) {
            nativeAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.INVALID_REQUEST, "Ad unit ID is not set."))
            return
        }

        resetAdState()

        val adRequest = AdRequest(
            adUnitId = currentAdUnitId,
            format = AdFormat.NATIVE
            // For Native ads, AdLoader's buildOpenRTBRequest creates the Native.request object
            // specifying desired assets (title, image, data etc.)
        )

        adLoader.loadAd(adRequest, object : AdLoader.AdLoadListener {
            override fun onAdLoaded(adResponse: AdResponse) {
                handler.post {
                    if (adResponse.adType != AdType.NATIVE || adResponse.creativePayload.isBlank()) {
                        Log.e(
                            TAG,
                            "Invalid ad response for Native Ad. Type: ${adResponse.adType}, " +
                                "Payload empty: ${adResponse.creativePayload.isBlank()}"
                        )
                        nativeAdListener?.onAdFailedToLoad(
                            AdError(AdErrorCodes.SERVER_ERROR, "Invalid response for Native Ad")
                        )
                        return@post
                    }

                    try {
                        val parsedNativeMarkup = openRTBNativeMarkupAdapter.fromJson(adResponse.creativePayload)
                        if (parsedNativeMarkup == null) {
                            nativeAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.SERVER_ERROR, "Failed to parse Native Ad markup"))
                            return@post
                        }

                        mapOpenRTBToNativeAdAssets(parsedNativeMarkup)
                        landingUrl = parsedNativeMarkup.link.url
                        impressionTrackers = parsedNativeMarkup.imptrackers ?: emptyList()
                        clickTrackers = parsedNativeMarkup.link.clicktrackers ?: emptyList()
                        jsTracker = parsedNativeMarkup.jstracker

                        if (nativeAdAssets == null || landingUrl == null) {
                             nativeAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.SERVER_ERROR, "Essential native ad components missing after parsing."))
                            return@post
                        }
                        
                        isLoaded = true
                        Log.i(TAG, "Native ad loaded successfully for AdUnit: $currentAdUnitId")
                        nativeAdListener?.onAdLoaded(this@NativeAd)

                        if (!jsTracker.isNullOrBlank()) {
                            prepareJsTracker()
                        }

                    } catch (e: com.squareup.moshi.JsonDataException) {
                        Log.e(TAG, "Error parsing native ad JSON response: ${e.message}", e)
                        nativeAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.SERVER_ERROR, "Invalid native ad data: ${e.message}"))
                    } catch (e: RuntimeException) { // Catch other unexpected errors during processing
                        Log.e(TAG, "Unexpected error processing native ad response: ${e.message}", e)
                        nativeAdListener?.onAdFailedToLoad(AdError(AdErrorCodes.SERVER_ERROR, "Unexpected error processing ad: ${e.message}"))
                    }
                }
            }

            override fun onError(adError: AdError) {
                handler.post {
                    nativeAdListener?.onAdFailedToLoad(adError)
                }
            }
        })
    }

    private fun mapOpenRTBToNativeAdAssets(markup: OpenRTBNativeMarkup) {
        val mappedAssets = mutableMapOf<Int, NativeAsset>()
        var titleAsset: NativeAsset.Title? = null
        var mainImageAsset: NativeAsset.Image? = null
        var iconImageAsset: NativeAsset.Icon? = null
        var descriptionAsset: NativeAsset.Data? = null
        var ctaAsset: NativeAsset.Data? = null
        var sponsoredByAsset: NativeAsset.Data? = null
        var ratingAsset: NativeAsset.Data? = null
        var videoAsset: NativeAsset.Video? = null


        markup.assets.forEach { asset ->
            val required = asset.required == 1
            if (asset.title != null) {
                val currentTitle = NativeAsset.Title(asset.title.text, asset.id, required)
                mappedAssets[asset.id] = currentTitle
                // Prioritize official ID or first found
                if (asset.id == NativeAssetId.TITLE_ID || titleAsset == null) {
                    titleAsset = currentTitle
                }
            } else if (asset.img != null) {
                val imageType = OpenRTBNativeImageAssetType.fromInt(asset.img.type ?: 0)
                when (imageType) {
                    OpenRTBNativeImageAssetType.MAIN -> {
                        val currentImage = NativeAsset.Image(
                            asset.img.url, asset.img.w, asset.img.h, asset.id, required
                        )
                        mappedAssets[asset.id] = currentImage
                        if (asset.id == NativeAssetId.MAIN_IMAGE_ID || mainImageAsset == null) {
                            mainImageAsset = currentImage
                        }
                    }
                    OpenRTBNativeImageAssetType.ICON -> {
                         val currentIcon = NativeAsset.Icon(
                             asset.img.url, asset.img.w, asset.img.h, asset.id, required
                         )
                        mappedAssets[asset.id] = currentIcon
                        if (asset.id == NativeAssetId.ICON_ID || iconImageAsset == null) {
                            iconImageAsset = currentIcon
                        }
                    }
                    else -> { // Generic image if type not specified or unknown
                        val currentImage = NativeAsset.Image(
                            asset.img.url, asset.img.w, asset.img.h, asset.id, required
                        )
                        mappedAssets[asset.id] = currentImage
                        // Could assign to mainImage if it's still null
                        if (mainImageAsset == null && imageType == null) {
                            mainImageAsset = currentImage
                        }
                    }
                }
            } else if (asset.data != null) {
                val dataType = OpenRTBNativeDataAssetType.fromInt(asset.data.type ?: 0)
                if (dataType != null) {
                     val currentData = NativeAsset.Data(asset.data.value, dataType, asset.id, required)
                     mappedAssets[asset.id] = currentData
                    when (dataType) {
                        OpenRTBNativeDataAssetType.DESC ->
                            if (asset.id == NativeAssetId.DESCRIPTION_ID || descriptionAsset == null) {
                                descriptionAsset = currentData
                            }
                        OpenRTBNativeDataAssetType.CTA ->
                            if (asset.id == NativeAssetId.CTA_TEXT_ID || ctaAsset == null) {
                                ctaAsset = currentData
                            }
                        OpenRTBNativeDataAssetType.SPONSORED ->
                            if (asset.id == NativeAssetId.SPONSORED_BY_ID || sponsoredByAsset == null) {
                                sponsoredByAsset = currentData
                            }
                        OpenRTBNativeDataAssetType.RATING ->
                            if (asset.id == NativeAssetId.RATING_ID || ratingAsset == null) {
                                ratingAsset = currentData
                            }
                        else -> { /* Store in allAssets, no specific field */ }
                    }
                }
            } else if (asset.video != null) {
                val currentVideo = NativeAsset.Video(asset.video.vastTag, asset.id, required)
                mappedAssets[asset.id] = currentVideo
                if (videoAsset == null) videoAsset = currentVideo // Take the first video asset
            }
        }
        this.nativeAdAssets = NativeAdAssets(
            title = titleAsset,
            description = descriptionAsset,
            callToAction = ctaAsset,
            mainImage = mainImageAsset,
            iconImage = iconImageAsset,
            sponsoredBy = sponsoredByAsset,
            rating = ratingAsset,
            video = videoAsset,
            allAssets = mappedAssets
        )
    }

    private fun prepareJsTracker() {
        if (jsTracker.isNullOrBlank() || context == null) return
        Log.d(TAG, "Preparing MRAID WebView for JS Tracker.")
        mraidWebViewForJsTracker = MraidWebView(context, placementType = MraidPlacementType.INTERSTITIAL) // Placement type might not matter much
        mraidWebViewForJsTracker?.mraidListener = object: MraidListener { // Basic listener
            override fun onMraidReady(mraidWebView: MraidWebView) { Log.d(TAG, "JS Tracker MRAID Ready"); }
            override fun onMraidOpen(mraidWebView: MraidWebView, url: String) {}
            override fun onMraidClose(mraidWebView: MraidWebView) {}
            override fun onMraidExpand(mraidWebView: MraidWebView, url: String?) {}
            override fun onMraidResize(mraidWebView: MraidWebView, width: Int, height: Int, offsetX: Int, offsetY: Int, customClosePosition: String, allowOffscreen: Boolean) {}
            override fun onMraidPlayVideo(mraidWebView: MraidWebView, url: String) {}
            override fun onMraidStorePicture(mraidWebView: MraidWebView, url: String) {}
            override fun onMraidCreateCalendarEvent(mraidWebView: MraidWebView, eventJson: String) {}
            override fun onMraidSetUseCustomClose(mraidWebView: MraidWebView, useCustomClose: Boolean) {}
            override fun onMraidSetOrientationProperties(mraidWebView: MraidWebView, allowOrientationChange: Boolean, forceOrientation: String) {}
            override fun onMraidLocation(mraidWebView: MraidWebView, lat: Double, lon: Double, accuracy: Float, lastFix: Long) {}
            override fun onMraidError(mraidWebView: MraidWebView, message: String, action: String) { Log.e(TAG, "JS Tracker MRAID Error: $message, Action: $action");}
        }
        // The JS tracker content is often just a script tag or JS snippet.
        // MraidWebView.loadCreative expects full HTML.
        val htmlToLoad = """
            <!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' /></head>
            <body><script>$jsTracker</script></body></html>
        """.trimIndent()
        mraidWebViewForJsTracker?.loadCreative(htmlToLoad, MraidPlacementType.INTERSTITIAL)
        // The MRAID WebView needs to be attached to the window to get viewability events.
        // This is complex for a non-visible tracker. Some JS trackers might not need MRAID viewability
        // and just execute. For MRAID compliance, it would need to be part of the view hierarchy.
        // For now, we load it; actual viewability signal depends on MraidWebView's capabilities
        // and if the JS tracker itself uses MRAID viewability APIs.
    }


    // --- Asset Getters ---
    fun getAssets(): NativeAdAssets? = nativeAdAssets
    fun getLandingUrl(): String? = landingUrl
    fun getTitle(): String? = nativeAdAssets?.title?.text
    fun getDescription(): String? = nativeAdAssets?.description?.value
    fun getCallToAction(): String? = nativeAdAssets?.callToAction?.value
    fun getMainImageUrl(): String? = nativeAdAssets?.mainImage?.url
    fun getIconUrl(): String? = nativeAdAssets?.iconImage?.url
    fun getSponsoredBy(): String? = nativeAdAssets?.sponsoredBy?.value
    fun getRating(): String? = nativeAdAssets?.rating?.value // Assuming rating is stored as string
    fun getVideoVastTag(): String? = nativeAdAssets?.video?.vastTag


    fun registerViewForInteraction(
        adView: View,
        clickableViews: List<View>,
        nonClickableViews: List<View> = emptyList() // Not directly used in this basic setup
    ) {
        if (!isLoaded || nativeAdAssets == null) {
            Log.w(TAG, "NativeAd not loaded or assets not available. Cannot register view.")
            return
        }

        this.registeredAdView = WeakReference(adView)
        this.clickableViewsRefs = clickableViews.map { WeakReference(it) }

        val clickListener = View.OnClickListener {
            handleAdClick()
        }
        clickableViewsRefs.forEach { weakView ->
            weakView.get()?.setOnClickListener(clickListener)
            // Ensure clickable views are actually clickable (e.g. not covered by nonClickableViews)
            // This responsibility usually lies with the publisher's layout.
        }

        // Impression Tracking
        if (adView.viewTreeObserver.isAlive) {
            adView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (adView.viewTreeObserver.isAlive) {
                         adView.viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    // A more robust solution would check visibility percentage and duration.
                    // For this basic version, fire impression if view is simply attached and drawn.
                    if (adView.isAttachedToWindow && adView.visibility == View.VISIBLE) {
                        trackImpression()
                    }
                    return true
                }
            })
        } else if (adView.isAttachedToWindow && adView.visibility == View.VISIBLE) {
            // Fallback if ViewTreeObserver is not alive but view is already visible
            trackImpression()
        }
    }

    private fun trackImpression() {
        if (hasTrackedImpression) return
        hasTrackedImpression = true

        Log.d(TAG, "Tracking impression for AdUnit: $adUnitId")
        fireTrackingUrls(impressionTrackers)
        nativeAdListener?.onAdImpression()

        // For JS tracker, MRAID viewableChange events in MraidWebView will handle viewability.
        // If mraidWebViewForJsTracker is used and needs explicit triggering:
        // mraidWebViewForJsTracker?.updateViewability(true) // Assuming visible by now
        // But this is tricky as it's not part of the main view. MRAID JS should handle this.
    }

    private fun handleAdClick() {
        if (hasTrackedClick) return // Track click only once
        hasTrackedClick = true

        Log.d(TAG, "Handling ad click for AdUnit: $adUnitId. Landing URL: $landingUrl")
        fireTrackingUrls(clickTrackers)
        nativeAdListener?.onAdClicked()

        if (!landingUrl.isNullOrBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(landingUrl))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                Log.e(TAG, "Failed to open landing URL (ActivityNotFound): $landingUrl", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to open landing URL (SecurityException): $landingUrl", e)
            }
        }
    }

    private fun fireTrackingUrls(urls: List<String>) {
        urls.forEach { url ->
            if (url.isBlank()) return@forEach
            Log.d(TAG, "Firing tracking URL: $url")
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Failed to fire tracking URL: $url, Error: ${e.message}")
                }
                override fun onResponse(call: Call, response: Response) {
                    response.close() // Consume response body
                    if (response.isSuccessful) {
                        Log.d(TAG, "Successfully fired tracking URL: $url")
                    } else {
                        Log.w(TAG, "Failed to fire tracking URL: $url, Code: ${response.code}")
                    }
                }
            })
        }
    }
    
    private fun resetAdState() {
        isLoaded = false
        hasTrackedImpression = false
        hasTrackedClick = false
        nativeAdAssets = null
        landingUrl = null
        impressionTrackers = emptyList()
        clickTrackers = emptyList()
        jsTracker = null
        mraidWebViewForJsTracker?.destroyMraid()
        mraidWebViewForJsTracker = null
        registeredAdView?.clear()
        clickableViewsRefs.forEach { it.clear() }
        clickableViewsRefs = emptyList()
    }

    fun destroy() {
        Log.d(TAG, "destroy() called for NativeAd: $adUnitId")
        resetAdState()
        nativeAdListener = null
        // Cancel OkHttp calls if any are tagged and cancellable (not done in this basic version)
    }
}

```
