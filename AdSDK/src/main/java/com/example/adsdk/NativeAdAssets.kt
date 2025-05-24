package com.example.adsdk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Enum for asset types (can be extended from OpenRTB spec)
enum class NativeAssetType { TITLE, IMAGE, ICON, DESCRIPTION, CTA_TEXT, RATING, SPONSORED_BY, VIDEO } // Added VIDEO as per OpenRTB NativeAsset

// Base class for an asset
sealed class NativeAsset(val type: NativeAssetType, val id: Int, val required: Boolean) {
    data class Title(val text: String, val assetId: Int, val assetRequired: Boolean) : NativeAsset(NativeAssetType.TITLE, assetId, assetRequired)
    data class Image(val url: String, val width: Int?, val height: Int?, val assetId: Int, val assetRequired: Boolean) : NativeAsset(NativeAssetType.IMAGE, assetId, assetRequired) // For main image
    data class Icon(val url: String, val width: Int?, val height: Int?, val assetId: Int, val assetRequired: Boolean) : NativeAsset(NativeAssetType.ICON, assetId, assetRequired)   // For icon
    // Using a more generic Data class that can hold different types of string-based assets
    data class Data(val value: String, val dataType: OpenRTBNativeDataAssetType, val assetId: Int, val assetRequired: Boolean) : NativeAsset(assetTypeFromDataAssetType(dataType), assetId, assetRequired)
    data class Video(val vastTag: String, val assetId: Int, val assetRequired: Boolean) : NativeAsset(NativeAssetType.VIDEO, assetId, assetRequired) // VAST XML or URL
}

// Helper to map OpenRTB DataAssetType to our NativeAssetType
fun assetTypeFromDataAssetType(dataAssetType: OpenRTBNativeDataAssetType): NativeAssetType {
    return when (dataAssetType) {
        OpenRTBNativeDataAssetType.SPONSORED -> NativeAssetType.SPONSORED_BY
        OpenRTBNativeDataAssetType.DESC -> NativeAssetType.DESCRIPTION
        OpenRTBNativeDataAssetType.RATING -> NativeAssetType.RATING
        OpenRTBNativeDataAssetType.CTA -> NativeAssetType.CTA_TEXT
        else -> NativeAssetType.DESCRIPTION // Fallback or handle other types like LIKES, DOWNLOADS, etc.
    }
}


// Class to hold all assets for a native ad
data class NativeAdAssets(
    val title: NativeAsset.Title?,
    val description: NativeAsset.Data?,
    val callToAction: NativeAsset.Data?,
    val mainImage: NativeAsset.Image?,
    val iconImage: NativeAsset.Icon?,
    val sponsoredBy: NativeAsset.Data?,
    val rating: NativeAsset.Data?, // Added rating
    val video: NativeAsset.Video?, // Added video
    val allAssets: Map<Int, NativeAsset> // Store all assets by ID for generic access
)

// OpenRTB Native Ad Markup (for parsing the 'adm' field from BidResponse)
// This should align with NativeAdMarkup from OpenRTBResponse.kt, maybe reuse/refine it.
// For this task, I'll define it as requested, but ideally, this would be consolidated.
@JsonClass(generateAdapter = true)
data class OpenRTBNativeMarkup(
    val assets: List<OpenRTBNativeAssetResponse>, // Changed to OpenRTBNativeAssetResponse to match existing OpenRTBResponse structure
    val link: OpenRTBNativeLinkResponse, // Changed to OpenRTBNativeLinkResponse
    val imptrackers: List<String>? = null,
    val jstracker: String? = null, // JavaScript tracker code
    val ver: String? = "1.2" // Native Ad Specification Version
    // privacy: String? // URL to a page with privacy policies for the ad
)

// Re-using existing definitions from OpenRTBResponse.kt for consistency if possible,
// but the prompt asks for specific structures here. Let's assume these are slightly different
// or specific to the parsing context of 'adm'.
// However, `NativeAdMarkup` from `OpenRTBResponse.kt` is almost identical.
// For now, I'll use the names as specified in the prompt for this file.
// If this were a real project, I'd consolidate OpenRTBNativeMarkup with NativeAdMarkup from OpenRTBResponse.kt.

@JsonClass(generateAdapter = true)
data class OpenRTBNativeAssetResponse( // Renamed from OpenRTBNativeAsset to avoid conflict if used broadly
    val id: Int,
    val required: Int = 0, // Defaulting to 0 if not present
    val title: OpenRTBTitleAsset? = null,
    val img: OpenRTBImageAsset? = null,
    val data: OpenRTBDataAsset? = null,
    val video: OpenRTBVideoAsset? = null // Added video asset
)

@JsonClass(generateAdapter = true)
data class OpenRTBTitleAsset(val text: String)

@JsonClass(generateAdapter = true)
data class OpenRTBImageAsset(
    val url: String,
    val type: Int? = null, // As per OpenRTB Native Spec: 1 Icon, 2 Logo (deprecated), 3 Main
    val w: Int? = null,
    val h: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenRTBDataAsset(
    val value: String,
    // Using an enum for type safety and clarity
    val type: OpenRTBNativeDataAssetType? = null // Matches value from OpenRTB Native Data Asset Types table
)

@JsonClass(generateAdapter = true)
data class OpenRTBVideoAsset( // Corresponds to the Video object in OpenRTB Native spec
    @Json(name="vasttag") val vastTag: String // VAST XML string or a VAST URL
    // minduration, maxduration, protocols, etc. could be here if needed for pre-filtering or validation
)


@JsonClass(generateAdapter = true)
data class OpenRTBNativeLinkResponse( // Renamed from OpenRTBNativeLink
    val url: String, // Landing URL
    @Json(name = "clicktrackers") val clickTrackers: List<String>? = null,
    @Json(name = "fallback") val fallbackUrl: String? = null
    // ext: Any?
)

// Enum for OpenRTB Native Data Asset Types (subset from spec 1.2)
// This helps in mapping `OpenRTBDataAsset.type` to our internal `NativeAssetType`
enum class OpenRTBNativeDataAssetType(val value: Int) {
    SPONSORED(1),
    DESC(2),
    RATING(3),
    LIKES(4),
    DOWNLOADS(5),
    PRICE(6),
    SALEPRICE(7),
    PHONE(8),
    ADDRESS(9),
    DESC2(10),
    DISPLAYURL(11),
    CTA(12); // Call To Action

    companion object {
        fun fromInt(value: Int) = values().firstOrNull { it.value == value }
    }
}

// Enum for OpenRTB Native Image Asset Types (subset from spec 1.2)
enum class OpenRTBNativeImageAssetType(val value: Int) {
    ICON(1),
    // LOGO(2), // Deprecated in 1.2, but some old systems might use it
    MAIN(3);

    companion object {
        fun fromInt(value: Int) = values().firstOrNull { it.value == value }
    }
}

// Constants for mapping specific asset IDs (if your ad server uses fixed IDs for common assets)
// These are examples and depend on the ad server's native ad configuration.
object NativeAssetId {
    const val TITLE_ID = 1
    const val MAIN_IMAGE_ID = 2
    const val ICON_ID = 3
    const val DESCRIPTION_ID = 4
    const val CTA_TEXT_ID = 5
    const val SPONSORED_BY_ID = 6
    const val RATING_ID = 7
    // Add other standard asset IDs if applicable
}
```
