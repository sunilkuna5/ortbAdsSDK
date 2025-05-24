package com.example.adsdksample

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class NativeAdIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun registerIdlingResource() {
        IdlingRegistry.getInstance().register(com.example.adsdk.AdLoader.adLoaderIdlingResource)
        IdlingRegistry.getInstance().register(nativeAdLoadIdlingResource)
    }

    @After
    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(com.example.adsdk.AdLoader.adLoaderIdlingResource)
        IdlingRegistry.getInstance().unregister(nativeAdLoadIdlingResource)
    }

    @Test
    fun testNativeAd_loadsAndDisplaysAssets_andHandlesClick() {
        // 1. Load Native Ad
        onView(withId(R.id.btnLoadNativeAd)).perform(click())
        // AdLoaderIdlingResource will wait for AdLoader network operations.
        // nativeAdLoadIdlingResource will wait for MainActivity's onAdLoaded/onAdFailedToLoad.

        // 2. Verify assets are displayed (Espresso waits due to IdlingResources)
        // The mock server should return predictable text for these assets.
        // The MainActivity.displayNativeAd() uses "Default Title" etc. if ad.getTitle() is null.
        // Assuming the mock server *does* return values for these fields.
        // Let's check against the actual values the mock server for "test-native-ad-unit" returns.
        // From the mock server (https://mock-ad-server.vercel.app/api/openrtb?adUnitId=test-native-ad-unit):
        // title: "Test Native Ad Title"
        // description: "This is a test native ad description from the mock server."
        // cta: "Click Here!"
        // sponsoredBy: "Mock Ad Server"

        onView(withId(R.id.nativeAdTitle)).check(matches(withText("Test Native Ad Title")))
        onView(withId(R.id.nativeAdDescription)).check(matches(withText("This is a test native ad description from the mock server.")))
        onView(withId(R.id.nativeAdCtaButton)).check(matches(withText("Click Here!")))
        onView(withId(R.id.nativeAdSponsoredBy)).check(matches(withText("Sponsored by Mock Ad Server")))

        // Check visibility of image views (actual image loading is not part of this test)
        onView(withId(R.id.nativeAdIcon)).check(matches(isDisplayed()))
        onView(withId(R.id.nativeAdMainImage)).check(matches(isDisplayed()))
        onView(withId(R.id.nativeAdContainer)).check(matches(isDisplayed()))


        // 3. Test Click Interaction
        // MainActivity's NativeAdListener should log "Native Ad: Clicked" and show a Toast.
        // The NativeAd class itself handles opening the landing URL.
        // The SampleAppIdlingResource is not currently set by native ad's onAdClicked.
        // For this test, we'll check the Toast.
        
        onView(withId(R.id.nativeAdCtaButton)).perform(click())
        
        // Verify Toast (if MainActivity's listener shows one for Native onAdClicked)
        onView(withText("Native Ad Clicked"))
            .inRoot(ToastMatcher())
            .check(matches(isDisplayed()));
            
        // Further verification could involve checking if an Intent to open a URL was fired,
        // but that requires more setup with Espresso-Intents or checking ShadowActivity.
        // For now, the click itself and listener Toast is the main check.
    }
}
