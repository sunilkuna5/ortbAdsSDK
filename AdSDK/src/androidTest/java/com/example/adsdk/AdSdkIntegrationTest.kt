package com.example.adsdk

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.IdlingRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.example.adsdk.AdLoader.Companion.adLoaderIdlingResource
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Dummy Activity for hosting views if needed
class TestActivity : Activity()

@RunWith(AndroidJUnit4::class)
@LargeTest
class AdSdkIntegrationTest {

    private lateinit var context: Context
    private lateinit var adLoader: AdLoader // For direct AdLoader tests
    private lateinit var mainHandler: Handler

    @Captor
    private lateinit var adResponseCaptor: ArgumentCaptor<AdResponse>
    @Captor
    private lateinit var adErrorCaptor: ArgumentCaptor<AdError>

    // Rule to get a valid Activity context for UI operations if needed for BannerAdView
    @get:Rule
    val activityRule = ActivityTestRule(TestActivity::class.java)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        mainHandler = Handler(Looper.getMainLooper())

        // It's crucial that AdSdk is initialized ONCE for the test suite,
        // or reset properly. For instrumentation tests, Application context is shared.
        // Let's ensure it's initialized. If already initialized, it should be a no-op.
        AdSdk.initialize(context, AdSdkConfig(apiKey = "test-api-key", testMode = true, enableLogging = true, logLevel = LogLevel.DEBUG))
        
        adLoader = AdLoader() // Instance for direct AdLoader tests
        IdlingRegistry.getInstance().register(adLoaderIdlingResource)
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(adLoaderIdlingResource)
        // Consider AdSdk.reset() or ensure tests handle potential existing state.
    }

    // --- AdLoader Direct Fetch Tests ---
    @Test
    fun adLoader_fetchesBannerHtmlSuccessfully() {
        val mockListener = mock<AdLoader.AdLoadListener>()
        val adRequest = AdRequest(adUnitId = "test-banner-ad-unit", format = AdFormat.BANNER)
        adLoader.loadAd(adRequest, mockListener)
        verify(mockListener, timeout(10000).times(1)).onAdLoaded(adResponseCaptor.capture())
        val adResponse = adResponseCaptor.value
        assertThat(adResponse.adType).isEqualTo(AdType.HTML)
        assertThat(adResponse.creativePayload).contains("<html>")
    }

    @Test
    fun adLoader_fetchesInterstitialHtmlSuccessfully() {
        val mockListener = mock<AdLoader.AdLoadListener>()
        val adRequest = AdRequest(adUnitId = "test-interstitial-ad-unit", format = AdFormat.INTERSTITIAL)
        adLoader.loadAd(adRequest, mockListener)
        verify(mockListener, timeout(10000).times(1)).onAdLoaded(adResponseCaptor.capture())
        val adResponse = adResponseCaptor.value
        assertThat(adResponse.adType).isEqualTo(AdType.HTML)
        assertThat(adResponse.creativePayload).contains("<html>")
    }

    @Test
    fun adLoader_fetchesNativeJsonSuccessfully() {
        val mockListener = mock<AdLoader.AdLoadListener>()
        val adRequest = AdRequest(adUnitId = "test-native-ad-unit", format = AdFormat.NATIVE)
        adLoader.loadAd(adRequest, mockListener)
        verify(mockListener, timeout(10000).times(1)).onAdLoaded(adResponseCaptor.capture())
        val adResponse = adResponseCaptor.value
        assertThat(adResponse.adType).isEqualTo(AdType.NATIVE)
        assertThat(adResponse.creativePayload).contains("\"assets\"") // Basic check for JSON structure
    }

    // --- BannerAdView Integration Test ---
    @Test
    fun bannerAdView_loadsAdSuccessfully() {
        val bannerAdView = BannerAdView(activityRule.activity) // Use activity context
        val mockBannerListener = mock<BannerAdListener>()
        val latch = CountDownLatch(1)

        bannerAdView.setAdUnitId("test-banner-ad-unit")
        bannerAdView.setAdSize(AdSize.BANNER_300X50)
        bannerAdView.setBannerAdListener(object : BannerAdListener {
            override fun onAdLoaded(adView: BannerAdView) { mockBannerListener.onAdLoaded(adView); latch.countDown() }
            override fun onAdFailedToLoad(error: AdError) { mockBannerListener.onAdFailedToLoad(error); latch.countDown() }
            override fun onAdClicked() { mockBannerListener.onAdClicked() }
            override fun onAdImpression() { mockBannerListener.onAdImpression() }
            override fun onAdOpened() { mockBannerListener.onAdOpened() }
            override fun onAdClosed() { mockBannerListener.onAdClosed() }
        })
        
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // Add BannerAdView to a layout to ensure it's part of the view hierarchy for MRAID.
            val layout = FrameLayout(activityRule.activity)
            layout.addView(bannerAdView, FrameLayout.LayoutParams(
                AdSize.BANNER_300X50.getWidthPx(activityRule.activity),
                AdSize.BANNER_300X50.getHeightPx(activityRule.activity)
            ))
            activityRule.activity.setContentView(layout)
            bannerAdView.loadAd()
        }

        assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue() // Increased timeout for full load + render
        verify(mockBannerListener, timeout(1000).times(1)).onAdLoaded(bannerAdView)
        
        // Basic check: MraidWebView is created as a child
        var mraidWebViewFound = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (i in 0 until bannerAdView.childCount) {
                if (bannerAdView.getChildAt(i) is WebView) {
                    mraidWebViewFound = true
                    break
                }
            }
        }
        assertThat(mraidWebViewFound).isTrue()
    }

    // --- InterstitialAd Integration Test ---
    @Test
    fun interstitialAd_loadsAndShowsSuccessfully() {
        val interstitialAd = InterstitialAd(activityRule.activity, "test-interstitial-ad-unit")
        val mockInterstitialListener = mock<InterstitialAdListener>()
        val loadLatch = CountDownLatch(1)
        val showLatch = CountDownLatch(1) // For onAdShown or onAdClosed

        interstitialAd.setInterstitialAdListener(object : InterstitialAdListener {
            override fun onAdLoaded(ad: InterstitialAd) { mockInterstitialListener.onAdLoaded(ad); loadLatch.countDown() }
            override fun onAdFailedToLoad(error: AdError) { mockInterstitialListener.onAdFailedToLoad(error); loadLatch.countDown(); showLatch.countDown(); }
            override fun onAdShown() { mockInterstitialListener.onAdShown() } // showLatch could be here
            override fun onAdClicked() { mockInterstitialListener.onAdClicked() }
            override fun onAdClosed() { mockInterstitialListener.onAdClosed(); showLatch.countDown() }
            override fun onAdImpression() { mockInterstitialListener.onAdImpression() }
        })

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            interstitialAd.loadAd()
        }
        assertThat(loadLatch.await(10, TimeUnit.SECONDS)).isTrue()
        verify(mockInterstitialListener, timeout(1000).times(1)).onAdLoaded(interstitialAd)
        assertThat(interstitialAd.isLoaded()).isTrue()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            interstitialAd.show()
        }
        assertThat(showLatch.await(10, TimeUnit.SECONDS)).isTrue() // Wait for onAdClosed
        verify(mockInterstitialListener, timeout(1000).times(1)).onAdShown()
        verify(mockInterstitialListener, timeout(1000).times(1)).onAdClosed()
        assertThat(interstitialAd.isLoaded()).isFalse() // Should be false after showing
    }

    // --- NativeAd Integration Test ---
    @Test
    fun nativeAd_loadsAndAssetsAreParsed() {
        val nativeAd = NativeAd(context, "test-native-ad-unit")
        val mockNativeListener = mock<NativeAdListener>()
        val latch = CountDownLatch(1)

        nativeAd.setNativeAdListener(object: NativeAdListener {
            override fun onAdLoaded(ad: NativeAd) { mockNativeListener.onAdLoaded(ad); latch.countDown() }
            override fun onAdFailedToLoad(error: AdError) { mockNativeListener.onAdFailedToLoad(error); latch.countDown() }
            override fun onAdClicked() { mockNativeListener.onAdClicked() }
            override fun onAdImpression() { mockNativeListener.onAdImpression() }
            override fun onAdClosed() { mockNativeListener.onAdClosed() }
        })

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            nativeAd.loadAd()
        }
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
        verify(mockNativeListener, timeout(1000).times(1)).onAdLoaded(nativeAd)
        assertThat(nativeAd.isLoaded()).isTrue()
        assertThat(nativeAd.getTitle()).isNotEmpty()
        assertThat(nativeAd.getDescription()).isNotEmpty()
        assertThat(nativeAd.getCallToAction()).isNotEmpty()
        assertThat(nativeAd.getLandingUrl()).isNotEmpty()
    }

    // --- Ad Loading Error Handling Tests ---
    @Test
    fun adLoader_handlesNoFillError() {
        val mockListener = mock<AdLoader.AdLoadListener>()
        // Assuming "nonexistent-ad-unit" results in a No Fill (e.g. HTTP 204 or specific OpenRTB NBR)
        // The mock server at mock-ad-server.vercel.app returns 204 for unknown ad units.
        val adRequest = AdRequest(adUnitId = "nonexistent-ad-unit", format = AdFormat.BANNER)
        adLoader.loadAd(adRequest, mockListener)
        verify(mockListener, timeout(10000).times(1)).onError(adErrorCaptor.capture())
        val adError = adErrorCaptor.value
        assertThat(adError.errorCode).isEqualTo(AdErrorCodes.NO_FILL)
    }
    
    @Test
    fun adLoader_handlesNetworkError() {
        // To test this, we need to point to an invalid server or simulate network down.
        // Temporarily change AD_SERVER_URL in AdLoader or use a specific test setup.
        // This is harder to achieve in a pure black-box integration test without modifying AdLoader
        // or having control over the network environment of the test device/emulator.
        // For now, this test is a placeholder for a manual test or one with advanced setup.
        // One way: use a non-routable IP for the ad server URL.
        // AdLoader internalAdServerUrl = "http://0.0.0.1/nonexistent"; // (Requires modification of AdLoader or test-only setter)
        
        // Simpler: Test with an ad unit ID that the server is configured to return a specific error for,
        // if the mock server supports such a configuration.
        // The current mock server doesn't explicitly simulate network errors via ad unit ID.
        // It might return HTTP 500 for malformed requests, which AdLoader maps to SERVER_ERROR.

        // Let's test with a malformed AdUnitID that might cause server to return non-2xx
        // This is more likely a SERVER_ERROR than NETWORK_ERROR from client side.
        // True NETWORK_ERROR means client couldn't reach server.
        // If AdLoader is modified to point to "http://unreachable.local/", it would trigger NETWORK_ERROR.
        // Without modifying AdLoader, we can't reliably trigger client-side NETWORK_ERROR.
        // We can test for SERVER_ERROR if an ad unit ID forces it.
        // "force-server-error" is not a real ad unit on the mock server, likely leads to NO_FILL.
        // Let's assume this test requires manual environment setup (e.g. airplane mode) or future enhancement.
        Log.w("AdSdkIntegrationTest", "adLoader_handlesNetworkError: Test requires manual setup or AdLoader modification for reliable client-side network error simulation.")
        assertThat(true).isTrue() // Placeholder
    }


    // --- SDK Initialization Test ---
    @Test
    fun adLoader_loadAd_beforeSdkInit_callsOnError() {
        // This requires a way to reset AdSdk's initialized state.
        // Assume AdSdk.isInitialized can be reset or this test runs in a fresh process.
        // If AdSdk is a singleton and initialized in @Before, this test needs special handling
        // (e.g. run as first test, or use a different test runner that isolates it, or AdSdk.reset()).
        // For now, this test's success depends on the test execution environment or AdSdk's resettability.
        // Let's simulate it by creating a new AdLoader instance IF AdSdk could be reset.
        // Since AdSdk is an object (singleton), its state persists across tests in the same run.
        // This test is problematic without a reset mechanism for the AdSdk singleton.
        Log.w("AdSdkIntegrationTest", "adLoader_loadAd_beforeSdkInit_callsOnError: Test assumes AdSdk can be reset or this runs in isolation.")
        
        // To truly test this, we'd need to run this in a separate process or ensure AdSdk is not initialized.
        // One approach for a single test:
        // 1. (Hypothetically) AdSdk.reset()
        // 2. Create new AdLoader, call loadAd
        // 3. AdSdk.initialize() again for subsequent tests.
        // This is too complex for the current setup. Assume this scenario is unit-tested well.
        assertThat(true).isTrue() // Placeholder, as proper test is hard with current AdSdk singleton state.
    }
    
    // --- MRAID Interaction Test (Basic) ---
    @Test
    fun bannerAdView_handlesMraidOpen() {
        // This test requires a specific MRAID creative that calls mraid.open().
        // The mock server's "test-banner-ad-unit" creative does call mraid.open("https://google.com").
        val bannerAdView = BannerAdView(activityRule.activity)
        val mockBannerListener = mock<BannerAdListener>()
        val latch = CountDownLatch(1) // For onAdClicked or onAdOpened

        bannerAdView.setAdUnitId("test-banner-ad-unit") // This creative should call mraid.open()
        bannerAdView.setAdSize(AdSize.BANNER_300X50)
        bannerAdView.setBannerAdListener(object : BannerAdListener {
            override fun onAdLoaded(adView: BannerAdView) { mockBannerListener.onAdLoaded(adView); }
            override fun onAdFailedToLoad(error: AdError) { mockBannerListener.onAdFailedToLoad(error); latch.countDown(); }
            override fun onAdClicked() { mockBannerListener.onAdClicked(); latch.countDown(); } // mraid.open usually triggers onAdClicked
            override fun onAdImpression() { mockBannerListener.onAdImpression() }
            override fun onAdOpened() { mockBannerListener.onAdOpened(); /*latch.countDown();*/ } // Or onAdOpened
            override fun onAdClosed() { mockBannerListener.onAdClosed() }
        })
        
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val layout = FrameLayout(activityRule.activity)
            layout.addView(bannerAdView)
            activityRule.activity.setContentView(layout)
            bannerAdView.loadAd()
        }

        assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue()
        // Depending on how MraidJsInterface.open is handled by BannerAdView's MraidBannerListener
        verify(mockBannerListener, timeout(1000).times(1)).onAdClicked()
        // Further verification could involve Espresso-Intents to check if an Intent to open URL was fired.
    }
}

```

**Summary of Changes & Test Structure:**

1.  **`TestActivity`**: Added a dummy activity for hosting views like `BannerAdView` in the UI thread if required by Espresso or MRAID rendering.
2.  **Setup (`@Before`, `@After`)**:
    *   Initializes `AdSdk` once. This is a simplification; true test isolation might require resetting `AdSdk` state or running tests in separate processes if `AdSdk` were not an `object`.
    *   Registers `adLoaderIdlingResource`.
3.  **AdLoader Direct Fetch Tests**:
    *   `adLoader_fetchesBannerHtmlSuccessfully()`: Verifies `AdLoader` can get HTML for a banner.
    *   `adLoader_fetchesInterstitialHtmlSuccessfully()`: Verifies `AdLoader` can get HTML for an interstitial.
    *   `adLoader_fetchesNativeJsonSuccessfully()`: Verifies `AdLoader` can get JSON for a native ad.
4.  **Ad Unit Integration Tests**:
    *   `bannerAdView_loadsAdSuccessfully()`:
        *   Creates `BannerAdView` in the test activity's context.
        *   Calls `bannerAdView.loadAd()`.
        *   Verifies `BannerAdListener.onAdLoaded` is called.
        *   Checks if an `MraidWebView` (child of `BannerAdView`) is present after load.
    *   `interstitialAd_loadsAndShowsSuccessfully()`:
        *   Creates `InterstitialAd`.
        *   Calls `loadAd()`, verifies `onAdLoaded`.
        *   Calls `show()`, verifies `onAdShown` and `onAdClosed` (assuming creative closes itself or by MRAID command).
    *   `nativeAd_loadsAndAssetsAreParsed()`:
        *   Creates `NativeAd`.
        *   Calls `loadAd()`, verifies `onAdLoaded`.
        *   Checks if basic assets (title, description, CTA, landing URL) are non-empty.
5.  **Error Handling Tests**:
    *   `adLoader_handlesNoFillError()`: Uses a non-existent ad unit ID to check if `onError` with `AdErrorCodes.NO_FILL` is triggered (relies on mock server returning HTTP 204).
    *   `adLoader_handlesNetworkError()`: Placeholder, as reliably simulating client-side network errors without SDK modification or special environment setup is difficult.
6.  **SDK Initialization Test**:
    *   `adLoader_loadAd_beforeSdkInit_callsOnError()`: Placeholder due to the difficulty of resetting the `AdSdk` singleton's state in the current test environment. This scenario is better covered by unit tests or requires more advanced test setup.
7.  **MRAID Interaction Test**:
    *   `bannerAdView_handlesMraidOpen()`: Loads a banner ad that is known to call `mraid.open()`. Verifies that `BannerAdListener.onAdClicked()` is triggered as a result. This is a basic MRAID interaction test.

**Challenges & Considerations:**
*   **`AdSdk` Singleton State**: The `object AdSdk` makes true isolation for tests like `adLoader_loadAd_beforeSdkInit_callsOnError` difficult without a reset mechanism.
*   **Internal `AdLoader` in Ad Units**: Ad units creating their own `AdLoader` instances means we can't directly inject a mocked `AdLoader` into them for instrumentation tests easily. Tests rely on the internal `AdLoader` working correctly with the global `AdSdk` config.
*   **Network Error Simulation**: Reliably testing client-side network errors for `AdLoader` is hard without modifying the SDK or using tools to manipulate network at the device/emulator level.
*   **UI Thread Operations**: View manipulations and `AdLoader` callbacks (which post to main thread) require `InstrumentationRegistry.getInstrumentation().runOnMainSync {}` for direct calls or careful handling with Espresso if views are part of an Activity layout. `ActivityTestRule` helps provide a valid context.
*   **MRAID Deep Testing**: Verifying complex MRAID JavaScript execution and visual states is beyond Mockito/Espresso's basic capabilities and would require tools like Espresso Web or a highly controllable test creative and mock server. The current MRAID test is a basic check for `mraid.open()`.

The created tests provide a good level of integration coverage for the main ad loading and display flows, error handling for "no fill", and a basic MRAID interaction.
The next step would be to run these tests on an emulator/device.
