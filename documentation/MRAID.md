# MRAID Support in AdSDK

MRAID (Mobile Rich Media Ad Interface Definitions) is a standard set by the IAB (Interactive Advertising Bureau) that enables rich media ad creatives to interact with mobile apps in a standardized way. This allows creatives to perform actions like expanding, resizing, accessing device features (with permission), and playing video.

## Overview

The AdSDK includes robust support for MRAID, specifically targeting **MRAID 2.0** and **MRAID 3.0** compatible creatives. This support is primarily handled by the internal `MraidWebView` component, which is used to render HTML-based banner and interstitial ads.

## Key MRAID Features Supported

-   **Basic MRAID Events:** `ready`, `error`, `stateChange`, `viewableChange`, `sizeChange`.
-   **MRAID Actions:**
    -   `mraid.open(url)`: Opening URLs in an external browser.
    -   `mraid.close()`: Closing the ad (especially for expanded banners or interstitials).
    -   `mraid.expand(url?)`: Expanding an ad to fill more screen space (primarily for banners).
    -   `mraid.resize()`: Resizing an ad to custom dimensions (primarily for banners).
    -   `mraid.playVideo(url)`: Requesting the app to play a video.
    -   `mraid.storePicture(url)`: Requesting the app to save a picture to the device gallery (requires host app to handle permission and download).
    -   `mraid.createCalendarEvent(eventJSON)`: Requesting the app to create a calendar event (requires host app to handle permission and event creation).
    -   `mraid.useCustomClose(boolean)`: Allowing the creative to provide its own close button.
    -   `mraid.setOrientationProperties(properties)`: Influencing screen orientation (requires host app/activity to handle actual changes).
-   **MRAID 3.0 Features (Partial/Basic Support):**
    -   `mraid.getLocation()`: Creatives can request location. The SDK provides a hook, but the host application must grant location permissions and provide the location data to the SDK.
    -   `mraid.getAudibility()` / `audioVolumeChange` event: The SDK can report audibility status to the creative.
    -   Exposure Change events: Basic infrastructure exists, but full nuanced reporting might depend on host app view hierarchy complexity.

## Publisher Integration

For publishers using `BannerAdView`, `InterstitialAd`, or `NativeAd` (if it renders an MRAID-enabled JS tracker), MRAID support is largely **transparent**. You do not need to write MRAID-specific code to handle these creatives.

-   The internal `MraidWebView` interprets MRAID commands from the creative.
-   These commands are translated into calls on the respective `MraidListener` interface.
-   The ad unit classes (`BannerAdView`, `InterstitialAd`) implement `MraidListener` internally and translate these MRAID events into the appropriate `BannerAdListener`, `InterstitialAdListener`, etc., callbacks. For example:
    -   `mraid.open()` typically triggers `onAdClicked()`.
    -   `mraid.expand()` on a banner triggers `BannerAdListener.onAdOpened()`.
    -   `mraid.close()` on an expanded banner or interstitial triggers `onAdClosed()`.

**Permissions for MRAID Features:**

Some MRAID features (like `storePicture`, `createCalendarEvent`, `getLocation`) require specific Android permissions. The AdSDK **does not** declare these permissions in its own manifest.

-   If you expect to serve MRAID ads that use these features, your application **must** declare the necessary permissions in its `AndroidManifest.xml` (e.g., `android.permission.WRITE_EXTERNAL_STORAGE`, `android.permission.ACCESS_FINE_LOCATION`, `android.permission.WRITE_CALENDAR`).
-   Your application is also responsible for handling runtime permission requests (for Android 6.0+).
-   The AdSDK will generally fire an MRAID error event back to the creative if it attempts an action for which the host app does not have permission or has not implemented a handler (e.g., the `MraidListener` callbacks for `onMraidStorePicture` in the ad unit classes currently fire an error if the host doesn't explicitly handle it).

## Testing MRAID Ads

When testing, use MRAID test creatives that explicitly call various MRAID methods to ensure they behave as expected within the AdSDK's views. The `testMode = true` in `AdSdkConfig` might serve MRAID test ads if your ad server is configured for it.

The mock ad server (`https://mock-ad-server.vercel.app/api/openrtb`) used by the sample app serves basic MRAID creatives for banner (`test-banner-ad-unit`) and interstitial (`test-interstitial-ad-unit`) placements.

---

Next: [Testing Guidelines](./Testing.md)
