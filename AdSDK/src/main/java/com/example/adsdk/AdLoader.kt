package com.example.adsdk

import android.util.Log
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*

class AdLoader {

    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val openRTBRequestAdapter = moshi.adapter(OpenRTBRequest::class.java)
    private val openRTBResponseAdapter = moshi.adapter(OpenRTBResponse::class.java)
    private val nativeAdMarkupAdapter = moshi.adapter(NativeAdMarkup::class.java)


    private val TAG = "AdLoader"
    // Replace with your actual ad server endpoint that supports OpenRTB
    private val AD_SERVER_URL = "https://mock-ad-server.vercel.app/api/openrtb"

    interface AdLoadListener {
        fun onAdLoaded(adResponse: AdResponse)
        fun onError(adError: AdError)
    }

    fun loadAd(adRequest: AdRequest, listener: AdLoadListener) {
        if (AdSdk.getApiKey() == null || AdSdk.getApplicationContext() == null) {
            Log.e(TAG, "AdSDK not initialized. Call AdSdk.initialize() first.")
            listener.onError(AdError(AdErrorCodes.SDK_NOT_INITIALIZED, "SDK not initialized"))
            return
        }

        val context = AdSdk.getApplicationContext()!!
        val openRTBRequest = buildOpenRTBRequest(adRequest, context)

        val jsonRequest: String
        try {
            jsonRequest = openRTBRequestAdapter.toJson(openRTBRequest)
            Log.d(TAG, "OpenRTB Request: $jsonRequest")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize OpenRTBRequest: ${e.message}", e)
            listener.onError(AdError(AdErrorCodes.INVALID_REQUEST, "Failed to build ad request"))
            return
        }

        val requestBody = jsonRequest.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(AD_SERVER_URL)
            .post(requestBody)
            .addHeader("x-openrtb-version", "2.5") // Standard OpenRTB header
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Ad request failed: ${e.message}", e)
                listener.onError(AdError(AdErrorCodes.NETWORK_ERROR, e.message ?: "Network error"))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code == 204) { // HTTP 204 No Content means No Bid
                    Log.i(TAG, "Ad server returned HTTP 204: No Bid")
                    listener.onError(AdError(AdErrorCodes.NO_FILL, "No ad available (No Bid)"))
                    return
                }

                if (!response.isSuccessful) {
                    Log.e(TAG, "Ad server returned error: ${response.code} ${response.message}")
                    val responseBodyString = response.body?.string() ?: "Empty error body"
                    Log.e(TAG, "Error body: $responseBodyString")
                    listener.onError(AdError(response.code, "Server error: ${response.message} - $responseBodyString"))
                    return
                }

                val responseBody = response.body?.string()
                if (responseBody == null) {
                    Log.e(TAG, "Ad server returned empty response body.")
                    listener.onError(AdError(AdErrorCodes.NO_FILL, "Empty response body"))
                    return
                }
                Log.d(TAG, "OpenRTB Response: $responseBody")

                try {
                    val openRTBResponse = openRTBResponseAdapter.fromJson(responseBody)
                    if (openRTBResponse == null) {
                        Log.e(TAG, "Failed to parse OpenRTBResponse.")
                        listener.onError(AdError(AdErrorCodes.SERVER_ERROR, "Failed to parse ad response"))
                        return
                    }

                    if (openRTBResponse.nbr != null) {
                        Log.i(TAG, "OpenRTB No-Bid Reason Code: ${openRTBResponse.nbr}")
                        listener.onError(AdError(mapNbrToAdErrorCode(openRTBResponse.nbr), "No ad available (NBR: ${openRTBResponse.nbr})"))
                        return
                    }

                    val firstBid = openRTBResponse.seatBid?.firstOrNull()?.bid?.firstOrNull()
                    if (firstBid == null || firstBid.adMarkup == null) {
                        Log.i(TAG, "No valid bid found in OpenRTB response.")
                        listener.onError(AdError(AdErrorCodes.NO_FILL, "No ad available (empty bid)"))
                        return
                    }
                    
                    // Determine AdType based on bid details (e.g. presence of native markup, VAST XML vs HTML)
                    // For this example, we'll assume HTML or Native based on presence of firstBid.ext.sdkNativePayload
                    // A more robust solution would inspect firstBid.adm or rely on prebid.type if available.

                    var adType = AdType.HTML // Default to HTML
                    var creativePayload = firstBid.adMarkup

                    // Check if it's a native ad by looking for our custom extension or by trying to parse adm
                    // In a real scenario, the server should populate bid.ext.sdkNativePayload or bid.mtype for native
                    if (firstBid.ext?.sdkNativePayload != null) {
                        adType = AdType.NATIVE
                        // The creativePayload for NATIVE type AdResponse should be the stringified NativeAdMarkup
                        creativePayload = nativeAdMarkupAdapter.toJson(firstBid.ext.sdkNativePayload)
                         Log.d(TAG, "Native Ad Markup Payload: $creativePayload")
                    } else if (adRequest.format == AdFormat.NATIVE) {
                        // Try to parse adm as NativeAdMarkup if format was native
                        try {
                            nativeAdMarkupAdapter.fromJson(firstBid.adMarkup) // validate
                            adType = AdType.NATIVE // If parsable, it's native
                            // creativePayload remains firstBid.adMarkup (the stringified JSON)
                             Log.d(TAG, "Native Ad Markup from adm: $creativePayload")
                        } catch (e: Exception) {
                            Log.w(TAG, "Requested NATIVE, but adm is not valid NativeAdMarkup JSON. Falling back to HTML interpretation or error.")
                            // Depending on strictness, either error out or try to render as HTML if possible
                            // For now, let's assume if NATIVE was requested, it must be valid native markup
                            listener.onError(AdError(AdErrorCodes.SERVER_ERROR, "Invalid Native Ad Markup received"))
                            return
                        }
                    }
                    // TODO: Add VAST/Video type detection based on firstBid.adm (e.g. check for <VAST> tag)
                    // For now, we only explicitly handle HTML and NATIVE.

                    val adResponse = AdResponse(
                        creativePayload = creativePayload,
                        width = firstBid.w ?: 0,
                        height = firstBid.h ?: 0,
                        adType = adType
                    )
                    listener.onAdLoaded(adResponse)

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse or process OpenRTB response: ${e.message}", e)
                    listener.onError(AdError(AdErrorCodes.SERVER_ERROR, "Error processing ad response: ${e.message}"))
                }
            }
        })
    }

    private fun buildOpenRTBRequest(adRequest: AdRequest, context: Context): OpenRTBRequest {
        val app = createAppObject(context)
        val device = createDeviceObject(context)
        val user = createUserObject(adRequest.userId) // Pass userId if available

        val impId = UUID.randomUUID().toString()
        val impression = Impression(
            id = impId,
            tagId = adRequest.adUnitId,
            instl = if (adRequest.format == AdFormat.INTERSTITIAL) 1 else 0,
            banner = if (adRequest.format == AdFormat.BANNER || adRequest.format == AdFormat.INTERSTITIAL) {
                // For INTERSTITIAL, typically full screen. For BANNER, specific sizes.
                // Use exact pixel dimensions from AdRequest if provided, otherwise use defaults or derive.
                val bannerWidthPx = adRequest.widthPx ?: if (adRequest.format == AdFormat.INTERSTITIAL) device.w ?: 320 else 320
                val bannerHeightPx = adRequest.heightPx ?: if (adRequest.format == AdFormat.INTERSTITIAL) device.h ?: 480 else 50
                
                // Create Format object list if specific w/h are provided
                val formatList = if (adRequest.widthPx != null && adRequest.heightPx != null) {
                    listOf(Format(w = adRequest.widthPx, h = adRequest.heightPx))
                } else {
                    // Default formats or formats derived from AdFormat if not explicitly set in AdRequest
                    // For example, a standard banner size
                    listOf(Format(w = bannerWidthPx, h = bannerHeightPx)) // Fallback to the main w/h
                }

                Banner(
                    w = bannerWidthPx, // OpenRTB width in pixels
                    h = bannerHeightPx, // OpenRTB height in pixels
                    format = formatList, // Array of Format objects for various sizes if flexible
                    api = listOf(ApiFrameworks.MRAID_2, ApiFrameworks.MRAID_3), // Support MRAID
                    pos = if (adRequest.format == AdFormat.INTERSTITIAL) AdPosition.FULL_SCREEN else AdPosition.ABOVE_THE_FOLD // Example position
                )
            } else null,
            nativeAd = if (adRequest.format == AdFormat.NATIVE) {
                createNativeObject()
            } else null,
            video = null, // TODO: Add video support if needed
            bidFloor = 0.01f, // Example bid floor
            secure = 1 // Request secure assets
        )

        return OpenRTBRequest(
            id = UUID.randomUUID().toString(),
            imp = listOf(impression),
            app = app,
            device = device,
            user = user,
            test = 1, // Set to 1 for test mode, 0 for live
            at = 2, // Second Price Auction
            tmax = 1000, // Timeout in milliseconds
            regs = Regs(coppa = 0) // Example: COPPA not applicable
        )
    }

    private fun createNativeObject(): Native {
        val nativeMarkupRequest = NativeMarkupRequest(
            ver = "1.2",
            // context = NativeContext.CONTENT, // Example context
            // plcmttype = NativePlacementType.IN_FEED, // Example placement
            assets = listOf(
                NativeAsset(id = 1, required = 1, title = NativeTitle(len = 100)),
                NativeAsset(id = 2, required = 1, img = NativeImage(type = NativeImageAssetType.MAIN, wmin = 200, hmin = 200, mimes = listOf("image/jpeg", "image/png"))),
                NativeAsset(id = 3, required = 0, img = NativeImage(type = NativeImageAssetType.ICON, wmin = 50, hmin = 50, mimes = listOf("image/jpeg", "image/png"))),
                NativeAsset(id = 4, required = 1, data = NativeData(type = NativeDataAssetType.DESC, len = 150)),
                NativeAsset(id = 5, required = 0, data = NativeData(type = NativeDataAssetType.CTA, len = 20))
            )
        )
        val nativeMarkupRequestString = moshi.adapter(NativeMarkupRequest::class.java).toJson(nativeMarkupRequest)
        return Native(
            request = nativeMarkupRequestString,
            ver = "1.2",
            api = listOf(ApiFrameworks.MRAID_2, ApiFrameworks.MRAID_3) // MRAID for rich interactions within native parts if needed
        )
    }


    private fun createAppObject(context: Context): App {
        val packageName = context.packageName
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        val appName = packageManager.getApplicationLabel(appInfo).toString()
        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        return App(
            bundle = packageName,
            name = appName,
            ver = appVersion,
            storeurl = "https://play.google.com/store/apps/details?id=$packageName", // Example store URL
            publisher = Publisher(name = "Example Publisher Inc.") // Placeholder
            // cat = listOf("IAB9-30") // Example: "Technology & Computing"
        )
    }

    @Suppress("DEPRECATION") // For Build.SERIAL and Settings.Secure.ANDROID_ID
    private fun createDeviceObject(context: Context): Device {
        val displayMetrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val advertisingIdClientInfo = try { // This is conceptual. Real implementation requires Google Play Services Ads library.
            Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
            // val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
            // object { val id = adInfo.id; val isLimitAdTrackingEnabled = adInfo.isLimitAdTrackingEnabled }
            object { val id = "test-ifa-uuid"; val isLimitAdTrackingEnabled = false } // Placeholder
        } catch (e: Exception) {
            null
        }


        return Device(
            ua = System.getProperty("http.agent"), // User Agent
            ifa = advertisingIdClientInfo?.id ?: Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID), // Use ANDROID_ID as fallback if no proper IFA
            lmt = if (advertisingIdClientInfo?.isLimitAdTrackingEnabled == true) 1 else 0,
            ip = "192.168.1.100", // Placeholder - Server should use IP from request header
            devicetype = DeviceType.PHONE, // Assuming phone for now
            make = Build.MANUFACTURER,
            model = Build.MODEL,
            os = "Android",
            osv = Build.VERSION.RELEASE,
            h = displayMetrics.heightPixels,
            w = displayMetrics.widthPixels,
            pxratio = displayMetrics.density,
            language = Locale.getDefault().language,
            connectiontype = ConnectionType.WIFI, // Placeholder, detect actual type
            js = 1 // JavaScript support assumed
        )
    }

    private fun createUserObject(userId: String?): User {
        return User(
            id = userId, // User ID from AdRequest if available
            // buyeruid = "some-buyer-uid" // If SDK has its own stable user ID recognized by the ad exchange
        )
    }

    private fun mapNbrToAdErrorCode(nbr: Int): Int {
        return when (nbr) {
            NoBidReasonCode.UNKNOWN_ERROR -> AdErrorCodes.SERVER_ERROR
            NoBidReasonCode.TECHNICAL_ERROR -> AdErrorCodes.SERVER_ERROR
            NoBidReasonCode.INVALID_REQUEST -> AdErrorCodes.INVALID_REQUEST
            // Add more specific mappings as needed
            else -> AdErrorCodes.NO_FILL // Default to NO_FILL for other NBR codes
        }
    }
}
