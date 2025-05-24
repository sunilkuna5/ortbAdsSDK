# Native Ads Integration Guide

Native ads are a component-based ad format that gives you the freedom to customize the way ad assets like headlines, images, and calls to action are presented in your app. By formatting ads that match your app's design, you can provide a less disruptive user experience and potentially increase engagement.

## Overview

The AdSDK provides a `NativeAd` class to load native ad assets. It's your responsibility as the publisher to render these assets using your app's UI components. The SDK handles fetching the assets, tracking impressions, and processing clicks.

## Creating a NativeAd

Instantiate `NativeAd` by providing a `Context` and your ad unit ID.

```kotlin
// In your Activity or Fragment
lateinit var nativeAd: NativeAd

// In onCreate or a suitable initialization point
nativeAd = NativeAd(this, "YOUR_NATIVE_AD_UNIT_ID")
// Or: nativeAd = NativeAd(this)
// nativeAd.adUnitId = "YOUR_NATIVE_AD_UNIT_ID" // If adUnitId is made public and mutable
```

## Listening to Ad Events

Set a `NativeAdListener` to be notified of ad lifecycle events.

```kotlin
nativeAd.setNativeAdListener(object : NativeAdListener {
    override fun onAdLoaded(ad: NativeAd) {
        // Called when native ad assets are successfully loaded.
        // You can now access assets via ad.getAssets() or specific getters like ad.getTitle().
        Log.i("NativeAd", "Ad loaded successfully!")
        Toast.makeText(this@MainActivity, "Native Ad Loaded", Toast.LENGTH_SHORT).show()
        
        // Example: Render the ad immediately
        // displayNativeAd(ad) 
    }

    override fun onAdFailedToLoad(error: AdError) {
        // Called when an ad request fails.
        Log.e("NativeAd", "Ad failed to load: ${error.errorCode} - ${error.errorMessage}")
    }

    override fun onAdClicked() {
        // Called when a registered clickable view in the native ad is clicked.
        Log.i("NativeAd", "Ad clicked!")
    }

    override fun onAdImpression() {
        // Called when an impression is recorded for the native ad.
        Log.i("NativeAd", "Ad impression recorded.")
    }

    override fun onAdClosed() {
        // This callback is less common for basic native ads unless they present a modal/full-screen view.
        // The AdSDK's current NativeAd doesn't manage such views directly.
        Log.i("NativeAd", "Ad closed (if applicable).")
    }
})
```

## Loading an Ad

To request native ad assets, call the `loadAd()` method.

```kotlin
Log.d("NativeAd", "Requesting native ad load...")
nativeAd.loadAd()
```
It's good practice to load native ads in advance of when you intend to display them.

## Accessing Ad Assets

Once `onAdLoaded()` is called, the `NativeAd` object will contain the fetched assets in a `NativeAdAssets` object. You can access this object or use convenient getter methods on the `NativeAd` instance.

```kotlin
val assets: NativeAdAssets? = nativeAd.getAssets()

val title: String? = nativeAd.getTitle() 
// Or: assets?.title?.text

val description: String? = nativeAd.getDescription()
// Or: assets?.description?.value

val callToAction: String? = nativeAd.getCallToAction()
// Or: assets?.callToAction?.value

val mainImageUrl: String? = nativeAd.getMainImageUrl()
// Or: assets?.mainImage?.url

val iconUrl: String? = nativeAd.getIconUrl()
// Or: assets?.iconImage?.url

val sponsoredBy: String? = nativeAd.getSponsoredBy()
// Or: assets?.sponsoredBy?.value

val rating: String? = nativeAd.getRating() // Assuming rating is a string, e.g. "4.5"
// Or: assets?.rating?.value

val videoVastTag: String? = nativeAd.getVideoVastTag() // VAST XML for video asset
// Or: assets?.video?.vastTag

// You can also iterate through all available assets:
// assets?.allAssets?.forEach { (assetId, nativeAsset) ->
//     Log.d("NativeAd", "Asset ID: $assetId, Type: ${nativeAsset.type}")
//     when (nativeAsset) {
//         is NativeAsset.Title -> Log.d("NativeAd", "Title: ${nativeAsset.text}")
//         is NativeAsset.Image -> Log.d("NativeAd", "Image URL: ${nativeAsset.url}")
//         // ... and so on for other asset types
//     }
// }
```
Always check for null, as not all assets are guaranteed to be returned by the ad server.

## Rendering the Ad

You are responsible for creating the UI that displays the native ad assets. This typically involves populating standard Android UI components like `TextView`, `ImageView`, and `Button`.

**Example (Conceptual - within your Activity/Fragment):**

```kotlin
// Assume you have these views in your layout (e.g., R.id.nativeAdTitleView, etc.)
// private fun displayNativeAd(nativeAd: NativeAd) {
//     val titleView: TextView = findViewById(R.id.nativeAdTitleView)
//     val descriptionView: TextView = findViewById(R.id.nativeAdDescriptionView)
//     val ctaButton: Button = findViewById(R.id.nativeAdCtaButton)
//     val mainImageView: ImageView = findViewById(R.id.nativeAdMainImageView)
//     val iconImageView: ImageView = findViewById(R.id.nativeAdIconView)
//     // ... other views for sponsored by, rating, etc.

//     titleView.text = nativeAd.getTitle()
//     descriptionView.text = nativeAd.getDescription()
//     ctaButton.text = nativeAd.getCallToAction()
//     sponsoredByView.text = nativeAd.getSponsoredBy()
    
//     // For images, use an image loading library like Glide or Picasso in a real app
//     nativeAd.getMainImageUrl()?.let { url ->
//         // Glide.with(this).load(url).into(mainImageView)
//         mainImageView.visibility = View.VISIBLE
//     } ?: run {
//         mainImageView.visibility = View.GONE
//     }

//     nativeAd.getIconUrl()?.let { url ->
//         // Glide.with(this).load(url).into(iconImageView)
//         iconImageView.visibility = View.VISIBLE
//     } ?: run {
//         iconImageView.visibility = View.GONE
//     }
    
//     // Make the ad container visible
//     val nativeAdContainer: ViewGroup = findViewById(R.id.nativeAdContainerView)
//     nativeAdContainer.visibility = View.VISIBLE

//     // IMPORTANT: Register views for interaction (see next section)
//     val clickableViews = listOf<View>(ctaButton, titleView, mainImageView) // Define which views trigger a click
//     nativeAd.registerViewForInteraction(nativeAdContainer, clickableViews)
// }
```
Refer to `AdSDKSample/src/main/java/com/example/adsdksample/MainActivity.kt` for a more concrete example.

## Registering Views for Interaction

For the SDK to correctly track impressions and handle clicks, you **must** register the views that constitute your native ad UI.

-   **Ad View Container:** The outermost parent view that contains all your native ad asset views.
-   **Clickable Views:** A list of specific views within the ad container that should be treated as clickable for ad interaction (e.g., a call-to-action button, title, or the main image).

```kotlin
// In your displayNativeAd method, after populating views:
val adViewContainer: ViewGroup = findViewById(R.id.nativeAdContainer) // Your main native ad layout
val ctaButton: Button = findViewById(R.id.nativeAdCtaButton)
val titleTextView: TextView = findViewById(R.id.nativeAdTitle)
// ... other views ...

val clickableViews = mutableListOf<View>()
clickableViews.add(ctaButton)
clickableViews.add(titleTextView)
// Add other views that should trigger a click on the ad

// Optionally, specify non-clickable views if they overlap interactive elements
// val nonClickableViews = listOf<View>(someOverlayView)

nativeAd.registerViewForInteraction(
    adView = adViewContainer,
    clickableViews = clickableViews
    // nonClickableViews = nonClickableViews // If needed
)
```
Failure to call `registerViewForInteraction` will prevent clicks from being processed and impressions from being accurately tracked by the SDK.

## Destroying NativeAd

Clean up the `NativeAd` instance when it's no longer needed (e.g., when the hosting Activity/Fragment is destroyed).

```kotlin
// In your Activity
override fun onDestroy() {
    nativeAd.destroy()
    super.onDestroy()
}

// Or in your Fragment
override fun onDestroyView() {
    nativeAd.destroy()
    super.onDestroyView()
}
```

---

Next: [MRAID Support](./MRAID.md)
