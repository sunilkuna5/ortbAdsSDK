# AdSDK Error Codes Reference

This page provides a reference for common error codes that the AdSDK might return via the `AdError` object in listener callbacks (e.g., `onAdFailedToLoad`). Understanding these codes can help you diagnose issues during ad integration and operation.

The error codes are defined in the `com.example.adsdk.AdErrorCodes` object.

## General SDK Errors

-   **`SDK_NOT_INITIALIZED` (1001):**
    -   **Explanation:** An attempt was made to use an SDK feature (like loading an ad) before `AdSdk.initialize()` was called.
    -   **Solution:** Ensure `AdSdk.initialize()` is called once, typically in your `Application` class's `onCreate()` method, before any ad operations.

-   **`INTERNAL_ERROR` (1006):**
    -   **Explanation:** A generic internal error occurred within the SDK that wasn't covered by a more specific error code. This might indicate an unexpected state or unhandled exception within the SDK.
    -   **Solution:** Check SDK logs for more details. If persistent, report to SDK provider with logs and reproduction steps.

-   **`INVALID_REQUEST` (1005):**
    -   **Explanation:** The ad request parameters were invalid or missing. For example, loading an ad without setting an `adUnitId`.
    -   **Solution:** Verify that all required parameters (especially `adUnitId`) are correctly set before calling `loadAd()`.

-   **`CONFIGURATION_ERROR` (1009):**
    -   **Explanation:** There's a problem with the SDK's configuration, or a feature is used that requires a configuration not yet set.
    -   **Solution:** Review the `AdSdkConfig` used for initialization and ensure all necessary fields are correctly populated.

## Network & Server Errors

-   **`NETWORK_ERROR` (1002):**
    -   **Explanation:** A problem occurred with network connectivity, preventing the SDK from communicating with the ad server (e.g., no internet connection, DNS resolution failure).
    -   **Solution:** Check the device's network connection. This error is often transient.

-   **`SERVER_ERROR` (1003):**
    -   **Explanation:** The ad server was reached but responded with an error (e.g., HTTP 5xx status code) or an invalid/unexpected response format. This can also include errors parsing the server's response.
    -   **Solution:** This usually indicates an issue on the ad server side. Check SDK logs for specific server messages if available. Retrying the request later might resolve transient server issues.

-   **`TIMEOUT_ERROR` (1010):**
    -   **Explanation:** The request to the ad server timed out before a response was received.
    -   **Solution:** Network latency or server unresponsiveness can cause this. Retrying might help.

-   **`NO_FILL` (1004):**
    -   **Explanation:** The ad server successfully processed the ad request but had no suitable ad to return for the given ad unit and targeting parameters. This is a common scenario in live environments and does not necessarily indicate an error with your integration.
    -   **Solution:** This is expected behavior if no ad inventory is available. You might try adjusting targeting or checking ad unit configuration in your ad server dashboard. Consider it as "no ad available" rather than an error.

## Ad Content & Display Errors

-   **`CREATIVE_ERROR` (1008):**
    -   **Explanation:** An error occurred within the ad creative itself during its execution (e.g., a JavaScript error in an MRAID ad, invalid MRAID command).
    -   **Solution:** This usually indicates an issue with the ad creative being served. Detailed MRAID errors might be logged if `LogLevel` is `DEBUG` or `VERBOSE`.

-   **`AD_NOT_READY` (1007):**
    -   **Explanation:** An attempt was made to show an ad (e.g., `interstitialAd.show()`) before it was successfully loaded and ready for display.
    -   **Solution:** Ensure you call `show()` only after the `onAdLoaded()` callback has been received. Use the `isLoaded()` method to check an ad's readiness.

-   **`UNSUPPORTED_AD_TYPE` (1011):**
    -   **Explanation:** The ad type returned by the server is not supported by the ad unit that requested it (e.g., a native ad payload was returned for a `BannerAdView`).
    -   **Solution:** Check your ad unit configuration on the ad server to ensure it's set up to serve compatible ad types for your integration.

-   **`RENDER_ERROR` (1012):**
    -   **Explanation:** An error occurred while the SDK was attempting to render or display the ad creative (e.g., an issue setting up the `MraidWebView` or displaying the interstitial dialog).
    -   **Solution:** Check SDK logs for more specific details. This could be an internal SDK issue or a problem with the device environment.

## Permission & Device Feature Errors

-   **`PERMISSION_DENIED` (1013):**
    -   **Explanation:** An ad creative attempted to use a feature that requires a permission not granted by the application (e.g., MRAID `storePicture` without `WRITE_EXTERNAL_STORAGE`).
    -   **Solution:** If you expect ads to use such features, declare the necessary permissions in your app's manifest and handle runtime permission requests.

-   **`FEATURE_NOT_SUPPORTED` (1014):**
    -   **Explanation:** An ad creative attempted to use a device feature that is not supported by the current device (e.g., an MRAID feature not available on the device's WebKit version).
    -   **Solution:** This is usually dependent on the creative and device capabilities.

## Specific MRAID Errors (Examples)

These might be reported as part of a `CREATIVE_ERROR` or through MRAID-specific error events.

-   **`MRAID_INVALID_STATE_TRANSITION` (2001):**
    -   **Explanation:** An MRAID command was called when the MRAID view was not in an appropriate state (e.g., `mraid.expand()` called when the ad was already expanded or still loading).
-   **`MRAID_ACTION_NOT_SUPPORTED` (2002):**
    -   **Explanation:** An MRAID command is not supported for the current ad type or context (e.g., `mraid.resize()` called on an interstitial ad).

---

Always check the `errorMessage` property of the `AdError` object and the SDK logs (with an appropriate `LogLevel`) for more context on any errors encountered.
