package com.example.adsdk

interface BaseAdListener {
    fun onAdFailedToLoad(error: AdError)
    fun onAdClicked() // New: For any user click resulting in an action (e.g. opening URL)
    fun onAdClosed() // Ad view dismissed (interstitial) or creative closed itself (MRAID banner)
    fun onAdImpression() // New: Ad impression has been recorded by the SDK
    // fun onAdLeftApplication() // For future use - when click causes user to leave app
}

interface BannerAdListener : BaseAdListener {
    fun onAdLoaded(adView: BannerAdView)
    fun onAdOpened() // Specifically for banner expansion / taking over screen
    // onAdClicked from BaseAdListener handles simple clicks on banner.
    // onAdImpression from BaseAdListener handles impression.
    // onAdClosed from BaseAdListener handles MRAID close if it was expanded.
}

interface InterstitialAdListener : BaseAdListener {
    fun onAdLoaded(interstitialAd: InterstitialAd)
    fun onAdShown() // Interstitial is displayed on screen
    // onAdClicked from BaseAdListener handles clicks on interstitial.
    // onAdClosed from BaseAdListener handles interstitial dismissal.
    // onAdImpression from BaseAdListener handles impression (typically when shown).
}

interface NativeAdListener : BaseAdListener {
    fun onAdLoaded(nativeAd: NativeAd)
    // onAdClicked from BaseAdListener handles clicks.
    // onAdImpression from BaseAdListener handles impression.
    // onAdClosed is less typical for native unless it involves a modal/fullscreen view.
}

// Forward declaration for NativeAd class for the listener
// class NativeAd(context: android.content.Context, adUnitId: String) {} // No longer needed here
```
