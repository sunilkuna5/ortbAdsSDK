package com.example.adsdk.benchmarks

import android.content.Context
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.adsdk.AdError
import com.example.adsdk.AdFormat
import com.example.adsdk.AdLoader
import com.example.adsdk.AdRequest
import com.example.adsdk.AdResponse
import com.example.adsdk.AdSdk
import com.example.adsdk.AdSdkConfig
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AdLoaderBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private lateinit var context: Context
    private lateinit var adLoader: AdLoader
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Initialize AdSdk - this should ideally be done once for the entire test suite
        // or ensure the test app (AdSDKSample) handles initialization.
        if (!AdSdk.isInitialized()) {
            AdSdk.initialize(context, AdSdkConfig(apiKey = "test-api-key", testMode = true))
        }
        adLoader = AdLoader()
        device = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun adLoader_fullAdRequestResponseCycle() {
        benchmarkRule.measureRepeated(
            packageName = "com.example.adsdksample", // Package name of the app to benchmark
            metrics = listOf(FrameTimingMetric()), // FrameTimingMetric is common, others like StartupTimingMetric exist
            compilationMode = CompilationMode.DEFAULT, // Or .None() for faster but less realistic runs
            startupMode = StartupMode.COLD, // Or .WARM or .HOT. COLD is more realistic for app launch.
                                            // For this operation, might not be as critical as for app startup.
            iterations = 5, // Number of times the benchmark loop is run
            setupBlock = {
                // Optional: Code to run before each measurement iteration
                // e.g., navigate to a specific UI, clear state
                // For AdLoader, ensure it's a fresh call scenario if needed.
                // Since AdLoader is stateless for a single call, this might not be complex.
                
                // For macrobenchmarks, you usually start an activity from the target app.
                // This ensures the app is in a known state and running.
                val intent = context.packageManager.getLaunchIntentForPackage("com.example.adsdksample")
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
                device.wait(Until.hasObject(By.pkg("com.example.adsdksample").depth(0)), 5000) // Wait for app
            }
        ) { // this: MacrobenchmarkScope
            
            // The operation to benchmark
            val latch = CountDownLatch(1)
            val adRequest = AdRequest(adUnitId = "test-banner-ad-unit", format = AdFormat.BANNER)
            
            // Ensure loadAd is called on the main thread if it interacts with UI components
            // or posts to the main handler, which AdLoader does for callbacks.
            // MacrobenchmarkScope.launchGesture and similar methods handle UI thread execution.
            // For non-UI logic that just needs to run within the benchmarked process:
            // This is tricky, as adLoader.loadAd itself is async and posts callbacks.
            // We need to measure the time until the callback.
            // measureRepeated measures the block. So, the block must include the wait.

            var adError: AdError? = null
            var adResponse: AdResponse? = null

            // The actual benchmarked code. It must be self-contained and synchronous from
            // the perspective of measureRepeated. The latch handles the async nature internally.
            adLoader.loadAd(adRequest, object : AdLoader.AdLoadListener {
                override fun onAdLoaded(response: AdResponse) {
                    adResponse = response
                    latch.countDown()
                }

                override fun onError(error: AdError) {
                    adError = error
                    latch.countDown()
                }
            })

            // Wait for the ad to load or error out
            val success = latch.await(15, TimeUnit.SECONDS) // Increased timeout

            // Optional: Assertions to ensure the operation was successful,
            // but be careful as failures might skew benchmark results.
            // It's better to ensure setup makes success likely.
             if (!success) {
                 throw RuntimeException("AdLoader benchmark timed out.")
             }
             if (adError != null) {
                 throw RuntimeException("AdLoader benchmark failed with error: ${adError?.errorMessage}")
             }
             if (adResponse == null) {
                 throw RuntimeException("AdLoader benchmark did not receive an ad response.")
             }
        }
    }
}
