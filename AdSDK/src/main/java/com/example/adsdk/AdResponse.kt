package com.example.adsdk

data class AdResponse(
    val creativePayload: String, // Could be HTML, JSON, or other formats
    val width: Int,
    val height: Int,
    val adType: AdType
)

enum class AdType {
    HTML,
    VAST, // For video ads
    NATIVE // For native ad components
}
