# Interstitial Ads Integration Guide

Interstitial ads are full-screen ads that cover the interface of their host app. They're typically displayed at natural transition points in the flow of an app, such as between activities or during the pause between levels in a game.

## Overview

The AdSDK provides an `InterstitialAd` class to load and display interstitial ads. These ads are typically MRAID-enabled HTML creatives.

## Creating an InterstitialAd

To get started, create an instance of `InterstitialAd` in your Activity or Fragment. You need to provide a `Context` and your ad unit ID.

```kotlin
// In your Activity or Fragment
lateinit var interstitialAd: InterstitialAd

// In onCreate or a suitable initialization point
// The context should ideally be an Activity context for showing the ad.
interstitialAd = InterstitialAd(this, "YOUR_INTERSTITIAL_AD_UNIT_ID") 
// Or: interstitialAd = InterstitialAd(this)
// interstitialAd.setAdUnitId("YOUR_INTERSTITIAL_AD_UNIT_ID")
```
The `adUnitId` can be set via the constructor or using the `setAdUnitId()` method.

## Listening to Ad Events

Set an `InterstitialAdListener` to be notified of ad lifecycle events. This is crucial for knowing when an ad is ready to be shown or if it failed to load.

```kotlin
interstitialAd.setInterstitialAdListener(object : InterstitialAdListener {
    override fun onAdLoaded(ad: InterstitialAd) {
        // Called when an interstitial ad has successfully loaded.
        // You can now call interstitialAd.show()
        Log.i("InterstitialAd", "Ad loaded successfully!")
        Toast.makeText(this@MainActivity, "Interstitial Ad Loaded", Toast.LENGTH_SHORT).show()
        
        // If you want to show the ad immediately after loading:
        // if (interstitialAd.isLoaded()) {
        //     interstitialAd.show()
        // }
    }

    override fun onAdFailedToLoad(error: AdError) {
        // Called when an ad request fails.
        Log.e("InterstitialAd", "Ad failed to load: ${error.errorCode} - ${error.errorMessage}")
    }

    override fun onAdShown() {
        // Called when the interstitial ad is actually displayed on screen.
        Log.i("InterstitialAd", "Ad shown on screen.")
    }

    override fun onAdClicked() {
        // Called when the user clicks on the ad.
        Log.i("InterstitialAd", "Ad clicked!")
    }

    override fun onAdClosed() {
        // Called when the interstitial ad is closed by the user.
        // At this point, you should resume your app's normal flow and can load a new interstitial.
        Log.i("InterstitialAd", "Ad closed.")
        // Load a new ad if needed for the next opportunity
        // interstitialAd.loadAd() 
    }
    
    override fun onAdImpression() {
        // Called when an impression is recorded for the ad (typically when shown).
        Log.i("InterstitialAd", "Ad impression recorded.")
    }
})
```

## Loading an Ad

To request an interstitial ad, call the `loadAd()` method.

```kotlin
Log.d("InterstitialAd", "Requesting interstitial ad load...")
interstitialAd.loadAd()
```
It's recommended to load an interstitial ad in advance of when you plan to show it. For example, load it when your Activity starts, and then show it at a natural break point.

## Showing the Ad

Once the ad is loaded (i.e., `onAdLoaded()` has been called), you can display it by calling `show()`. Before calling `show()`, it's good practice to check if the ad is ready using `isLoaded()`.

```kotlin
if (interstitialAd.isLoaded()) {
    interstitialAd.show()
} else {
    Log.d("InterstitialAd", "Interstitial ad not loaded yet.")
    // Optionally, try to load it again or wait for the current load to finish.
}
```
Interstitial ads are typically single-use. After an interstitial ad is shown and closed, you must call `loadAd()` again to request a new ad for the next opportunity. The `isLoaded()` status will become `false` after `show()` is called.

## Destroying InterstitialAd

Clean up the `InterstitialAd` instance when your Activity or Fragment is destroyed to release resources and prevent memory leaks.

```kotlin
// In your Activity
override fun onDestroy() {
    interstitialAd.destroy()
    super.onDestroy()
}

// Or in your Fragment
override fun onDestroyView() {
    interstitialAd.destroy()
    super.onDestroyView()
}
```

---

Next: [Native Ads](./NativeAds.md)
