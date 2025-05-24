package com.example.adsdk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Based on OpenRTB 2.5/2.6 Specification
// Includes essential fields for processing a bid response.

@JsonClass(generateAdapter = true)
data class OpenRTBResponse(
    val id: String, // ID of the bid request to which this is a response.
    @Json(name = "seatbid") val seatBid: List<SeatBid>? = null,
    @Json(name = "bidid") val bidId: String? = null, // Optional response tracking ID
    val cur: String = "USD", // Currency of bid price
    @Json(name = "customdata") val customData: String? = null,
    val nbr: Int? = null // No-Bid Reason Codes (see OpenRTB spec for values)
    // ext: Any? // Placeholder for exchange-specific extensions
)

@JsonClass(generateAdapter = true)
data class SeatBid(
    val bid: List<Bid>,
    @Json(name = "seat") val seat: String? = null, // ID of the buyer seat on whose behalf this bid is made
    @Json(name = "group") val group: Int = 0 // 0 = normal bid, 1 = group bid
    // ext: Any?
)

@JsonClass(generateAdapter = true)
data class Bid(
    val id: String, // Bidder's unique ID for the bid
    @Json(name = "impid") val impId: String, // ID of the impression object in the bid request
    val price: Float, // Bid price in currency specified by response.cur
    @Json(name = "adm") val adMarkup: String? = null, // Ad markup. Can be HTML, VAST XML, or JSON for Native.
    @Json(name = "adid") val adId: String? = null, // Advertiser's campaign ID
    @Json(name = "adomain") val advertiserDomains: List<String>? = null, // Advertiser domain for block list checking
    @Json(name = "bundle") val appBundle: String? = null, // App bundle of the ad if the ad is an app install ad
    @Json(name = "iurl") val imageUrl: String? = null, // Sample image URL (deprecated by crid)
    @Json(name = "cid") val campaignId: String? = null, // Campaign ID
    @Json(name = "crid") val creativeId: String? = null, // Creative ID
    val cat: List<String>? = null, // IAB content categories of the creative
    val api: Int? = null, // API framework required for the ad (e.g., 5 for MRAID-2, 6 for MRAID-3)
    val protocol: Int? = null, // Protocol for video ads (e.g., VAST version)
    val qagmediarating: Int? = null, // Media rating of the creative (see OpenRTB spec)
    val w: Int? = null, // Width of the creative
    val h: Int? = null, // Height of the creative
    @Json(name = "dealid") val dealId: String? = null, // Deal ID for PMP
    val exp: Int? = null, // Advisory indicating expiration of bid in seconds
    @Json(name = "burl") val billingUrl: String? = null, // Optional billing notice URL
    @Json(name = "lurl") val lossUrl: String? = null, // Optional loss notice URL
    @Json(name = "tactic") val tactic: String? = null, // Tactic ID for private marketplace deals
    @Json(name = "ext") val ext: BidExt? = null // Placeholder for bidder-specific extensions
)

// Example of how to handle extensions if needed for native ad parsing or other features
@JsonClass(generateAdapter = true)
data class BidExt(
    @Json(name = "prebid") val prebid: PrebidExt? = null,
    // Other exchange specific extensions can be added here
    @Json(name = "sdk_native_payload") val sdkNativePayload: NativeAdMarkup? = null // Custom field for parsed native ad
)

@JsonClass(generateAdapter = true)
data class PrebidExt( // Common extension used by Prebid Server
    val type: String? = null, // e.g., "banner", "video", "native"
    val cache: CacheExt? = null,
    @Json(name = "targeting") val targeting: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class CacheExt(
    val key: String? = null,
    val url: String? = null
)


// Native Ad Markup Response (parsed from Bid.adm if it's a native ad)
// This structure needs to align with what the SDK expects to render.
// This is the JSON structure stringified in Bid.adm for native ads.
@JsonClass(generateAdapter = true)
data class NativeAdMarkup(
    val assets: List<NativeAssetResponse>,
    val link: NativeLink,
    @Json(name = "imptrackers") val impressionTrackers: List<String>? = null,
    @Json(name = "jstracker") val jsTracker: String? = null, // Optional JS tracker code
    val ver: String? = "1.2" // Native Ad Specification Version
    // privacy: String? // URL to a page with privacy policies for the ad
)

@JsonClass(generateAdapter = true)
data class NativeAssetResponse(
    val id: Int,
    val required: Int = 0, // Echoed from request
    val title: NativeTitleResponse? = null,
    val img: NativeImageResponse? = null,
    val video: NativeVideoResponse? = null, // For native video ads
    val data: NativeDataResponse? = null
    // link: NativeLink? // For assets that are themselves clickable
)

@JsonClass(generateAdapter = true)
data class NativeTitleResponse(
    val text: String
    // len: Int // Optional, max length from request
)

@JsonClass(generateAdapter = true)
data class NativeImageResponse(
    val url: String,
    val type: Int? = null, // Echoed from request
    val w: Int? = null,
    val h: Int? = null
)

@JsonClass(generateAdapter = true)
data class NativeVideoResponse( // Corresponds to OpenRTB Native Video object, not the main Video object
    val vasttag: String // VAST XML string or a VAST URL
    // Other fields like 'cururls' if directly embedding VAST
)

@JsonClass(generateAdapter = true)
data class NativeDataResponse(
    val value: String,
    val type: Int? = null // Echoed from request
    // len: Int // Optional, max length from request
)

@JsonClass(generateAdapter = true)
data class NativeLink(
    val url: String,
    @Json(name = "clicktrackers") val clickTrackers: List<String>? = null,
    @Json(name = "fallback") val fallbackUrl: String? = null
    // ext: Any?
)

// No-Bid Reason Codes (OpenRTB Section 5.24)
object NoBidReasonCode {
    const val UNKNOWN_ERROR = 0
    const val TECHNICAL_ERROR = 1
    const val INVALID_REQUEST = 2
    const val KNOWN_WEB_SPIDER = 3
    const val SUSPECTED_NON_HUMAN_TRAFFIC = 4
    const val CLOUD_DATACENTER_PROXY_IP = 5
    const val UNSUPPORTED_DEVICE = 6
    const val BLOCKED_PUBLISHER = 7 // or site
    const val UNMATCHED_USER = 8
    const val DAILY_READER_CAP_MET = 9
    const val DAILY_DOMAIN_CAP_MET = 10
    const val ADS_TXT_AUTHORIZATION_UNAVAILABLE = 11 // Ads.txt not found or unauthorized
    const val ADS_CERT_AUTHENTICATION_FAILURE = 12 // Ads.cert authentication failure
}
```
