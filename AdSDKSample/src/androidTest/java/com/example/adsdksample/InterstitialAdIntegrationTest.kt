package com.example.adsdksample

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
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
import android.webkit.WebView
import org.hamcrest.Matchers.allOf // For combining matchers

@RunWith(AndroidJUnit4::class)
@LargeTest
class InterstitialAdIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun registerIdlingResource() {
        IdlingRegistry.getInstance().register(com.example.adsdk.AdLoader.adLoaderIdlingResource)
        IdlingRegistry.getInstance().register(interstitialLifecycleIdlingResource)
    }

    @After
    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(com.example.adsdk.AdLoader.adLoaderIdlingResource)
        IdlingRegistry.getInstance().unregister(interstitialLifecycleIdlingResource)
    }

    @Test
    fun testInterstitialAd_loadsAndShowsAndCloses() {
        // 1. Load Interstitial
        onView(withId(R.id.btnLoadInterstitial)).perform(click())
        // AdLoaderIdlingResource will wait for load to complete.

        // 2. Show Interstitial (once loaded)
        onView(withId(R.id.btnShowInterstitial)).perform(click())
        // interstitialLifecycleIdlingResource will wait for onAdClosed.
        
        // 3. Verify the ad dialog is displayed (briefly).
        // This check happens *before* interstitialLifecycleIdlingResource becomes idle if onAdClosed is quick.
        // It's more to ensure the show path was taken.
        // A more reliable check for dialog content would need more advanced sync or a creative that stays open.
        onView(instanceOf(WebView::class.java)) 
            .check(matches(isDisplayed())) 

        // 4. Espresso waits for interstitialLifecycleIdlingResource (decremented in onAdClosed)
        // After it's closed, verify a main activity view is available.
        onView(withId(R.id.btnLoadInterstitial)).check(matches(isDisplayed()));
    }

    @Test
    fun testShowInterstitial_withoutLoading_failsGracefully() {
        // Click "Show Interstitial" without loading
        onView(withId(R.id.btnShowInterstitial)).perform(click())

        // Verify it doesn't crash: The test will pass if no crash occurs.
        // Verify an error/log is produced: MainActivity shows a Toast "Interstitial Ad not loaded yet."
        onView(withText("Interstitial Ad not loaded yet."))
            .inRoot(ToastMatcher()) // Custom matcher for Toast
            .check(matches(isDisplayed()));
        
        // Ensure no ad dialog (WebView) is shown
        onView(instanceOf(WebView::class.java)).check(doesNotExist());
        // A more robust way is to check for a unique view ID *inside* the MRAID creative if possible,
        // or check if a Dialog is currently displayed (harder with Espresso directly).
        // For now, we'll look for a WebView that is likely part of the interstitial.
        // This assumes the MRAID creative is simple and fills the WebView.
        onView(instanceOf(WebView::class.java)) // This might be too generic if other WebViews are present
            .check(matches(isDisplayed())) // Check if any WebView is displayed (likely the ad)

        // 4. Press back to dismiss dialog (or use MRAID close if testable)
        // Espresso.pressBack()
        // For MRAID, the creative usually has its own close button.
        // The mock ad server needs to provide a creative that calls mraid.close()
        // or has a clickable element that does so.
        // For this test, we'll assume the back press closes it or there's an implicit close after a timeout.
        // If the MRAID creative has a "close" button with a known ID/text, we'd click it:
        // onView(withId(R.id.mraid_close_button)).perform(click());
        // Since our SampleAppIdlingResource is set to true in onAdClosed, Espresso will wait.

        // To simulate MRAID close or back press for now, let's assume onAdClosed will be called.
        // The IdlingResource handles waiting for onAdClosed.
        // After closing, the WebView of the interstitial should no longer be found.
        // This check is tricky because the WebView might just be hidden or removed from hierarchy.
        // A more robust check is that the MainActivity's views are interactive again.
        onView(withId(R.id.btnLoadInterstitial)).check(matches(isDisplayed())); // Check a main view is back
    }

    @Test
    fun testShowInterstitial_withoutLoading_failsGracefully() {
        // Click "Show Interstitial" without loading
        onView(withId(R.id.btnShowInterstitial)).perform(click())

        // Verify it doesn't crash: The test will pass if no crash occurs.
        // Verify an error/log is produced: MainActivity shows a Toast "Interstitial Ad not loaded yet."
        onView(withText("Interstitial Ad not loaded yet."))
            .inRoot(ToastMatcher()) // Custom matcher for Toast
            .check(matches(isDisplayed()));
        
        // Ensure no ad dialog (WebView) is shown
        onView(instanceOf(WebView::class.java)).check(doesNotExist());
    }
}
```

**Note:** The `ToastMatcher` is a custom matcher needed to verify Toast messages. I'll add a basic version of it.
I'll also refine `MainActivity.kt` for `InterstitialAd` to use `SampleAppIdlingResource` more precisely for `show()` if needed. The current setup in `MainActivity` sets `SampleAppIdlingResource` to `false` when `showInterstitial` is clicked and `true` when `onAdClosed` is called, which should work.

Now, I'll create the `ToastMatcher.kt`.
