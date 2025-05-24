# Banner Ads Integration Guide

Banner ads are rectangular image or text ads that occupy a spot within an app's layout. They typically stay on screen while users are interacting with the app.

## Overview

The AdSDK provides `BannerAdView`, a custom `View` that handles fetching and displaying banner ads. It supports various standard banner sizes and MRAID creatives.

## Adding BannerAdView to Layout

You can add `BannerAdView` to your XML layout like any other view.

```xml
<!-- activity_main.xml or your_layout.xml -->
<com.example.adsdk.BannerAdView
    android:id="@+id/bannerAdView"
    android:layout_width="wrap_content"  <!-- Or a fixed DP size -->
    android:layout_height="wrap_content" <!-- Or a fixed DP size -->
    app:adUnitId="YOUR_BANNER_AD_UNIT_ID"
    app:adSize="BANNER_320X50" 
    app:refreshRate="30" /> 
    <!-- Ensure xmlns:app="http://schemas.android.com/apk/res-auto" is in your root layout -->
```

**Attributes:**

-   `android:id`: A unique ID for the view.
-   `android:layout_width`, `android:layout_height`: Standard layout parameters. For banner ads, these are often set to `wrap_content` if you want the view to size itself to the ad, or you can specify exact dimensions that match one of the supported ad sizes.
-   `app:adUnitId` (required): Your unique identifier for this ad placement.
-   `app:adSize` (optional, default: `BANNER_320X50`): The desired size of the banner ad. See [Supported Ad Sizes](#supported-ad-sizes).
-   `app:refreshRate` (optional, default: `30`): The auto-refresh interval in seconds. Set to `0` or a negative value to disable auto-refresh.

Alternatively, you can create and configure `BannerAdView` programmatically.

## Supported Ad Sizes

The SDK provides predefined `AdSize` constants. You can set the `adSize` attribute in XML using the string representation of these constants or use the `AdSize` objects when configuring programmatically.

Available `AdSize` constants (defined in `com.example.adsdk.AdSize`):

-   `AdSize.BANNER_320X50` (or `AdSize.BANNER`): 320x50 dp
-   `AdSize.LARGE_BANNER_320X100`: 320x100 dp
-   `AdSize.MEDIUM_RECTANGLE_300X250`: 300x250 dp
-   `AdSize.FULL_BANNER_468X60`: 468x60 dp
-   `AdSize.LEADERBOARD_728X90`: 728x90 dp

When setting `app:adSize` in XML, use the constant name as a string (e.g., `"BANNER_320X50"`, `"LEADERBOARD_728X90"`).

## Loading an Ad

To load an ad, call the `loadAd()` method on your `BannerAdView` instance. If you have set an `adUnitId` in the XML, the ad might load automatically when the view is attached to the window, depending on the SDK's internal implementation. However, it's good practice to explicitly call `loadAd()` when you want to refresh or load an ad.

```kotlin
// In your Activity or Fragment
val bannerAdView = findViewById<BannerAdView>(R.id.bannerAdView)

// Optional: Configure programmatically if not done in XML
// bannerAdView.setAdUnitId("YOUR_BANNER_AD_UNIT_ID")
// bannerAdView.setAdSize(AdSize.BANNER_320X50)
// bannerAdView.setRefreshRate(60) // Set refresh rate to 60 seconds

bannerAdView.loadAd()
```

## Listening to Ad Events

To receive notifications about ad lifecycle events (e.g., when an ad is loaded, fails to load, or is clicked), implement `BannerAdListener` and set it on your `BannerAdView`.

```kotlin
bannerAdView.setBannerAdListener(object : BannerAdListener {
    override fun onAdLoaded(adView: BannerAdView) {
        // Called when an ad is successfully loaded and displayed.
        Log.i("BannerAd", "Ad loaded successfully!")
    }

    override fun onAdFailedToLoad(error: AdError) {
        // Called when an ad request fails.
        // `error` contains details about the failure (errorCode, errorMessage).
        Log.e("BannerAd", "Ad failed to load: ${error.errorCode} - ${error.errorMessage}")
    }

    override fun onAdClicked() {
        // Called when the user clicks on the ad.
        // This usually means the user will be navigated away from the app (e.g., to a browser or app store).
        Log.i("BannerAd", "Ad clicked!")
    }

    override fun onAdOpened() {
        // Called when the banner ad expands or opens a full-screen overlay (e.g., MRAID expand).
        Log.i("BannerAd", "Ad opened (expanded).")
    }

    override fun onAdClosed() {
        // Called when an expanded banner ad or MRAID creative is closed, returning focus to the app.
        Log.i("BannerAd", "Ad closed (MRAID).")
    }

    override fun onAdImpression() {
        // Called when an impression is recorded for the ad.
        Log.i("BannerAd", "Ad impression recorded.")
    }
})
```

## Refresh Rate

`BannerAdView` can automatically refresh its ad content at a specified interval.

-   **Set via XML:** `app:refreshRate="SECONDS"` (e.g., `"30"` for 30 seconds).
-   **Set Programmatically:** `bannerAdView.setRefreshRate(seconds: Int)`

To disable auto-refresh, set the refresh rate to `0` or a negative value. A new ad load (via `loadAd()`) will also reset the refresh timer.

## Destroying BannerAdView

It's important to clean up the `BannerAdView` when it's no longer needed, typically in your Activity's or Fragment's `onDestroy()` or `onDestroyView()` method respectively. This releases resources, stops ad refresh timers, and prevents potential memory leaks.

```kotlin
// In your Activity
override fun onDestroy() {
    bannerAdView.destroy()
    super.onDestroy()
}

// Or in your Fragment
override fun onDestroyView() {
    bannerAdView.destroy()
    super.onDestroyView()
}
```

---

Next: [Interstitial Ads](./InterstitialAds.md)
