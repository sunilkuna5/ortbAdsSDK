# Testing Guidelines for AdSDK

Effective testing is crucial for ensuring a smooth ad integration. The AdSDK provides features to aid in testing your ad implementations.

## Enabling Test Mode

During development and testing, it is highly recommended to enable the SDK's test mode. This mode typically serves test ads that are always available and ensures that no real revenue or campaign data is affected.

To enable test mode, set the `testMode` flag to `true` in your `AdSdkConfig` during SDK initialization:

```kotlin
// YourApplication.kt
import com.example.adsdk.AdSdk
import com.example.adsdk.AdSdkConfig
import com.example.adsdk.LogLevel

class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val sdkConfig = AdSdkConfig(
            apiKey = "YOUR_API_KEY_HERE", 
            testMode = true, // Enable test mode for development/testing
            logLevel = LogLevel.DEBUG // Use a verbose log level for testing
        )
        AdSdk.initialize(this, sdkConfig)
    }
}
```

**Important:** Always ensure `testMode` is set to `false` when building your application for production release. A common practice is to tie this to your build type: `testMode = BuildConfig.DEBUG`.

## Test Ad Unit IDs

When `testMode` is enabled, the SDK (or the ad server it communicates with) should ideally serve test ads. For this AdSDK, when using the provided mock ad server (`https://mock-ad-server.vercel.app/api/openrtb`), the following ad unit IDs are configured to return test creatives:

-   **Banner Ads:**
    -   Ad Unit ID: `test-banner-ad-unit`
    -   Expected Creative: A simple MRAID HTML banner that may include text like "MRAID Banner Ad Ready!" and might attempt to auto-open a URL for click testing.

-   **Interstitial Ads:**
    -   Ad Unit ID: `test-interstitial-ad-unit`
    -   Expected Creative: A full-screen MRAID HTML creative that may include text like "MRAID Interstitial Ad" and a close button.

-   **Native Ads:**
    -   Ad Unit ID: `test-native-ad-unit`
    -   Expected Creative: An OpenRTB Native JSON payload containing sample assets (title, description, image URLs, CTA text, sponsored by text). The mock server provides specific values for these assets.

Using these ad unit IDs in conjunction with `testMode = true` (or if the mock server is directly hit without an API key that enforces live mode) will ensure you receive predictable test ads, making it easier to verify your UI and integration logic.

## Verifying Integration

-   **Listeners:** Implement all relevant listener callbacks (`BannerAdListener`, `InterstitialAdListener`, `NativeAdListener`) and log their events or display Toasts to confirm that ad lifecycle events are firing as expected (e.g., `onAdLoaded`, `onAdFailedToLoad`, `onAdClicked`, `onAdImpression`, `onAdClosed`).
-   **UI Display:**
    -   For banner ads, check that the `BannerAdView` displays the creative correctly and matches the specified `AdSize`.
    -   For interstitial ads, ensure the ad covers the full screen and can be dismissed.
    -   For native ads, verify that all fetched assets are correctly mapped and rendered in your custom native UI layout.
-   **Interaction:** Test click-through behavior. For MRAID ads, test any interactive features they might have (e.g., expand, resize, close button functionality).
-   **Log Output:** Utilize `LogLevel.DEBUG` or `LogLevel.VERBOSE` during testing to get detailed logs from the SDK, which can help diagnose issues.

## Espresso UI Testing

For automated UI testing, refer to the `AdSDKSample` application, which includes Espresso tests for banner, interstitial, and native ad integrations. Key practices include:
-   Using `IdlingResource` (like `AdLoader.adLoaderIdlingResource` and activity-managed resources in the sample) to synchronize tests with asynchronous ad loading and display operations.
-   Verifying UI elements are displayed and contain expected content.
-   Simulating user interactions like clicks.

---

Next: [Error Codes](./ErrorCodes.md)
