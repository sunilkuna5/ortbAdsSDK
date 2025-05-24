package com.example.adsdk

import android.os.Handler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.json.JSONObject

// To test methods that post to Handler, we need Robolectric or a way to manage Loopers.
// For simplicity, if Robolectric isn't used, one might need to inject a TestCoroutineDispatcher
// or use a library that manages Android's main looper in tests.
// Here, I'll assume we can manage the Looper for Handler posts or that immediate execution occurs.
// Let's try with a simple Looper setup that might work for basic Handler posts.
// If not, Robolectric would be needed.

// For testing classes using Android's Handler, ShadowLooper from Robolectric is helpful.
// If we want to avoid full Robolectric, we can try to ensure Handler posts run immediately.
// One way is to mock Handler to execute posted runnables immediately.

class MraidJsInterfaceTest {

    private lateinit var mraidJsInterface: MraidJsInterface
    private lateinit var mockMraidWebView: MraidWebView
    private lateinit var mockMraidListener: MraidListener
    private lateinit var mockHandler: Handler

    @BeforeEach
    fun setUp() {
        mockMraidWebView = mock()
        mockMraidListener = mock()
        mockHandler = mock()

        // Make the mockHandler execute runnables immediately
        whenever(mockHandler.post(any())).thenAnswer {
            it.getArgument<Runnable>(0).run()
            true
        }

        mraidJsInterface = MraidJsInterface(mockMraidWebView, MraidPlacementType.INLINE)
        mraidJsInterface.mraidWebView.mraidListener = mockMraidListener // Set listener via MraidWebView

        // Replace the real Handler in MraidJsInterface with the mock one using reflection
        val handlerField = MraidJsInterface::class.java.getDeclaredField("handler")
        handlerField.isAccessible = true
        handlerField.set(mraidJsInterface, mockHandler)
        
        // Initial state
        mraidJsInterface.updateState(MraidState.LOADING) // Set initial state for tests
    }

    @Nested
    inner class MraidCommands {

        @Test
        fun `open should call listener onMraidOpen`() {
            val url = "http://example.com"
            mraidJsInterface.open(url)
            verify(mockMraidListener).onMraidOpen(mockMraidWebView, url)
        }

        @Test
        fun `close from DEFAULT state should update state to HIDDEN and call listener`() {
            mraidJsInterface.updateState(MraidState.DEFAULT)
            mraidJsInterface.close()
            assertThat(mraidJsInterface.state).isEqualTo(MraidState.HIDDEN)
            verify(mockMraidWebView).fireStateChangeEvent(MraidState.HIDDEN)
            verify(mockMraidListener).onMraidClose(mockMraidWebView)
        }

        @Test
        fun `close from EXPANDED state should update state to DEFAULT and call listener`() {
            mraidJsInterface.updateState(MraidState.EXPANDED)
            mraidJsInterface.close()
            assertThat(mraidJsInterface.state).isEqualTo(MraidState.DEFAULT)
            verify(mockMraidWebView).fireStateChangeEvent(MraidState.DEFAULT)
            verify(mockMraidListener).onMraidClose(mockMraidWebView)
        }
        
        @Test
        fun `close from RESIZED state should update state to DEFAULT and call listener`() {
            mraidJsInterface.updateState(MraidState.RESIZED)
            mraidJsInterface.close()
            assertThat(mraidJsInterface.state).isEqualTo(MraidState.DEFAULT)
            verify(mockMraidWebView).fireStateChangeEvent(MraidState.DEFAULT)
            verify(mockMraidListener).onMraidClose(mockMraidWebView)
        }


        @Test
        fun `expand without URL should call listener onMraidExpand and update state`() {
            mraidJsInterface.updateState(MraidState.DEFAULT)
            mraidJsInterface.expand(null)
            assertThat(mraidJsInterface.state).isEqualTo(MraidState.EXPANDED)
            verify(mockMraidWebView).fireStateChangeEvent(MraidState.EXPANDED)
            verify(mockMraidListener).onMraidExpand(mockMraidWebView, null)
        }
        
        @Test
        fun `expand with URL should call listener onMraidExpand and update state`() {
            mraidJsInterface.updateState(MraidState.DEFAULT)
            val url = "http://two.part.expand"
            mraidJsInterface.expand(url)
            assertThat(mraidJsInterface.state).isEqualTo(MraidState.EXPANDED)
            verify(mockMraidWebView).fireStateChangeEvent(MraidState.EXPANDED)
            verify(mockMraidListener).onMraidExpand(mockMraidWebView, url)
        }

        @Test
        fun `expand not from DEFAULT or RESIZED state should fire error`() {
            mraidJsInterface.updateState(MraidState.LOADING)
            mraidJsInterface.expand(null)
            verify(mockMraidWebView).fireErrorEvent(any(), eq("expand"))
            assertThat(mraidJsInterface.state).isEqualTo(MraidState.LOADING) // State should not change
        }
        
        @Test
        fun `expand for INTERSTITIAL placement should fire error`() {
            val interstitialInterface = MraidJsInterface(mockMraidWebView, MraidPlacementType.INTERSTITIAL)
            interstitialInterface.mraidWebView.mraidListener = mockMraidListener
            val handlerField = MraidJsInterface::class.java.getDeclaredField("handler")
            handlerField.isAccessible = true
            handlerField.set(interstitialInterface, mockHandler)
            interstitialInterface.updateState(MraidState.DEFAULT)

            interstitialInterface.expand(null)
            verify(mockMraidWebView).fireErrorEvent(any(), eq("expand"))
        }


        @Test
        fun `resize should call listener and update state`() {
            mraidJsInterface.updateState(MraidState.DEFAULT)
            val props = """{"width":300, "height":250, "offsetX":10, "offsetY":10, "customClosePosition":"top-right", "allowOffscreen":false}"""
            mraidJsInterface.resize(props)

            assertThat(mraidJsInterface.state).isEqualTo(MraidState.RESIZED)
            verify(mockMraidWebView).fireStateChangeEvent(MraidState.RESIZED)
            verify(mockMraidListener).onMraidResize(mockMraidWebView, 300, 250, 10, 10, "top-right", false)
        }
        
        @Test
        fun `resize with invalid JSON should fire error`() {
            mraidJsInterface.updateState(MraidState.DEFAULT)
            mraidJsInterface.resize("invalid json")
            verify(mockMraidWebView).fireErrorEvent(any(), eq("resize"))
            assertThat(mraidJsInterface.state).isEqualTo(MraidState.DEFAULT) // State should not change
        }

        @Test
        fun `storePicture should call listener`() {
            val url = "http://image.com/img.png"
            mraidJsInterface.storePicture(url)
            verify(mockMraidListener).onMraidStorePicture(mockMraidWebView, url)
        }

        @Test
        fun `createCalendarEvent should call listener`() {
            val eventJson = "{event: 'details'}"
            mraidJsInterface.createCalendarEvent(eventJson)
            verify(mockMraidListener).onMraidCreateCalendarEvent(mockMraidWebView, eventJson)
        }

        @Test
        fun `playVideo should call listener`() {
            val url = "http://video.com/vid.mp4"
            mraidJsInterface.playVideo(url)
            verify(mockMraidListener).onMraidPlayVideo(mockMraidWebView, url)
        }

        @Test
        fun `useCustomClose should call listener`() {
            mraidJsInterface.useCustomClose(true)
            verify(mockMraidListener).onMraidSetUseCustomClose(mockMraidWebView, true)
        }
        
        @Test
        fun `setOrientationProperties should call listener`() {
            val properties = """{"allowOrientationChange":true, "forceOrientation":"landscape"}"""
            mraidJsInterface.setOrientationProperties(properties)
            verify(mockMraidListener).onMraidSetOrientationProperties(mockMraidWebView, true, "landscape")
        }
    }

    @Nested
    inner class PropertyGetters {
        @Test
        fun `getPlacementType should return correct type`() {
            assertThat(mraidJsInterface.getPlacementType()).isEqualTo(MraidPlacementType.INLINE)
            val interstitialInterface = MraidJsInterface(mockMraidWebView, MraidPlacementType.INTERSTITIAL)
            assertThat(interstitialInterface.getPlacementType()).isEqualTo(MraidPlacementType.INTERSTITIAL)
        }

        @Test
        fun `getState should return current state`() {
            mraidJsInterface.updateState(MraidState.EXPANDED)
            assertThat(mraidJsInterface.getState()).isEqualTo(MraidState.EXPANDED)
        }

        @Test
        fun `isViewable should return current viewability`() {
            mraidJsInterface.updateViewability(true)
            assertThat(mraidJsInterface.isViewable()).isTrue()
            mraidJsInterface.updateViewability(false)
            assertThat(mraidJsInterface.isViewable()).isFalse()
        }

        @Test
        fun `getVersion should return 3 dot 0`() {
            assertThat(mraidJsInterface.getVersion()).isEqualTo("3.0")
        }

        @Test
        fun `supports should return true for known features and false otherwise`() {
            assertThat(mraidJsInterface.supports("sms")).isTrue()
            assertThat(mraidJsInterface.supports("tel")).isTrue()
            assertThat(mraidJsInterface.supports("calendar")).isTrue()
            assertThat(mraidJsInterface.supports("storePicture")).isTrue()
            assertThat(mraidJsInterface.supports("inlineVideo")).isTrue()
            assertThat(mraidJsInterface.supports("location")).isTrue()
            assertThat(mraidJsInterface.supports("vpaid")).isFalse() // VPAID not supported by default
            assertThat(mraidJsInterface.supports("unknownFeature")).isFalse()
        }
        
        @Test
        fun `getAudibility should return correct audibility state`() {
            mraidJsInterface.updateAudibility(true)
            assertThat(mraidJsInterface.getAudibility()).isEqualTo(MraidAudibility.AUDIBLE)
            mraidJsInterface.updateAudibility(false)
            assertThat(mraidJsInterface.getAudibility()).isEqualTo(MraidAudibility.NOT_AUDIBLE)
        }
    }
    
    @Nested
    inner class PropertyUpdates {
        @Test
        fun `updateState should change state and fire event`() {
            mraidJsInterface.updateState(MraidState.DEFAULT) // initial
            mraidJsInterface.updateState(MraidState.EXPANDED)
            assertThat(mraidJsInterface.state).isEqualTo(MraidState.EXPANDED)
            verify(mockMraidWebView).fireStateChangeEvent(MraidState.EXPANDED)
        }

        @Test
        fun `updateViewability should change viewable flag and fire event if changed`() {
            mraidJsInterface.updateViewability(false) // initial
            mraidJsInterface.updateViewability(true)
            assertThat(mraidJsInterface.isViewable).isTrue()
            verify(mockMraidWebView).fireViewableChangeEvent(true)

            // Call again with same value, should not fire event again
            mraidJsInterface.updateViewability(true)
            verifyNoMoreInteractions(mockMraidWebView) // Or verify(mockMraidWebView, times(1)).fireViewableChangeEvent(true)
        }
        
        @Test
        fun `updateAudibility should change audible flag and fire event if changed`() {
            mraidJsInterface.updateAudibility(true) // initial
            mraidJsInterface.updateAudibility(false)
            assertThat(mraidJsInterface.isAudible).isFalse()
            verify(mockMraidWebView).fireAudibleChangeEvent(false)

            mraidJsInterface.updateAudibility(false) // No change
            verifyNoMoreInteractions(mockMraidWebView)
        }

        @Test
        fun `updateScreenMetrics should update all metrics and fire sizeChange if currentPosition size changes`() {
            val initialCurrentPos = MraidPosition(0,0,100,100)
            val initialDefaultPos = MraidPosition(0,0,100,100)
            val initialMaxSize = MraidScreenSize(800,600)
            val initialScreenSize = MraidScreenSize(800,600)
            mraidJsInterface.updateScreenMetrics(initialCurrentPos, initialDefaultPos, initialMaxSize, initialScreenSize)
            // Should fire once due to initial setup if width/height of currentPosition changed from 0,0 to 100,100
            verify(mockMraidWebView).fireSizeChangeEvent(100, 100) 

            val newCurrentPos = MraidPosition(10,10,200,200) // Width/Height changed
            val newDefaultPos = MraidPosition(0,0,100,100) // No change
            val newMaxSize = MraidScreenSize(1024,768)    // Changed
            val newScreenSize = MraidScreenSize(1024,768) // Changed
            
            mraidJsInterface.updateScreenMetrics(newCurrentPos, newDefaultPos, newMaxSize, newScreenSize)

            assertThat(mraidJsInterface.currentPosition).isEqualTo(newCurrentPos)
            assertThat(mraidJsInterface.defaultPosition).isEqualTo(newDefaultPos)
            assertThat(mraidJsInterface.maxSize).isEqualTo(newMaxSize)
            assertThat(mraidJsInterface.screenSize).isEqualTo(newScreenSize)
            
            // Fire size change again because currentPosition width/height changed
            verify(mockMraidWebView).fireSizeChangeEvent(200, 200) 
            verify(mockMraidWebView, times(2)).fireSizeChangeEvent(any(), any()) // Called twice in total
        }
    }
    
    @Test
    fun `mraidJsLoaded should call MraidWebView onMraidJsLoaded`() {
        mraidJsInterface.mraidJsLoaded()
        verify(mockMraidWebView).onMraidJsLoaded()
    }
}

```

**Key aspects of `MraidJsInterfaceTest.kt`:**
-   **Mocking `Handler`**: A mock `Handler` is injected into `MraidJsInterface` using reflection. This mock is configured to execute posted `Runnable`s immediately, simplifying testing of methods that use `handler.post { ... }`. Without this (or Robolectric's `ShadowLooper`), testing those posted actions would be more complex.
-   **Listener Verification**: Most tests verify that the correct methods on `mockMraidListener` (or `mockMraidWebView` for event firing) are called with the expected arguments.
-   **State Verification**: Tests check that the internal `state` of `MraidJsInterface` is updated correctly by commands like `close()`, `expand()`, `resize()`.
-   **Property Getters**: Tests for methods like `getState()`, `isViewable()`, etc., ensure they return the current values.
-   **Error Conditions**: Tests for `expand()` and `resize()` include scenarios where they should fire error events (e.g., invalid state, interstitial placement).
-   **Placement Type**: Checks that `expand` and `resize` are correctly disabled for `INTERSTITIAL` placement.
-   **JSON Parsing**: `resize` and `setOrientationProperties` test both valid and invalid JSON string inputs.
-   **`updateScreenMetrics`**: Verifies that all metric properties are updated and that `fireSizeChangeEvent` is called when `currentPosition`'s dimensions change.

This test suite covers the core MRAID command handling logic, state transitions, and property management within `MraidJsInterface`.

I'll now prepare the tests for `MraidWebView.kt`. These will be more challenging due to its nature as a View and its reliance on WebKit. We'll focus on non-rendering logic and interactions with `MraidJsInterface` and `MraidListener`. Robolectric might be more suitable for thorough testing of `MraidWebView`, but I will attempt to create meaningful unit tests with Mockito first.

I'll create `AdSDK/src/test/java/com/example/adsdk/MraidWebViewTest.kt`.
