package com.example.adsdk

data class AdRequest(
    val adUnitId: String,
    val format: AdFormat,
    val userId: String? = null,
    // Optional: Specify exact pixel dimensions for the ad request,
    // particularly for banner ads. If not provided, AdLoader might use defaults
    // or derive from format.
    val widthPx: Int? = null,
    val heightPx: Int? = null
)

enum class AdFormat {
    BANNER,
    INTERSTITIAL,
    NATIVE
}
