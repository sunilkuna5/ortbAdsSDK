package com.example.adsdk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Based on OpenRTB 2.5/2.6 Specification
// Only essential fields for mobile app context are included initially.

@JsonClass(generateAdapter = true)
data class OpenRTBRequest(
    val id: String, // Unique ID of the bid request
    val imp: List<Impression>,
    val app: App,
    val device: Device,
    val user: User? = null,
    val test: Int = 0, // 0 = live, 1 = test mode
    val at: Int = 2, // Auction type, 2 = Second Price Auction
    val tmax: Int? = null, // Timeout in milliseconds
    @Json(name = "bcat") val blockedCategories: List<String>? = null,
    @Json(name = "badv") val blockedAdvertisers: List<String>? = null,
    val regs: Regs? = null,
    val source: Source? = null
)

@JsonClass(generateAdapter = true)
data class Impression(
    val id: String, // Unique ID for this impression
    val banner: Banner? = null,
    val video: Video? = null,
    @Json(name = "native") val nativeAd: Native? = null,
    val instl: Int = 0, // 1 if interstitial, 0 if not
    @Json(name = "tagid") val tagId: String? = null, // Ad unit ID
    @Json(name = "bidfloor") val bidFloor: Float = 0.0f, // Minimum bid price
    @Json(name = "bidfloorcur") val bidFloorCurrency: String = "USD",
    val secure: Int = 1 // 1 if HTTPS is required
)

@JsonClass(generateAdapter = true)
data class Banner(
    val w: Int,
    val h: Int,
    val format: List<Format>? = null, // Array of Format objects for various sizes if flexible
    val api: List<Int>? = null, // API frameworks supported (e.g., 5 for MRAID-2, 6 for MRAID-3)
    val pos: Int? = null, // Ad position (e.g., 1=Above Fold, 7=Full Screen)
    @Json(name = "btype") val blockedTypes: List<Int>? = null,
    @Json(name = "battr") val blockedAttributes: List<Int>? = null,
    val expdir: List<Int>? = null // Expandable ad directions
)

@JsonClass(generateAdapter = true)
data class Format(
    val w: Int,
    val h: Int
)

@JsonClass(generateAdapter = true)
data class Video(
    val mimes: List<String>, // e.g., ["video/mp4"]
    val minduration: Int,
    val maxduration: Int,
    val protocols: List<Int>? = null, // e.g., [2, 3] for VAST 2.0, VAST 3.0
    val w: Int,
    val h: Int,
    val linearity: Int, // 1 for In-Stream, 2 for Overlay
    val api: List<Int>? = null, // API frameworks (e.g., 5 for MRAID-2)
    val sequence: Int = 1,
    @Json(name = "battr") val blockedAttributes: List<Int>? = null,
    val maxextended: Int? = null,
    val minbitrate: Int? = null,
    val maxbitrate: Int? = null,
    val delivery: List<Int>? = null, // e.g., [0] for streaming
    val pos: Int? = null,
    val skip: Int? = null, // 0 = no skip, 1 = skippable
    val skipmin: Int = 0,
    val skipafter: Int = 0
)

@JsonClass(generateAdapter = true)
data class Native(
    val request: String, // Stringified JSON NativeMarkupRequest
    val ver: String = "1.2", // Native Ad Specification Version
    val api: List<Int>? = null, // API frameworks (e.g., 5 for MRAID-2)
    @Json(name = "battr") val blockedAttributes: List<Int>? = null
)

// This will be serialized to JSON string for Native.request
@JsonClass(generateAdapter = true)
data class NativeMarkupRequest(
    val ver: String = "1.2",
    @Json(name = "layout") val layout: Int? = null, // Optional: Layout ID
    @Json(name = "adunit") val adUnit: Int? = null, // Optional: Ad unit ID
    val plcmttype: Int? = null, // Optional: Placement type
    val plcmtcnt: Int? = null, // Optional: Placement count
    val context: Int? = null, // e.g. 1 for Content-centric context
    val contextsubtype: Int? = null, // e.g. 10 for General or Mixed Content
    val assets: List<NativeAsset>
)

@JsonClass(generateAdapter = true)
data class NativeAsset(
    val id: Int,
    val required: Int = 0,
    val title: NativeTitle? = null,
    val img: NativeImage? = null,
    val data: NativeData? = null,
    val video: NativeVideo? = null // For native video, if supported
)

@JsonClass(generateAdapter = true)
data class NativeTitle(
    val len: Int
)

@JsonClass(generateAdapter = true)
data class NativeImage(
    val type: Int, // 1 for Icon, 3 for Main image
    val w: Int? = null,
    val h: Int? = null,
    val wmin: Int? = null,
    val hmin: Int? = null,
    val mimes: List<String>? = null // e.g. ["image/jpg", "image/png"]
)

@JsonClass(generateAdapter = true)
data class NativeData(
    val type: Int, // 1 for sponsored by, 2 for desc, 3 for rating, etc.
    val len: Int
)

@JsonClass(generateAdapter = true)
data class NativeVideo( // Corresponds to the Video object in OpenRTB Native spec, not the main Video object
    val mimes: List<String>,
    val minduration: Int,
    val maxduration: Int,
    val protocols: List<Int>
)


@JsonClass(generateAdapter = true)
data class App(
    val id: String? = null, // Application ID on the exchange
    val name: String? = null,
    val bundle: String, // Application bundle/package name
    val domain: String? = null,
    val storeurl: String? = null,
    val cat: List<String>? = null, // IAB content categories
    val sectioncat: List<String>? = null,
    val pagecat: List<String>? = null,
    val ver: String? = null, // Application version
    val privacypolicy: Int? = null, // 0 = No, 1 = Yes
    val publisher: Publisher? = null,
    val paid: Int? = null // 0 = free, 1 = paid
)

@JsonClass(generateAdapter = true)
data class Publisher(
    val id: String? = null,
    val name: String? = null,
    val cat: List<String>? = null,
    val domain: String? = null
)

@JsonClass(generateAdapter = true)
data class Device(
    val ua: String? = null, // User agent
    val ip: String? = null, // IPv4 address
    val geo: Geo? = null,
    val dnt: Int? = null, // Do Not Track, 0 = allowed, 1 = not allowed
    val lmt: Int? = null, // Limit Ad Tracking, 0 = allowed, 1 = not allowed (iOS)
    val ifa: String? = null, // ID for advertisers
    val devicetype: Int? = null, // e.g., 2 for Phone, 3 for Tablet (OpenRTB DeviceType)
    val make: String? = null, // Device make (e.g., "Apple")
    val model: String? = null, // Device model (e.g., "iPhone")
    val os: String, // Operating system (e.g., "iOS", "Android")
    val osv: String, // OS version (e.g., "15.1")
    val h: Int? = null, // Physical screen height in pixels
    val w: Int? = null, // Physical screen width in pixels
    val ppi: Int? = null, // Screen size in pixels per inch
    val pxratio: Float? = null, //  Ratio of physical pixels to device independent pixels
    val js: Int = 1, // JavaScript support
    val language: String? = null, // Browser language
    val connectiontype: Int? = null // Network connection type (e.g., 2 for WiFi, 3 for Cellular)
)

@JsonClass(generateAdapter = true)
data class Geo(
    val lat: Float? = null,
    val lon: Float? = null,
    val type: Int? = null, // Source of location data (e.g., 1=GPS, 2=IP)
    val country: String? = null,
    val region: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val accuracy: Int? = null // Estimated accuracy in meters
)

@JsonClass(generateAdapter = true)
data class User(
    val id: String? = null, // Exchange-specific user ID
    val buyeruid: String? = null, // Buyer's user ID
    val yob: Int? = null, // Year of birth
    val gender: String? = null, // "M", "F", "O"
    val keywords: String? = null,
    val customdata: String? = null,
    val geo: Geo? = null // User's location, if different from device
)

@JsonClass(generateAdapter = true)
data class Regs(
    val coppa: Int? = null, // 1 if COPPA applies
    val gdpr: Int? = null, // 1 if GDPR applies
    @Json(name = "us_privacy") val usPrivacy: String? = null // IAB U.S. Privacy String
)

@JsonClass(generateAdapter = true)
data class Source(
    @Json(name = "fd") val finalDecision: Int? = null, // 1 if final decision maker, 0 if not
    @Json(name = "tid") val transactionId: String? = null, // Transaction ID
    @Json(name = "pchain") val paymentChain: String? = null // Payment chain string
)

// Enums for values (can be expanded)
object ApiFrameworks {
    const val VPAID_1 = 1
    const val VPAID_2 = 2
    const val MRAID_1 = 3 // MRAID = MRAID-1
    const val ORMMA = 4
    const val MRAID_2 = 5
    const val MRAID_3 = 6
    const val OMID_1 = 7 // Open Measurement
}

object DeviceType {
    const val MOBILE_TABLET = 1 // Deprecated
    const val PHONE = 4 // Mobile an Cellular Network (Enhanced)
    const val TABLET = 5 // Tablet an Cellular Network (Enhanced)
    const val PERSONAL_COMPUTER = 2
    const val CONNECTED_TV = 3
    const val CONNECTED_DEVICE = 6 // e.g. IoT
    const val SET_TOP_BOX = 7
}

object NativeAdUnit { // For NativeMarkupRequest.adunit
    const val PAID_SEARCH_UNIT = 1
    const val RECOMMENDATION_WIDGET = 2
    const val PROMOTED_LISTING = 3
    // ... and more as per spec
}

object NativeContext { // For NativeMarkupRequest.context
    const val CONTENT = 1 // Content-centric context (e.g., article)
    const val SOCIAL = 2 // Social-centric context (e.g., social feed)
    const val PRODUCT = 3 // Product-centric context (e.g., product listings)
    // ... and more as per spec
}
object NativeContextSubtype { // For NativeMarkupRequest.contextsubtype
    const val GENERAL = 10 // General or mixed content
    const val ARTICLE = 11 // Primarily article content
    const val VIDEO = 12 // Primarily video content
    // ... and more
}

object NativePlacementType { // For NativeMarkupRequest.plcmttype
    const val IN_FEED = 1
    const val ATOMIC_UNIT = 2
    const val OUTSIDE_CORE_CONTENT = 3
    const val RECOMMENDATION_WIDGET_NATIVE = 4 // different from AdUnit
    // ... and more
}

object NativeImageAssetType {
    const val ICON = 1
    const val LOGO = 2 // Deprecated by OpenRTB Native 1.2, but some old systems might use it
    const val MAIN = 3
}

object NativeDataAssetType {
    const val SPONSORED = 1
    const val DESC = 2
    const val RATING = 3
    const val LIKES = 4
    const val DOWNLOADS = 5
    const val PRICE = 6
    const val SALEPRICE = 7
    const val PHONE = 8
    const val ADDRESS = 9
    const val DESC2 = 10
    const val DISPLAYURL = 11
    const val CTA = 12
}

object AdPosition {
    const val UNKNOWN = 0
    const val ABOVE_THE_FOLD = 1
    const val BELOW_THE_FOLD = 3 // DEPRECATED - USE LIKELY_BELOW_THE_FOLD
    const val HEADER = 4
    const val FOOTER = 5
    const val SIDEBAR = 6
    const val FULL_SCREEN = 7
}

object VideoLinearity {
    const val IN_STREAM = 1 // e.g. pre-roll, mid-roll, post-roll
    const val OVERLAY = 2
}

object VideoProtocols {
    const val VAST_1_0 = 1
    const val VAST_2_0 = 2
    const val VAST_3_0 = 3
    const val VAST_1_0_WRAPPER = 4
    const val VAST_2_0_WRAPPER = 5
    const val VAST_3_0_WRAPPER = 6
    const val VAST_4_0 = 7
    const val VAST_4_0_WRAPPER = 8
    const val VAST_4_1 = 9
    const val VAST_4_1_WRAPPER = 10
    const val VAST_4_2 = 11
    const val VAST_4_2_WRAPPER = 12
    const val DAAST_1_0 = 13 // Digital Audio Ad Serving Template
    const val DAAST_1_0_WRAPPER = 14
}

object ConnectionType {
    const val UNKNOWN = 0
    const val ETHERNET = 1
    const val WIFI = 2
    const val CELLULAR_UNKNOWN_GENERATION = 3
    const val CELLULAR_2G = 4
    const val CELLULAR_3G = 5
    const val CELLULAR_4G = 6
    const val CELLULAR_5G = 7
}

// For Banner.btype, Banner.battr, Video.battr, Native.battr
// Refer to OpenRTB spec "Content Categories", "Creative Attributes"
// Example: CreativeAttribute.USER_INTERACTIVE (e.g. MRAID) = 5

// Regs.gdpr, Regs.coppa values 0 or 1
// Regs.usPrivacy is a string like "1NYN"
// Source.finalDecision 0 or 1
// Impression.secure 0 or 1 (default to 1 for https)
// Impression.instl 0 or 1
// Device.dnt, Device.lmt 0 or 1
// Device.js 0 or 1 (default to 1)
// App.privacypolicy 0 or 1
// App.paid 0 or 1

// Test mode: OpenRTBRequest.test = 1
// Auction type: OpenRTBRequest.at = 2 (Second Price)
// Native version: Native.ver = "1.2", NativeMarkupRequest.ver = "1.2"
// Geo.type (1=GPS, 2=IP, 3=User Provided)

// Banner.api / Video.api / Native.api should list supported API frameworks
// e.g. listOf(ApiFrameworks.MRAID_3, ApiFrameworks.OMID_1)
// For MRAID compliance, SDK should indicate support for MRAID 2.0 (5) or 3.0 (6).
// OMID is for viewability.
```
