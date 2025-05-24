# Getting Started with AdSDK

This guide will walk you through the initial setup of the AdSDK in your Android application.

## Prerequisites

-   **Android API Level:** The AdSDK requires a minimum Android API level of 21 (Android 5.0 Lollipop).
-   **Android Studio:** Latest stable version recommended.
-   **Kotlin:** The SDK is written in Kotlin, and examples will be provided in Kotlin. Java compatibility is supported.

## Installation

1.  **Add AdSDK Dependency:**
    The AdSDK is structured as a local library module (`:AdSDK`) within this project. To include it in your application module (e.g., `:app` or `:AdSDKSample`), ensure your `settings.gradle` file includes the AdSDK module:

    ```gradle
    // settings.gradle
    include ':AdSDK'
    include ':app' // Your application module
    ```

    Then, add the AdSDK module as a dependency in your application's `build.gradle` file:

    ```gradle
    // app/build.gradle
    dependencies {
        implementation project(":AdSDK")
        // Other app dependencies
    }
    ```

    If the AdSDK were published to a Maven repository, the dependency would look like this (replace with actual group, name, and version):
    ```gradle
    // Example for a published SDK (not applicable for this local setup)
    // implementation 'com.example:adsdk:1.0.0'
    ```

2.  **Permissions:**
    The AdSDK requires the `INTERNET` permission to fetch ads. Add this permission to your application's `AndroidManifest.xml` file if it's not already present:

    ```xml
    <!-- AndroidManifest.xml -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        ...>
        ...
    </application>
    ```
    Certain MRAID ad creatives might request additional permissions (like `WRITE_EXTERNAL_STORAGE`, `ACCESS_FINE_LOCATION`, `WRITE_CALENDAR`). The SDK itself does not declare these, but if you expect such creatives, you might need to include them.

## SDK Initialization

The AdSDK must be initialized before any ad requests can be made. This is typically done in your `Application` class's `onCreate()` method.

1.  **Create `AdSdkConfig`:**
    The SDK is initialized using an `AdSdkConfig` object. This allows you to configure various settings:

    -   `apiKey: String`: Your unique API key for the AdSDK.
    -   `testMode: Boolean` (optional, default: `false`): Set to `true` during development and testing to receive test ads and enable debug features. **Always set to `false` for production builds.**
    -   `logLevel: LogLevel` (optional, default: `LogLevel.INFO`): Controls the verbosity of SDK logs. Available levels: `NONE`, `ERROR`, `WARNING`, `INFO`, `DEBUG`, `VERBOSE`.
    -   `publisherId: String?` (optional, default: `null`): Your publisher identifier, if applicable.

2.  **Initialize the SDK:**
    Call `AdSdk.initialize(context, config)` once, preferably in your `Application` class.

    ```kotlin
    // YourApplication.kt (e.g., SampleApplication.kt)
    import android.app.Application
    import com.example.adsdk.AdSdk
    import com.example.adsdk.AdSdkConfig
    import com.example.adsdk.LogLevel

    class YourApplication : Application() {
        override fun onCreate() {
            super.onCreate()

            val sdkConfig = AdSdkConfig(
                apiKey = "YOUR_API_KEY_HERE", // Replace with your actual API key
                testMode = BuildConfig.DEBUG,    // Recommended: true for debug, false for release
                logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
            )

            AdSdk.initialize(this, sdkConfig)
        }
    }
    ```
    Remember to register `YourApplication` in your `AndroidManifest.xml`:
    ```xml
    <application
        android:name=".YourApplication"
        ...>
        ...
    </application>
    ```

## ProGuard/R8 Rules (Minification)

If you are using ProGuard or R8 for code shrinking and obfuscation in your release builds, you might need to add rules to ensure the AdSDK functions correctly. While the SDK is designed to be ProGuard-friendly, issues can sometimes arise with reflection or dynamic class loading if not configured properly.

Add the following rules to your ProGuard/R8 configuration file (typically `proguard-rules.pro`):

```proguard
# AdSDK ProGuard Rules
-keep class com.example.adsdk.** { *; }
-keep interface com.example.adsdk.** { *; }
-dontwarn com.example.adsdk.**

# Keep Moshi adapters if not using @JsonClass(generateAdapter = true) extensively or if reflection is needed
# -keep class com.squareup.moshi.* { *; }
# -keepclasseswithmembers class * { @com.squareup.moshi.Json <methods>; }
# -keepclasseswithmembers class * { @com.squareup.moshi.Json <fields>; }

# Keep OkHttp internal classes if reflection is used by any library (usually handled by OkHttp's own rules)
# -dontwarn okhttp3.**
# -keep class okhttp3.** { *; }
# -keep interface okhttp3.** { *; }
```
The provided rules for `com.example.adsdk.**` are generally a safe starting point. If you use `@JsonClass(generateAdapter = true)` for all your Moshi-serialized classes within the SDK (as done in the current implementation), specific Moshi rules might not be needed. OkHttp typically includes its own ProGuard rules. Always test your release builds thoroughly.

---

You are now ready to integrate specific ad formats! Refer to the other guides for detailed instructions:
-   [Banner Ads](./BannerAds.md)
-   [Interstitial Ads](./InterstitialAds.md)
-   [Native Ads](./NativeAds.md)
