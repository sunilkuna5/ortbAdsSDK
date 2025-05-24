package com.example.adsdk

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.lang.reflect.Field

@ExtendWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Config.OLDEST_SDK])
class InterstitialAdTest {

    private lateinit var activity: Activity
    private lateinit var interstitialAd: InterstitialAd
    private lateinit var mockAdLoader: AdLoader
    private lateinit var mockListener: InterstitialAdListener

    @BeforeEach
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        mockAdLoader = mock()
        mockListener = mock()

        resetAdSdkSingleton()
        AdSdk.initialize(activity.applicationContext, AdSdkConfig(apiKey = "test-sdk-key"))

        interstitialAd = InterstitialAd(activity, "test-interstitial-unit")
        interstitialAd.setInterstitialAdListener(mockListener)

        // Replace internal AdLoader with mock
        val adLoaderField = InterstitialAd::class.java.getDeclaredField("adLoader")
        adLoaderField.isAccessible = true
        adLoaderField.set(interstitialAd, mockAdLoader)
    }

    @AfterEach
    fun tearDown() {
        interstitialAd.destroy()
        resetAdSdkSingleton()
    }

    @Nested
    inner class LoadAdSequence {
        private val adRequestCaptor = argumentCaptor<AdRequest>()
        private val adLoadListenerCaptor = argumentCaptor<AdLoader.AdLoadListener>()

        @Test
        fun `loadAd should call AdLoader with correct AdRequest`() {
            interstitialAd.loadAd()
            verify(mockAdLoader).loadAd(adRequestCaptor.capture(), any())
            val request = adRequestCaptor.firstValue
            assertThat(request.adUnitId).isEqualTo("test-interstitial-unit")
            assertThat(request.format).isEqualTo(AdFormat.INTERSTITIAL)
        }

        @Test
        fun `loadAd when AdSDK not initialized should call onAdFailedToLoad`() {
            resetAdSdkSingleton()
            interstitialAd.loadAd()
            verify(mockListener).onAdFailedToLoad(check {
                assertThat(it.errorCode).isEqualTo(AdErrorCodes.SDK_NOT_INITIALIZED)
            })
        }

        @Test
        fun `onAdLoaded callback should set isLoaded true and call listener`() {
            interstitialAd.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            val loaderCallback = adLoadListenerCaptor.firstValue
            val adResponse = AdResponse("<html>Interstitial</html>", 1024, 768, AdType.HTML)
            
            loaderCallback.onAdLoaded(adResponse)
            ShadowLooper.runUiThreadTasks()

            assertThat(interstitialAd.isLoaded()).isTrue()
            verify(mockListener).onAdLoaded(interstitialAd)
        }
        
        @Test
        fun `onAdLoaded with NATIVE type should call onAdFailedToLoad`() {
            interstitialAd.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            val loaderCallback = adLoadListenerCaptor.firstValue
            val adResponse = AdResponse("{}", 1024, 768, AdType.NATIVE)
            
            loaderCallback.onAdLoaded(adResponse)
            ShadowLooper.runUiThreadTasks()

            verify(mockListener).onAdFailedToLoad(check {
                assertThat(it.errorCode).isEqualTo(AdErrorCodes.UNSUPPORTED_AD_TYPE)
            })
            assertThat(interstitialAd.isLoaded()).isFalse()
        }


        @Test
        fun `onError callback from AdLoader should call listener onAdFailedToLoad`() {
            interstitialAd.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            val loaderCallback = adLoadListenerCaptor.firstValue
            val error = AdError(AdErrorCodes.NO_FILL, "No ad")

            loaderCallback.onError(error)
            ShadowLooper.runUiThreadTasks()

            assertThat(interstitialAd.isLoaded()).isFalse()
            verify(mockListener).onAdFailedToLoad(error)
        }
    }

    @Nested
    inner class ShowAdSequence {
        @BeforeEach
        fun loadAdSuccessfully() {
            // Simulate a successful ad load before each show test
            interstitialAd.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            val loaderCallback = adLoadListenerCaptor.firstValue
            val adResponse = AdResponse("<html>Interstitial Ad Content</html>", 1024, 768, AdType.HTML)
            loaderCallback.onAdLoaded(adResponse)
            ShadowLooper.runUiThreadTasks() // Ensure onAdLoaded post is processed
            reset(mockListener) // Reset listener mock to only capture show sequence calls
            interstitialAd.setInterstitialAdListener(mockListener) // Re-set listener after reset
        }

        @Test
        fun `show when not loaded should call onAdFailedToLoad and not show dialog`() {
            interstitialAd.destroy() // Reset internal state, including isLoaded
            interstitialAd = InterstitialAd(activity, "test-interstitial-unit") // New instance, not loaded
            interstitialAd.setInterstitialAdListener(mockListener)
            
            interstitialAd.show()
            
            verify(mockListener).onAdFailedToLoad(check {
                assertThat(it.errorCode).isEqualTo(AdErrorCodes.AD_NOT_READY)
            })
            val latestDialog = ShadowDialog.getLatestDialog()
            assertThat(latestDialog).isNull() // No dialog should be shown
        }

        @Test
        fun `show when loaded should display dialog and call listeners`() {
            interstitialAd.show()
            ShadowLooper.runUiThreadTasks()

            val dialog = ShadowDialog.getLatestDialog()
            assertThat(dialog).isNotNull
            assertThat(dialog.isShowing).isTrue()
            assertThat(dialog).isInstanceOf(Dialog::class.java)
            
            val mraidWebView = dialog.findViewById<View>(android.R.id.content)?.findViewWithTag("MraidWebView") as? MraidWebView
            // Note: Finding MraidWebView by tag is not how it's set up. It *is* the content view.
            // The content view of the Dialog should be the MraidWebView.
            val contentView = shadowOf(dialog).contentView
            assertThat(contentView).isInstanceOf(MraidWebView::class.java)

            verify(mockListener).onAdImpression()
            verify(mockListener).onAdShown()
            assertThat(interstitialAd.isLoaded()).isFalse() // Should be marked as not loaded after show
        }
        
        @Test
        fun `show when context is not Activity should fail`() {
            val appContext = mock<Context>() // Non-activity context
            val interstitialWithAppContext = InterstitialAd(appContext, "unit-id")
            interstitialWithAppContext.setInterstitialAdListener(mockListener)
            
            // Simulate loaded state for this instance
            val adResponse = AdResponse("<html></html>", 320, 50, AdType.HTML)
            val adResponsePayloadField: Field = InterstitialAd::class.java.getDeclaredField("adResponsePayload")
            adResponsePayloadField.isAccessible = true
            adResponsePayloadField.set(interstitialWithAppContext, adResponse)
            val isLoadedField: Field = InterstitialAd::class.java.getDeclaredField("isLoaded")
            isLoadedField.isAccessible = true
            isLoadedField.set(interstitialWithAppContext, true)

            interstitialWithAppContext.show()
            verify(mockListener).onAdFailedToLoad(check {
                assertThat(it.errorCode).isEqualTo(AdErrorCodes.INTERNAL_ERROR)
                assertThat(it.errorMessage).contains("Context is not an Activity")
            })
        }


        @Test
        fun `dialog dismiss should call onAdClosed and cleanup`() {
            interstitialAd.show()
            ShadowLooper.runUiThreadTasks()

            val dialog = ShadowDialog.getLatestDialog()
            assertThat(dialog).isNotNull
            dialog.dismiss()
            ShadowLooper.runUiThreadTasks() // Process any posts from dismiss

            verify(mockListener).onAdClosed()
            // Verify cleanup
            val adDialogField = InterstitialAd::class.java.getDeclaredField("adDialog")
            adDialogField.isAccessible = true
            assertThat(adDialogField.get(interstitialAd)).isNull()
            
            val mraidWebViewField = InterstitialAd::class.java.getDeclaredField("mraidWebView")
            mraidWebViewField.isAccessible = true
            assertThat(mraidWebViewField.get(interstitialAd)).isNull()
        }
    }
    
    @Nested
    inner class MraidInterstitialListenerInternalClass {
        private lateinit var mraidListener: MraidListener
        private val mockInternalMraidWebView: MraidWebView = mock()

        @BeforeEach
        fun mraidListenerSetupAndShow() {
            // Load and show the ad to setup MraidWebView and its listener
            interstitialAd.loadAd()
            verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
            val loaderCallback = adLoadListenerCaptor.firstValue
            val adResponse = AdResponse("<html>Interstitial Ad Content</html>", 1024, 768, AdType.HTML)
            loaderCallback.onAdLoaded(adResponse)
            ShadowLooper.runUiThreadTasks()
            
            interstitialAd.show()
            ShadowLooper.runUiThreadTasks()

            val dialog = ShadowDialog.getLatestDialog()
            val contentView = shadowOf(dialog).contentView as MraidWebView
            mraidListener = contentView.mraidListener!!
            
            reset(mockListener) // Reset main listener for focused verification
            interstitialAd.setInterstitialAdListener(mockListener)
        }

        @Test
        fun `MraidInterstitialListener onMraidOpen should call onAdClicked`() {
            mraidListener.onMraidOpen(mockInternalMraidWebView, "http://example.com")
            verify(mockListener).onAdClicked()
            val startedIntent = shadowOf(activity).nextStartedActivity
            assertThat(startedIntent?.action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(startedIntent?.data?.toString()).isEqualTo("http://example.com")
        }

        @Test
        fun `MraidInterstitialListener onMraidClose should dismiss dialog`() {
            val dialog = ShadowDialog.getLatestDialog()
            assertThat(dialog.isShowing).isTrue()
            
            mraidListener.onMraidClose(mockInternalMraidWebView)
            ShadowLooper.runUiThreadTasks() // Allow dismiss to process
            
            assertThat(dialog.isShowing).isFalse()
            verify(mockListener).onAdClosed() // Verified via dialog dismiss listener
        }

        @Test
        fun `MraidInterstitialListener onMraidExpand should fire MRAID error`() {
            mraidListener.onMraidExpand(mockInternalMraidWebView, null)
            verify(mockInternalMraidWebView).fireErrorEvent(any(), eq("expand"))
        }
    }

    @Test
    fun `destroy method should cleanup resources`() {
        // Simulate loaded and shown ad
        interstitialAd.loadAd()
        verify(mockAdLoader).loadAd(any(), adLoadListenerCaptor.capture())
        val loaderCallback = adLoadListenerCaptor.firstValue
        loaderCallback.onAdLoaded(AdResponse("<html></html>", 320, 50, AdType.HTML))
        ShadowLooper.runUiThreadTasks()
        interstitialAd.show()
        ShadowLooper.runUiThreadTasks()

        val dialog = ShadowDialog.getLatestDialog()
        assertThat(dialog).isNotNull
        assertThat(dialog.isShowing).isTrue()

        interstitialAd.destroy()

        assertThat(dialog.isShowing).isFalse() // Dialog should be dismissed
        val adDialogField = InterstitialAd::class.java.getDeclaredField("adDialog")
        adDialogField.isAccessible = true
        assertThat(adDialogField.get(interstitialAd)).isNull()
        
        val mraidWebViewField = InterstitialAd::class.java.getDeclaredField("mraidWebView")
        mraidWebViewField.isAccessible = true
        assertThat(mraidWebViewField.get(interstitialAd)).isNull()

        val listenerField = InterstitialAd::class.java.getDeclaredField("interstitialAdListener")
        listenerField.isAccessible = true
        assertThat(listenerField.get(interstitialAd)).isNull()
    }
}
```

**Key aspects of `InterstitialAdTest.kt`:**
-   **Robolectric**: Used for `Activity` context, `Dialog` interactions (`ShadowDialog`), and `ShadowLooper`.
-   **Mocking**: `AdLoader` is mocked and injected. `InterstitialAdListener` is mocked.
-   **`loadAd()` Logic**:
    -   Verifies correct `AdRequest`.
    -   Tests error paths (SDK not init).
    -   Simulates `AdLoader` callbacks and checks:
        -   `isLoaded` state.
        -   Listener `onAdLoaded` / `onAdFailedToLoad` calls.
        -   Handling of unsupported ad types (NATIVE for InterstitialAd).
-   **`show()` Logic**:
    -   Requires a successful `loadAd()` first (simulated in `@BeforeEach` for the `ShowAdSequence` nested class).
    -   Verifies `onAdFailedToLoad` if shown when not ready or if context is not Activity.
    -   Uses `ShadowDialog.getLatestDialog()` to inspect the shown dialog.
    -   Verifies `MraidWebView` is set as content view.
    -   Verifies `onAdImpression` and `onAdShown` listener calls.
    -   Verifies `isLoaded` becomes false after showing.
    -   Tests dialog dismissal calls `onAdClosed` and cleans up resources.
-   **Internal `MraidInterstitialListener`**:
    -   Tests that MRAID events (like `onMraidOpen`, `onMraidClose`, `onMraidExpand`) trigger the correct actions (e.g., calling public listener, dismissing dialog, firing MRAID errors).
-   **`destroy()` Method**:
    -   Ensures dialog is dismissed and resources (MraidWebView, listeners) are nulled out.

This provides good coverage for `InterstitialAd`.

Finally, tests for `NativeAd.kt`.
I'll create `AdSDK/src/test/java/com/example/adsdk/NativeAdTest.kt`.
