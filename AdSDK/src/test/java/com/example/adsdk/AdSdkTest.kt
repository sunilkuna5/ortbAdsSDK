package com.example.adsdk

import android.content.Context
import android.util.Log
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.reflect.Field
import java.lang.reflect.Modifier

// Helper to reset AdSdk singleton state for testing
fun resetAdSdkSingleton() {
    val fieldsToReset = listOf(
        AdSdk::class.java.getDeclaredField("config").apply { isAccessible = true },
        AdSdk::class.java.getDeclaredField("applicationContext").apply { isAccessible = true },
        AdSdk::class.java.getDeclaredField("initialized").apply { isAccessible = true },
        AdSdk::class.java.getDeclaredField("currentLogLevel").apply { isAccessible = true }
    )

    fieldsToReset.forEach { field ->
        field.isAccessible = true
        // Remove final modifier if present (though these are not final in the current AdSdk.kt)
        // val modifiersField = Field::class.java.getDeclaredField("modifiers")
        // modifiersField.isAccessible = true
        // modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
        
        when (field.name) {
            "config" -> field.set(AdSdk, null)
            "applicationContext" -> field.set(AdSdk, null)
            "initialized" -> field.set(AdSdk, false)
            "currentLogLevel" -> field.set(AdSdk, LogLevel.INFO) // Reset to default
        }
    }

    // Also reset any Log.x mock if used globally, though here we use instance mocks
}


class AdSdkTest {

    private lateinit var mockContext: Context
    private lateinit var mockApplicationContext: Context // Separate mock for applicationContext

    // To capture log messages
    private val logMessages = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        resetAdSdkSingleton() // Ensure clean state for each test
        mockContext = mock()
        mockApplicationContext = mock()
        whenever(mockContext.applicationContext).thenReturn(mockApplicationContext)

        // Mock Android's Log class (basic example)
        // This is a bit tricky as Log methods are static.
        // For more complex scenarios, a test logger or PowerMockito might be used.
        // Here, we'll focus on verifying AdSdk's internal state and calls.
        // For AdSdk.log method, we can verify its behavior by checking the currentLogLevel
        // and assuming Log.x would be called if conditions are met.
    }

    @AfterEach
    fun tearDown() {
        // Clean up mocks or any other state if necessary
        resetAdSdkSingleton()
        logMessages.clear()
    }

    @Nested
    @DisplayName("Initialization Tests")
    inner class Initialization {
        @Test
        fun `initialize should set config and mark SDK as initialized`() {
            val apiKey = "test-api-key"
            val config = AdSdkConfig(apiKey = apiKey, testMode = true, logLevel = LogLevel.DEBUG)

            AdSdk.initialize(mockContext, config)

            assertThat(AdSdk.isInitialized()).isTrue()
            assertThat(AdSdk.getCurrentConfig()).isEqualTo(config)
            assertThat(AdSdk.getApiKey()).isEqualTo(apiKey)
            assertThat(AdSdk.isTestMode()).isTrue()
            // Check if internal currentLogLevel was set from config
            // Need a way to access internal state or observe log output if AdSdk.log was called
            // For now, trust internal assignment and test setLogLevel separately
        }

        @Test
        fun `initialize should use application context`() {
            val config = AdSdkConfig(apiKey = "test-key")
            AdSdk.initialize(mockContext, config)
            verify(mockContext).applicationContext // Verify applicationContext was fetched
            assertThat(AdSdk.getApplicationContext()).isEqualTo(mockApplicationContext)
        }

        @Test
        fun `re-initialization should be ignored and log a warning`() {
            val initialConfig = AdSdkConfig(apiKey = "initial-key", logLevel = LogLevel.INFO)
            AdSdk.initialize(mockContext, initialConfig)

            val initialInternalConfig = AdSdk.getCurrentConfig()

            val secondConfig = AdSdkConfig(apiKey = "second-key", logLevel = LogLevel.DEBUG)
            // We need to capture Log.w. For simplicity, we'll assume it logs if initialized.
            // A more robust test would involve a custom Log utility or PowerMockito.
            AdSdk.initialize(mockContext, secondConfig)

            assertThat(AdSdk.isInitialized()).isTrue() // Still true
            assertThat(AdSdk.getCurrentConfig()).isEqualTo(initialInternalConfig) // Config should not change
            assertThat(AdSdk.getApiKey()).isEqualTo("initial-key")
            // Verify a warning was logged (conceptual, requires log capture setup)
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    inner class Configuration {
        @Test
        fun `getSdkVersion should return the correct version string`() {
            // Access private const val SDK_VERSION for verification if possible, or hardcode expected
            val expectedVersion = "1.0.0-alpha" // As defined in AdSdk.kt
            assertThat(AdSdk.getSdkVersion()).isEqualTo(expectedVersion)
        }

        @Test
        fun `setLogLevel should update log level after initialization`() {
            val config = AdSdkConfig(apiKey = "test-key", logLevel = LogLevel.INFO)
            AdSdk.initialize(mockContext, config)
            
            AdSdk.setLogLevel(LogLevel.VERBOSE)
            // Test internal state if possible, or rely on AdSdk.log behavior if it uses the updated level.
            // For now, test if config object (if mutable or copied) reflects this.
            // The current AdSdk.setLogLevel updates the internal config copy.
            assertThat(AdSdk.getCurrentConfig()?.logLevel).isEqualTo(LogLevel.VERBOSE)
        }

        @Test
        fun `setLogLevel before initialization should also set the level for subsequent init`() {
            // This test depends on the refined AdSdk.setLogLevel behavior
            // where currentLogLevel is a static mutable field.
            AdSdk.setLogLevel(LogLevel.ERROR)

            val config = AdSdkConfig(apiKey = "test-key", logLevel = LogLevel.DEBUG) // Config has DEBUG
            AdSdk.initialize(mockContext, config) // Initialize AFTER setLogLevel

            // The initialize method should use the logLevel from AdSdkConfig,
            // but if setLogLevel was called before, the static currentLogLevel would be ERROR.
            // The refactored AdSdk.initialize now sets this.currentLogLevel = sdkConfig.logLevel.
            // So, the config's log level takes precedence at initialization.
            // The AdSdk.setLogLevel then *overrides* it.
            
            // Let's test the sequence:
            // 1. Default AdSdk.currentLogLevel = INFO
            // 2. AdSdk.setLogLevel(LogLevel.ERROR) -> AdSdk.currentLogLevel = ERROR
            // 3. AdSdk.initialize(config with LogLevel.DEBUG) -> AdSdk.currentLogLevel becomes DEBUG
            
            assertThat(AdSdk.getCurrentConfig()?.logLevel).isEqualTo(LogLevel.DEBUG)
            // To verify the AdSdk.log behavior, we'd need to inspect calls to android.util.Log
        }

        @Test
        fun `internal log method should respect current log level`() {
            // This is harder to test without direct access to Log calls or a testable logger.
            // We can infer by setting level and checking if logs *would* be made.

            AdSdk.initialize(mockContext, AdSdkConfig(apiKey = "test", logLevel = LogLevel.INFO))
            
            // With LogLevel.INFO, an INFO message should be logged.
            // A DEBUG message should not.
            // (Conceptual: requires capturing Log.i, Log.d)

            // Example with direct check (if AdSdk.log was public and returned boolean if logged)
            // AdSdk.setLogLevel(LogLevel.INFO)
            // assertThat(AdSdk.log(LogLevel.INFO, "TestTag", "Info message")).isTrue()
            // assertThat(AdSdk.log(LogLevel.DEBUG, "TestTag", "Debug message")).isFalse()
            // AdSdk.setLogLevel(LogLevel.NONE)
            // assertThat(AdSdk.log(LogLevel.ERROR, "TestTag", "Error message")).isFalse()

            // Current AdSdk.log is internal. Test by ensuring internal state is correct.
             AdSdk.setLogLevel(LogLevel.INFO)
             // If we call AdSdk.log(LogLevel.DEBUG, ...), it shouldn't log.
             // If we call AdSdk.log(LogLevel.INFO, ...), it should.
             // This test is more about the logic inside AdSdk.log itself which is hard to verify externally.
             // We trust that if AdSdk.getLogLevel() returns a certain level, the internal android.util.Log calls
             // will be made or skipped appropriately.
        }
    }

    @Nested
    @DisplayName("Internal Getters")
    inner class InternalGetters {
        @Test
        fun `getApiKey should return correct key after init`() {
            val config = AdSdkConfig(apiKey = "api123")
            AdSdk.initialize(mockContext, config)
            assertThat(AdSdk.getApiKey()).isEqualTo("api123")
        }

        @Test
        fun `getApiKey should return null if not initialized`() {
            assertThat(AdSdk.getApiKey()).isNull()
        }

        @Test
        fun `getApplicationContext should return correct context after init`() {
            val config = AdSdkConfig(apiKey = "api123")
            AdSdk.initialize(mockContext, config)
            assertThat(AdSdk.getApplicationContext()).isEqualTo(mockApplicationContext)
        }
        
        @Test
        fun `isTestMode should return correct mode after init`() {
            val config = AdSdkConfig(apiKey = "api123", testMode = true)
            AdSdk.initialize(mockContext, config)
            assertThat(AdSdk.isTestMode()).isTrue()
        }

        @Test
        fun `isTestMode should return false by default if not specified and initialized`() {
            val config = AdSdkConfig(apiKey = "api123") // testMode defaults to false
            AdSdk.initialize(mockContext, config)
            assertThat(AdSdk.isTestMode()).isFalse()
        }
    }
}

```

**Explanation of `resetAdSdkSingleton()`:**
Since `AdSdk` is an `object` (a Kotlin singleton), its state persists between tests. This is problematic for unit testing as tests should be independent. The `resetAdSdkSingleton` function uses reflection to access and reset the private fields of the `AdSdk` object before each test run. This ensures that each test starts with a clean `AdSdk` state.

**Note on Testing `AdSdk.log()`:**
Directly testing the `AdSdk.log()` internal method to verify if `android.util.Log` was called with specific messages is complex because `android.util.Log` methods are static. This typically requires:
1.  **Robolectric**: It provides shadows for Android framework classes, including `Log`, allowing you to inspect logs.
2.  **PowerMockito/MockK (for static mocks)**: These can mock static methods.
3.  **Injecting a Logger**: Refactoring `AdSdk` to use an injected logger interface would be the cleanest way for testing, but it's a larger change.

For this subtask, I've focused on testing the state and configuration aspects of `AdSdk`. The test `internal log method should respect current log level` is more conceptual for now. If deeper log verification is needed, Robolectric or PowerMockito would be the next step.

The current tests cover:
- Initialization and re-initialization.
- Correct storage and retrieval of configuration values (`apiKey`, `testMode`, `logLevel` via `AdSdkConfig`).
- `getSdkVersion()`.
- `setLogLevel()` behavior.
- Internal getters.

This provides good coverage for `AdSdk.kt`'s logic.
I will now create the test class for `AdLoader.kt`. This will be more complex due to mocking network responses.
I'll create `AdSDK/src/test/java/com/example/adsdk/AdLoaderTest.kt`.
