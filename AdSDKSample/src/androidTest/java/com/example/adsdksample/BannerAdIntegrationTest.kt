package com.example.adsdksample

import android.webkit.WebView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.adsdk.BannerAdView
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BannerAdIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun registerIdlingResource() {
        // Register AdLoader's idling resource
        IdlingRegistry.getInstance().register(com.example.adsdk.AdLoader.adLoaderIdlingResource)
    }

    @After
    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(com.example.adsdk.AdLoader.adLoaderIdlingResource)
    }

    @Test
    fun testBannerAd_loadsAndIsDisplayed() {
        // Banner ad in MainActivity is configured to load on create.
        // AdLoaderIdlingResource will handle waiting for the load to complete.

        onView(withId(R.id.bannerAdView))
            .check(matches(isDisplayed())) // First, ensure the view itself is part of the hierarchy
            .check { view, noViewFoundException -> // Then, perform custom checks
                if (noViewFoundException != null) {
                    throw noViewFoundException
                }
                assertThat(view).isInstanceOf(BannerAdView::class.java)
                val bannerAdView = view as BannerAdView
                // Check if MraidWebView is inside
                var mraidWebViewFound = false
                for (i in 0 until bannerAdView.childCount) {
                    if (bannerAdView.getChildAt(i) is WebView) {
                        mraidWebViewFound = true
                        break
                    }
                }
                assertThat(mraidWebViewFound).isTrue()
                // Check visibility of the WebView itself
                 val webView = bannerAdView.getChildAt(0) as WebView // Assuming it's the first child
                 assertThat(webView.visibility).isEqualTo(View.VISIBLE)
            }
        
        // Verify that onAdClicked was called (due to auto-open in the test creative)
        // This requires MainActivity's BannerAdListener to be accessible or to set a flag/idling resource.
        // For simplicity, we'll assume the Toast from onAdClicked is a verifiable side effect.
        // This relies on the timing of the auto-open.
         onView(withText("Banner Ad Clicked"))
            .inRoot(ToastMatcher())
            .check(matches(isDisplayed()));
    }

    // testBannerAd_click() is omitted as it's hard to reliably test MRAID open()
    // without a very specific test creative and mock server setup.
    // Clicking on a WebView that executes mraid.open() would require Espresso-Web
    // and complex JS interaction or a creative that signals click success via JS bridge.
}
