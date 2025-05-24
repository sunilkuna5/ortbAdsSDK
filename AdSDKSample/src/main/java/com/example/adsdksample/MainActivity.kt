package com.example.adsdksample

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.adsdk.AdError
import com.example.adsdk.BannerAdListener
import com.example.adsdk.BannerAdView
import com.example.adsdk.InterstitialAd
import com.example.adsdk.InterstitialAdListener
import com.example.adsdk.NativeAd
import com.example.adsdk.NativeAdListener
import com.example.adsdksample.databinding.ActivityMainBinding // Using ViewBinding
import androidx.test.espresso.idling.CountingIdlingResource

// IdlingResource for AdLoader (via AdSDK) is handled by AdLoader itself.
// IdlingResource for Interstitial show/close lifecycle, managed by MainActivity.
val interstitialLifecycleIdlingResource = CountingIdlingResource("InterstitialLifecycleResource")
// IdlingResource for Native Ad load, managed by MainActivity.
val nativeAdLoadIdlingResource = CountingIdlingResource("NativeAdLoadResource")


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var interstitialAd: InterstitialAd
    private lateinit var nativeAd: NativeAd

    private val TAG = "AdSDK_SampleApp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Banner Ad Setup ---
        setupBannerAd()

        // --- Interstitial Ad Setup ---
        setupInterstitialAd()

        // --- Native Ad Setup ---
        setupNativeAd()
    }

    private fun setupBannerAd() {
        // BannerAdView is already in the layout with app:adUnitId and app:adSize
        // We just need to set a listener and load it.
        // Banner loading is handled by AdLoaderIdlingResource, no manual changes needed here for that.
        binding.bannerAdView.setBannerAdListener(object : BannerAdListener {
            override fun onAdLoaded(adView: BannerAdView) {
                Log.i(TAG, "Banner Ad: Loaded")
                Toast.makeText(this@MainActivity, "Banner Ad Loaded", Toast.LENGTH_SHORT).show()
            }

            override fun onAdFailedToLoad(error: AdError) {
                Log.e(TAG, "Banner Ad: Failed to load. Error ${error.errorCode}: ${error.errorMessage}")
                Toast.makeText(this@MainActivity, "Banner Ad Failed: ${error.errorMessage}", Toast.LENGTH_SHORT).show()
            }

            override fun onAdClicked() {
                Log.i(TAG, "Banner Ad: Clicked")
                Toast.makeText(this@MainActivity, "Banner Ad Clicked", Toast.LENGTH_SHORT).show()
            }

            override fun onAdImpression() {
                Log.i(TAG, "Banner Ad: Impression")
                Toast.makeText(this@MainActivity, "Banner Ad Impression", Toast.LENGTH_SHORT).show()
            }

            override fun onAdOpened() { // For banner expansion
                Log.i(TAG, "Banner Ad: Opened (Expanded)")
                Toast.makeText(this@MainActivity, "Banner Ad Opened (Expanded)", Toast.LENGTH_SHORT).show()
            }

            override fun onAdClosed() { // For MRAID close if it was expanded
                Log.i(TAG, "Banner Ad: Closed (MRAID)")
                Toast.makeText(this@MainActivity, "Banner Ad Closed (MRAID)", Toast.LENGTH_SHORT).show()
            }
        })
        Log.d(TAG, "Banner Ad: Requesting ad load...")
        binding.bannerAdView.loadAd()
    }

    private fun setupInterstitialAd() {
        interstitialAd = InterstitialAd(this, "test-interstitial-ad-unit") // Ad unit ID from mock server
        interstitialAd.setInterstitialAdListener(object : InterstitialAdListener {
            override fun onAdLoaded(ad: InterstitialAd) {
                Log.i(TAG, "Interstitial Ad: Loaded")
                Toast.makeText(this@MainActivity, "Interstitial Ad Loaded", Toast.LENGTH_SHORT).show()
                // AdLoaderIdlingResource handles the load phase.
            }

            override fun onAdFailedToLoad(error: AdError) {
                Log.e(TAG, "Interstitial Ad: Failed to load. Error ${error.errorCode}: ${error.errorMessage}")
                Toast.makeText(this@MainActivity, "Interstitial Ad Failed: ${error.errorMessage}", Toast.LENGTH_SHORT).show()
                // AdLoaderIdlingResource handles the load phase.
                 if (!interstitialLifecycleIdlingResource.isIdleNow) interstitialLifecycleIdlingResource.decrement() // If show was attempted
            }

            override fun onAdShown() {
                Log.i(TAG, "Interstitial Ad: Shown")
                Toast.makeText(this@MainActivity, "Interstitial Ad Shown", Toast.LENGTH_SHORT).show()
                // Decrement could happen here if onMraidReady is also part of "shown" criteria
                // For now, onAdClosed is the main "idle" signal for show lifecycle.
            }

            override fun onAdClicked() {
                Log.i(TAG, "Interstitial Ad: Clicked")
                Toast.makeText(this@MainActivity, "Interstitial Ad Clicked", Toast.LENGTH_SHORT).show()
            }

            override fun onAdClosed() {
                Log.i(TAG, "Interstitial Ad: Closed")
                Toast.makeText(this@MainActivity, "Interstitial Ad Closed", Toast.LENGTH_SHORT).show()
                if (!interstitialLifecycleIdlingResource.isIdleNow) interstitialLifecycleIdlingResource.decrement()
            }

            override fun onAdImpression() {
                 Log.i(TAG, "Interstitial Ad: Impression")
                 Toast.makeText(this@MainActivity, "Interstitial Ad Impression", Toast.LENGTH_SHORT).show()
            }
        })

        binding.btnLoadInterstitial.setOnClickListener {
            Log.d(TAG, "Interstitial Ad: Requesting ad load...")
            // AdLoaderIdlingResource is incremented by AdLoader itself.
            interstitialAd.loadAd()
        }

        binding.btnShowInterstitial.setOnClickListener {
            if (interstitialAd.isLoaded()) {
                Log.d(TAG, "Interstitial Ad: Attempting to show...")
                interstitialLifecycleIdlingResource.increment() // Busy until ad is closed
                interstitialAd.show()
            } else {
                Log.w(TAG, "Interstitial Ad: Not loaded, cannot show.")
                Toast.makeText(this, "Interstitial Ad not loaded yet.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupNativeAd() {
        nativeAd = NativeAd(this, "test-native-ad-unit") // Ad unit ID from mock server
        nativeAd.setNativeAdListener(object : NativeAdListener {
            override fun onAdLoaded(ad: NativeAd) {
                Log.i(TAG, "Native Ad: Loaded")
                Toast.makeText(this@MainActivity, "Native Ad Loaded", Toast.LENGTH_SHORT).show()
                displayNativeAd(ad)
                if (!nativeAdLoadIdlingResource.isIdleNow) nativeAdLoadIdlingResource.decrement()
            }

            override fun onAdFailedToLoad(error: AdError) {
                Log.e(TAG, "Native Ad: Failed to load. Error ${error.errorCode}: ${error.errorMessage}")
                Toast.makeText(this@MainActivity, "Native Ad Failed: ${error.errorMessage}", Toast.LENGTH_SHORT).show()
                binding.nativeAdContainer.visibility = View.GONE
                if (!nativeAdLoadIdlingResource.isIdleNow) nativeAdLoadIdlingResource.decrement()
            }

            override fun onAdClicked() {
                Log.i(TAG, "Native Ad: Clicked")
                Toast.makeText(this@MainActivity, "Native Ad Clicked", Toast.LENGTH_SHORT).show()
            }

            override fun onAdImpression() {
                Log.i(TAG, "Native Ad: Impression")
                Toast.makeText(this@MainActivity, "Native Ad Impression", Toast.LENGTH_SHORT).show()
            }

            override fun onAdClosed() {
                // Not typical for this basic native ad implementation
                Log.i(TAG, "Native Ad: Closed (if it were a custom modal)")
            }
        })

        binding.btnLoadNativeAd.setOnClickListener {
            Log.d(TAG, "Native Ad: Requesting ad load...")
            binding.nativeAdContainer.visibility = View.GONE // Hide until loaded
            nativeAdLoadIdlingResource.increment() // Busy until native ad loaded/failed
            nativeAd.loadAd()
        }
    }

    private fun displayNativeAd(ad: NativeAd) {
        binding.nativeAdContainer.visibility = View.VISIBLE

        binding.nativeAdTitle.text = ad.getTitle() ?: "Default Title"
        binding.nativeAdDescription.text = ad.getDescription() ?: "Default description."
        binding.nativeAdCtaButton.text = ad.getCallToAction() ?: "Learn More"
        binding.nativeAdSponsoredBy.text = ad.getSponsoredBy() ?: "Sponsored"

        // For images, you'd use an image loading library like Glide or Picasso in a real app
        // For simplicity, if URLs are direct image URLs and small, they might load.
        // Here we just log, actual image loading from URL is not implemented.
        ad.getIconUrl()?.let { Log.d(TAG, "Native Ad Icon URL: $it (Image loading not implemented)") }
        ad.getMainImageUrl()?.let { Log.d(TAG, "Native Ad Main Image URL: $it (Image loading not implemented)") }
        // Example: Glide.with(this).load(ad.getIconUrl()).into(binding.nativeAdIcon);

        // Register views for interaction
        val clickableViews = mutableListOf<View>()
        clickableViews.add(binding.nativeAdCtaButton)
        clickableViews.add(binding.nativeAdTitle) // Example: title is also clickable
        // The entire container could be made clickable, or specific assets.
        
        ad.registerViewForInteraction(binding.nativeAdContainer, clickableViews)
    }

    override fun onDestroy() {
        binding.bannerAdView.destroy()
        interstitialAd.destroy()
        nativeAd.destroy()
        super.onDestroy()
    }
}
```

**Notes on `MainActivity.kt`:**
-   **ViewBinding**: Enabled in `build.gradle` and used for easy view access.
-   **Banner Ad**: Loaded automatically when the activity starts. The `adUnitId` and `adSize` are set in the XML layout.
-   **Interstitial Ad**:
    -   Initialized with a context and ad unit ID.
    -   `btnLoadInterstitial` calls `interstitialAd.loadAd()`.
    -   `btnShowInterstitial` calls `interstitialAd.show()` if loaded.
-   **Native Ad**:
    -   Initialized with context and ad unit ID.
    -   `btnLoadNativeAd` calls `nativeAd.loadAd()`.
    -   `displayNativeAd()` populates UI views from `NativeAdAssets` and calls `nativeAd.registerViewForInteraction()`. Image loading from URLs is not implemented, only logged.
-   **Listeners**: Basic listeners are set up for each ad type to log events and show Toasts.
-   **`SampleAppIdlingResource`**: A very basic `IdlingResource` is included. It's manually set to idle/busy states around ad loading calls. This is a simplified approach; a more robust solution would involve deeper integration with the SDK's async operations. For this task, it will help make Espresso tests more stable than `Thread.sleep()`.
-   **`onDestroy()`**: Calls `destroy()` on all ad objects.

This `MainActivity` provides the necessary hooks to test banner, interstitial, and native ad functionalities.

I also need to create placeholder `colors.xml` and `ic_launcher` related XMLs if they are referenced by the theme or manifest, or ensure the theme doesn't crash. For now, I'll assume default Material Component colors are sufficient and `mipmap/ic_launcher` can be skipped for this task if not strictly needed for build.

The `data_extraction_rules.xml` and `backup_rules.xml` referenced in the manifest are standard files generated by Android Studio. I'll create empty versions for now.
