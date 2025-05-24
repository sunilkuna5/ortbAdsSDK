package com.example.adsdk

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@ExtendWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Config.OLDEST_SDK])
class BannerAdViewTest {

    private lateinit var activityController: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var bannerAdView: BannerAdView
    private lateinit var mockAdLoader: AdLoader
    private lateinit var mockListener: BannerAdListener
    private lateinit var mockMraidWebView: MraidWebView // To verify interactions if needed

    @BeforeEach
    fun setUp() {
        activityController = Robolectric.buildActivity(Activity::class.java).create().start().resume()
        activity = activityController.get()
        
        mockAdLoader = mock()
        mockListener = mock()

        // Initialize AdSdk (required by AdLoader and BannerAdView logic)
        resetAdSdkSingleton()
        AdSdk.initialize(activity.applicationContext, AdSdkConfig(apiKey = "test-sdk-key", testMode = true))

        // Create BannerAdView programmatically for most tests
        bannerAdView = BannerAdView(activity)
        bannerAdView.setBannerAdListener(mockListener)
        bannerAdView.setAdUnitId("test-banner-ad-unit")

        // Replace internal AdLoader with mock using reflection
        val adLoaderField = BannerAdView::class.java.getDeclaredField("adLoader")
        adLoaderField.isAccessible = true
        adLoaderField.set(bannerAdView, mockAdLoader)
        
        // Mock MraidWebView creation indirectly or verify its calls if BannerAdView creates it.
        // For now, we'll verify AdLoader is called, and then simulate AdLoader's callbacks.
        // The actual MraidWebView interactions are tested in MraidWebViewTest and MraidJsInterfaceTest.
    }

    @AfterEach
    fun tearDown() {
        bannerAdView.destroy() // Ensure cleanup
        activityController.pause().stop().destroy()
        resetAdSdkSingleton()
    }

    @Nested
    inner class InitializationAndConfiguration {
        @Test
        fun `BannerAdView can be created with default AdSize`() {
            assertThat(bannerAdView.adSize).isEqualTo(AdSize.BANNER_320X50)
        }

        @Test
        fun `setAdUnitId should update adUnitId`() {
            bannerAdView.setAdUnitId("new-unit-id")
            assertThat(bannerAdView.adUnitId).isEqualTo("new-unit-id")
        }

        @Test
        fun `setAdSize should update adSize and requestLayout`() {
            val newSize = AdSize.LEADERBOARD_728X90
            // Spy on bannerAdView to verify requestLayout (Robolectric might handle this implicitly)
            val spiedView = spy(bannerAdView)
            spiedView.setAdSize(newSize)
            assertThat(spiedView.adSize).isEqualTo(newSize)
            verify(spiedView).requestLayout()
        }

        @Test
        fun `setRefreshRate should update refreshRateSeconds`() {
            bannerAdView.setRefreshRate(60)
            // Access private field via reflection or add a getter for testing (getter is cleaner)
            val refreshRateField = BannerAdView::class.java.getDeclaredField("refreshRateSeconds")
            refreshRateField.isAccessible = true
            assertThat(refreshRateField.get(bannerAdView) as Int).isEqualTo(60)
        }
    }

    @Nested
    inner class LoadAdSequence {
        private val adRequestCaptor = argumentCaptor<AdRequest>()
        private val adLoadListenerCaptor = argumentCaptor<AdLoader.AdLoadListener>()

        @Test
        fun `loadAd should call AdLoader with correct AdRequest`() {
            bannerAdView.setAdSize(AdSize.MEDIUM_RECTANGLE_300X250)
            bannerAdView.loadAd()

            verify(mockAdLoader).loadAd(adRequestCaptor.capture(), any())
            val capturedRequest = adRequestCaptor.firstValue
            assertThat(capturedRequest.adUnitId).isEqualTo("test-banner-ad-unit")
            assertThat(capturedRequest.format).isEqualTo(AdFormat.BANNER)
            assertThat(capturedRequest.widthPx).isEqualTo(AdSize.MEDIUM_RECTANGLE_300X250.getWidthPx(activity))
            assertThat(capturedRequest.heightPx).isEqualTo(AdSize.MEDIUM_RECTANGLE_300X250.getHeightPx(activity))
        }

        @Test
        fun `loadAd when AdSDK not initialized should call onAdFailedToLoad`() {
            resetAdSdkSingleton() // Ensure SDK is not initialized
            bannerAdView.loadAd()
            verify(mockListener).onAdFailedToLoad(check {
                assertThat(it.errorCode).isEqualTo(AdErrorCodes.SDK_NOT_INITIALIZED)
            })
            verifyNoInteractions(mockAdLoader)
        }

        @Test
        fun `loadAd when adUnitId is null should call onAdFailedToLoad`() {
            bannerAdView.setAdUnitId(null as String) // Cast to resolve ambiguity
            bannerAdView.loadAd()
            verify(mockListener).onAdFailedToLoad(check {
                assertThat(it.errorCode).isEqualTo(AdErrorCodes.INVALID_REQUEST)
            })
            verifyNoInteractions(mockAdLoader)
        }

        @Test
        fun `onAdLoaded callback from AdLoader should create MraidWebView and call listener`() {
            bannerAdView.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            val loaderCallback = adLoadListenerCaptor.firstValue

            val adResponse = AdResponse("<html></html>", 320, 50, AdType.HTML)
            loaderCallback.onAdLoaded(adResponse)
            ShadowLooper.runUiThreadTasks() // For posts to main handler

            assertThat(bannerAdView.childCount).isEqualTo(1) // MraidWebView added
            val mraidWebView = bannerAdView.getChildAt(0) as MraidWebView
            assertThat(mraidWebView).isNotNull
            // ShadowWebView shadowMraidWebView = shadowOf(mraidWebView);
            // assertThat(shadowMraidWebView.lastLoadedDataWithBaseURL.data).contains("<html></html>")
            
            // Verify MraidWebView properties
            val layoutParams = mraidWebView.layoutParams as FrameLayout.LayoutParams
            assertThat(layoutParams.width).isEqualTo(AdSize.BANNER.getWidthPx(activity))
            assertThat(layoutParams.height).isEqualTo(AdSize.BANNER.getHeightPx(activity))
            assertThat(mraidWebView.mraidListener).isInstanceOf(BannerAdView.MraidBannerListener::class.java)

            verify(mockListener).onAdLoaded(bannerAdView)
        }

        @Test
        fun `onAdLoaded with NATIVE adType should call onAdFailedToLoad`() {
            bannerAdView.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            val loaderCallback = adLoadListenerCaptor.firstValue

            val adResponse = AdResponse("{}", 300, 250, AdType.NATIVE) // Native payload
            loaderCallback.onAdLoaded(adResponse)
            ShadowLooper.runUiThreadTasks()

            verify(mockListener).onAdFailedToLoad(check {
                assertThat(it.errorCode).isEqualTo(AdErrorCodes.UNSUPPORTED_AD_TYPE)
            })
            assertThat(bannerAdView.childCount).isEqualTo(0) // No MraidWebView added
        }

        @Test
        fun `onError callback from AdLoader should call listener onAdFailedToLoad`() {
            bannerAdView.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            val loaderCallback = adLoadListenerCaptor.firstValue

            val error = AdError(AdErrorCodes.NO_FILL, "No ad available")
            loaderCallback.onError(error)
            ShadowLooper.runUiThreadTasks()

            verify(mockListener).onAdFailedToLoad(error)
        }
    }
    
    @Nested
    inner class RefreshLogic {
        @Test
        fun `successful ad load should schedule refresh if refreshRate positive`() {
            bannerAdView.setRefreshRate(30) // 30 seconds
            bannerAdView.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            val loaderCallback = adLoadListenerCaptor.firstValue
            loaderCallback.onAdLoaded(AdResponse("<html></html>", 320, 50, AdType.HTML))
            ShadowLooper.runUiThreadTasks()

            assertThat(ShadowLooper.getShadowMainLooper().hasScheduledTasks()).isTrue()
            ShadowLooper.getShadowMainLooper().idleFor(29_000L) // Advance just before refresh
            verify(mockAdLoader, times(1)).loadAd(any(), any()) // Still 1 call
            ShadowLooper.getShadowMainLooper().idleFor(1_000L) // Advance past refresh time
            verify(mockAdLoader, times(2)).loadAd(any(), any()) // Now 2 calls
        }

        @Test
        fun `loadAd should cancel previous refresh`() {
            bannerAdView.setRefreshRate(30)
            bannerAdView.loadAd() // First load, schedules refresh
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            loaderCallback.onAdLoaded(AdResponse("<html></html>", 320, 50, AdType.HTML))
            ShadowLooper.runUiThreadTasks()
            
            assertThat(ShadowLooper.getShadowMainLooper().hasScheduledTasks()).isTrue()
            
            bannerAdView.loadAd() // Second load before refresh fires
            // Should cancel previous and schedule new one after this load completes.
            assertThat(ShadowLooper.getShadowMainLooper().hasScheduledTasks()).isFalse() // Old one cancelled
            
            verify(mockAdLoader, times(2)).loadAd(any(), adLoadListenerCaptor.capture()) // Called for second loadAd
            val secondLoaderCallback = adLoadListenerCaptor.secondValue
            secondLoaderCallback.onAdLoaded(AdResponse("<html></html>", 320, 50, AdType.HTML))
            ShadowLooper.runUiThreadTasks()
            assertThat(ShadowLooper.getShadowMainLooper().hasScheduledTasks()).isTrue() // New one scheduled
        }

        @Test
        fun `destroy should cancel refresh`() {
            bannerAdView.setRefreshRate(30)
            bannerAdView.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            loaderCallback.onAdLoaded(AdResponse("<html></html>", 320, 50, AdType.HTML))
            ShadowLooper.runUiThreadTasks()
            
            assertThat(ShadowLooper.getShadowMainLooper().hasScheduledTasks()).isTrue()
            bannerAdView.destroy()
            assertThat(ShadowLooper.getShadowMainLooper().hasScheduledTasks()).isFalse()
        }
    }

    @Nested
    inner class LifecycleAndVisibility {
        @Test
        fun `onDetachedFromWindow should cancel refresh`() {
            bannerAdView.setRefreshRate(30)
            bannerAdView.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            loaderCallback.onAdLoaded(AdResponse("<html></html>", 320, 50, AdType.HTML))
            ShadowLooper.runUiThreadTasks() // Ensure refresh is scheduled
            
            assertThat(ShadowLooper.getShadowMainLooper().hasScheduledTasks()).isTrue()
            
            // Simulate detaching the view
            val parent = FrameLayout(activity)
            parent.addView(bannerAdView)
            parent.removeView(bannerAdView) // This triggers onDetachedFromWindow
            ShadowLooper.runUiThreadTasks() // Process any posted Runnables from onDetachedFromWindow

            assertThat(ShadowLooper.getShadowMainLooper().hasScheduledTasks()).isFalse()
        }
        
        // Test for onVisibilityChanged and onWindowFocusChanged updating MraidWebView.updateViewability
        // would require a mock MraidWebView to be injected or more complex setup.
        // For now, this interaction is implicitly tested via MraidWebView tests.
    }
    
    @Nested
    inner class MraidBannerListenerInternalClass {
        // Test the internal MraidBannerListener calls to the main BannerAdListener
        // This requires getting an instance of the internal listener.
        // Can be done by capturing it when MraidWebView is created, or reflection.

        private lateinit var mraidListener: MraidListener
        private val mockInternalMraidWebView: MraidWebView = mock() // Mock for listener calls

        @BeforeEach
        fun mraidListenerSetup() {
            // Simulate onAdLoaded to create the MraidWebView and its listener
            bannerAdView.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            val loaderCallback = adLoadListenerCaptor.firstValue
            val adResponse = AdResponse("<html></html>", 320, 50, AdType.HTML)
            loaderCallback.onAdLoaded(adResponse)
            ShadowLooper.runUiThreadTasks()
            
            val actualMraidWebView = bannerAdView.getChildAt(0) as MraidWebView
            mraidListener = actualMraidWebView.mraidListener!! // Assumes listener is set
        }

        @Test
        fun `MraidBannerListener onMraidReady should call onAdImpression`() {
            mraidListener.onMraidReady(mockInternalMraidWebView)
            verify(mockListener).onAdImpression()
        }

        @Test
        fun `MraidBannerListener onMraidOpen should call onAdClicked`() {
            mraidListener.onMraidOpen(mockInternalMraidWebView, "http://example.com")
            verify(mockListener).onAdClicked()
            // Also verify intent was started (Robolectric ShadowActivity can help here)
            val startedIntent = shadowOf(activity).nextStartedActivity
            assertThat(startedIntent).isNotNull
            assertThat(startedIntent.action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(startedIntent.data.toString()).isEqualTo("http://example.com")
        }
        
        @Test
        fun `MraidBannerListener onMraidExpand should call onAdOpened`() {
            mraidListener.onMraidExpand(mockInternalMraidWebView, null)
            verify(mockListener).onAdOpened()
        }

        @Test
        fun `MraidBannerListener onMraidClose should call onAdClosed`() {
            mraidListener.onMraidClose(mockInternalMraidWebView)
            verify(mockListener).onAdClosed()
        }
    }
}

```

**Key aspects of `BannerAdViewTest.kt`:**
-   **Robolectric**: Used for `Context`, `Activity`, `AttributeSet` (implicitly), `ShadowLooper` (for `Handler` posts), and `ShadowActivity` (for verifying `startActivity` intents).
-   **Mocking**: `AdLoader` is mocked and injected using reflection to control ad loading responses. `BannerAdListener` is mocked to verify callbacks.
-   **Initialization**: Tests programmatic and (implicitly via `AttributeSet` if `BannerAdView` was inflated from XML) configuration.
-   **`loadAd()` Logic**:
    -   Verifies correct `AdRequest` is built.
    -   Tests error paths (SDK not init, null ad unit ID).
    -   Simulates `AdLoader` callbacks (`onAdLoaded`, `onError`) and verifies:
        -   `MraidWebView` creation and setup upon successful load.
        -   Correct listener methods are called.
        -   Handling of unsupported ad types (e.g., NATIVE for BannerAdView).
-   **Refresh Logic**:
    -   Uses `ShadowLooper.idleFor()` to advance time and test scheduling/firing of refresh runnable.
    -   Verifies cancellation of refresh on new `loadAd()`, `destroy()`, and `onDetachedFromWindow()`.
-   **Lifecycle**: `onDetachedFromWindow` test for refresh cancellation.
-   **Internal `MraidBannerListener`**: Tests that MRAID events received by this internal listener correctly trigger the public `BannerAdListener` methods (`onAdImpression`, `onAdClicked`, `onAdOpened`, `onAdClosed`).

This provides good coverage for `BannerAdView`.

Next, `InterstitialAdTest.kt`.
I'll create `AdSDK/src/test/java/com/example/adsdk/InterstitialAdTest.kt`.
