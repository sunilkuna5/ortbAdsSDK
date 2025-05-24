package com.example.adsdk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class AdLoaderTest {

    private lateinit var adLoader: AdLoader
    private lateinit var mockClient: OkHttpClient
    private lateinit var mockCall: Call
    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager
    private lateinit var mockAppInfo: ApplicationInfo
    private lateinit var mockPackageInfo: PackageInfo
    private lateinit var mockWindowManager: WindowManager
    private lateinit var mockDisplay: android.view.Display // Correct type for defaultDisplay

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @BeforeEach
    fun setUp() {
        mockClient = mock()
        mockCall = mock()
        mockContext = mock()
        mockPackageManager = mock()
        mockAppInfo = mock()
        mockPackageInfo = PackageInfo().apply { versionName = "1.0.0" } // Initialize with non-null versionName
        mockWindowManager = mock()
        mockDisplay = mock() // Mock for android.view.Display

        whenever(mockContext.applicationContext).thenReturn(mockContext) // Return self for simplicity
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockContext.packageName).thenReturn("com.example.testapp")
        whenever(mockPackageManager.getApplicationInfo(any(), eq(0))).thenReturn(mockAppInfo)
        whenever(mockAppInfo.loadLabel(any())).thenReturn("TestApp")
        whenever(mockPackageManager.getPackageInfo(any(), eq(0))).thenReturn(mockPackageInfo)
        whenever(mockContext.getSystemService(Context.WINDOW_SERVICE)).thenReturn(mockWindowManager)
        whenever(mockWindowManager.defaultDisplay).thenReturn(mockDisplay) // Return the mocked Display

        // Mock DisplayMetrics - doAnswer used to simulate getMetrics behavior
        doAnswer { invocation ->
            val metrics = invocation.getArgument<DisplayMetrics>(0)
            metrics.widthPixels = 1080
            metrics.heightPixels = 1920
            metrics.density = 2.0f
            null // Must return Unit or null for a void method
        }.whenever(mockDisplay).getMetrics(any())


        // Mock Settings.Secure.ANDROID_ID
        // This requires Robolectric or PowerMockito to mock static methods.
        // For now, we'll assume it returns a test value or is handled by AdLoader if null.
        // To avoid static mocking, AdLoader could have a injectable "DeviceIdProvider".
        // For this test, we'll let it be null and see how AdLoader handles it (should use a placeholder).

        // Initialize AdSdk (minimal)
        resetAdSdkSingleton() // Reset AdSdk state
        AdSdk.initialize(mockContext, AdSdkConfig(apiKey = "test-sdk-key", testMode = true))

        adLoader = AdLoader()

        // Reflection to set the private OkHttpClient field in AdLoader
        val clientField = AdLoader::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(adLoader, mockClient)

        whenever(mockClient.newCall(any())).thenReturn(mockCall)
    }
    
    @AfterEach
    fun tearDown() {
        resetAdSdkSingleton()
    }

    private fun createAdRequest(format: AdFormat, widthPx: Int? = null, heightPx: Int? = null): AdRequest {
        return AdRequest(
            adUnitId = "test-ad-unit",
            format = format,
            userId = "test-user",
            widthPx = widthPx,
            heightPx = heightPx
        )
    }
    
    private fun simulateHttpError(errorCode: Int, errorMessage: String, responseBodyString: String = "") {
         val responseBody = responseBodyString.toResponseBody("application/json".toMediaTypeOrNull())
         val response = Response.Builder()
            .request(Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(errorCode)
            .message(errorMessage)
            .body(responseBody)
            .build()

        doAnswer { invocation ->
            val callback = invocation.getArgument<Callback>(0)
            callback.onResponse(mockCall, response)
            null
        }.whenever(mockCall).enqueue(any())
    }

    private fun simulateNetworkError(exception: IOException = IOException("Network failure")) {
         doAnswer { invocation ->
            val callback = invocation.getArgument<Callback>(0)
            callback.onFailure(mockCall, exception)
            null
        }.whenever(mockCall).enqueue(any())
    }

    private fun simulateSuccessHttpResponse(jsonBody: String) {
        val responseBody = jsonBody.toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.Builder()
            .request(Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        doAnswer { invocation ->
            val callback = invocation.getArgument<Callback>(0)
            callback.onResponse(mockCall, response)
            null
        }.whenever(mockCall).enqueue(any())
    }
    
    private fun simulateNoBidHttpResponse() { // HTTP 204
        val response = Response.Builder()
            .request(Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(204) // No Content
            .message("No Content")
            .body("".toResponseBody(null)) // Empty body
            .build()

        doAnswer { invocation ->
            val callback = invocation.getArgument<Callback>(0)
            callback.onResponse(mockCall, response)
            null
        }.whenever(mockCall).enqueue(any())
    }


    @Nested
    inner class RequestConstruction {
        @Test
        fun `buildOpenRTBRequest for BANNER should populate fields correctly`() {
            val adRequest = createAdRequest(AdFormat.BANNER, widthPx = 320, heightPx = 50)
            
            // Access private method via reflection for testing (not ideal, but common for complex units)
            val method = AdLoader::class.java.getDeclaredMethod("buildOpenRTBRequest", AdRequest::class.java, Context::class.java)
            method.isAccessible = true
            val ortbRequest = method.invoke(adLoader, adRequest, mockContext) as OpenRTBRequest

            assertThat(ortbRequest.app.bundle).isEqualTo("com.example.testapp")
            assertThat(ortbRequest.app.name).isEqualTo("TestApp")
            assertThat(ortbRequest.device.os).isEqualTo("Android")
            assertThat(ortbRequest.device.osv).isEqualTo(Build.VERSION.RELEASE) // from test environment
            assertThat(ortbRequest.imp).hasSize(1)
            val impression = ortbRequest.imp[0]
            assertThat(impression.tagId).isEqualTo("test-ad-unit")
            assertThat(impression.banner).isNotNull
            assertThat(impression.banner?.w).isEqualTo(320)
            assertThat(impression.banner?.h).isEqualTo(50)
            assertThat(impression.banner?.format?.get(0)?.w).isEqualTo(320)
            assertThat(impression.banner?.format?.get(0)?.h).isEqualTo(50)
            assertThat(impression.instl).isEqualTo(0)
            assertThat(ortbRequest.user?.id).isEqualTo("test-user")
            assertThat(ortbRequest.test).isEqualTo(1) // From AdSdkConfig.testMode
        }

        @Test
        fun `buildOpenRTBRequest for INTERSTITIAL should populate fields correctly`() {
            val adRequest = createAdRequest(AdFormat.INTERSTITIAL) // width/height derived by AdLoader
            val method = AdLoader::class.java.getDeclaredMethod("buildOpenRTBRequest", AdRequest::class.java, Context::class.java)
            method.isAccessible = true
            val ortbRequest = method.invoke(adLoader, adRequest, mockContext) as OpenRTBRequest

            assertThat(ortbRequest.imp).hasSize(1)
            val impression = ortbRequest.imp[0]
            assertThat(impression.instl).isEqualTo(1)
            assertThat(impression.banner).isNotNull // Interstitial often requested as a full-screen banner
            assertThat(impression.banner?.w).isEqualTo(1080) // From mocked DisplayMetrics
            assertThat(impression.banner?.h).isEqualTo(1920) // From mocked DisplayMetrics
            assertThat(impression.banner?.pos).isEqualTo(AdPosition.FULL_SCREEN)
        }

        @Test
        fun `buildOpenRTBRequest for NATIVE should populate native structure`() {
            val adRequest = createAdRequest(AdFormat.NATIVE)
            val method = AdLoader::class.java.getDeclaredMethod("buildOpenRTBRequest", AdRequest::class.java, Context::class.java)
            method.isAccessible = true
            val ortbRequest = method.invoke(adLoader, adRequest, mockContext) as OpenRTBRequest

            assertThat(ortbRequest.imp).hasSize(1)
            val impression = ortbRequest.imp[0]
            assertThat(impression.nativeAd).isNotNull
            assertThat(impression.banner).isNull()
            val nativeRequest = moshi.adapter(NativeMarkupRequest::class.java).fromJson(impression.nativeAd!!.request)
            assertThat(nativeRequest).isNotNull
            assertThat(nativeRequest!!.ver).isEqualTo("1.2")
            assertThat(nativeRequest.assets).isNotEmpty
            // Check for a few expected assets (IDs might vary if not using NativeAssetId constants)
            assertThat(nativeRequest.assets.any { it.title != null && it.id == 1 }).isTrue()
            assertThat(nativeRequest.assets.any { it.img != null && it.img.type == NativeImageAssetType.MAIN && it.id == 2 }).isTrue()
        }
    }

    @Nested
    inner class ResponseHandling {
        private lateinit var mockListener: AdLoader.AdLoadListener

        @BeforeEach
        fun listenerSetup() {
            mockListener = mock()
        }

        @Test
        fun `onAdLoaded called for successful BANNER response`() {
            val adRequest = createAdRequest(AdFormat.BANNER, 320, 50)
            val bidAdm = "<html><body>Banner Ad</body></html>"
            val ortbResponseJson = """
                {
                    "id": "response-id", "cur": "USD",
                    "seatbid": [{
                        "bid": [{
                            "id": "bid-id", "impid": "imp-id", "price": 0.1,
                            "adm": "${bidAdm.replace("\"", "\\\"")}", 
                            "w": 320, "h": 50,
                            "ext": { "prebid": { "type": "banner" } }
                        }]
                    }]
                }
            """.trimIndent()
            simulateSuccessHttpResponse(ortbResponseJson)

            adLoader.loadAd(adRequest, mockListener)

            val adResponseCaptor = argumentCaptor<AdResponse>()
            verify(mockListener).onAdLoaded(adResponseCaptor.capture())
            val capturedResponse = adResponseCaptor.firstValue
            assertThat(capturedResponse.creativePayload).isEqualTo(bidAdm)
            assertThat(capturedResponse.adType).isEqualTo(AdType.HTML)
            assertThat(capturedResponse.width).isEqualTo(320)
            assertThat(capturedResponse.height).isEqualTo(50)
        }
        
        @Test
        fun `onAdLoaded called for successful NATIVE response`() {
            val adRequest = createAdRequest(AdFormat.NATIVE)
            // This is the 'adm' field, which is a stringified JSON of NativeAdMarkup
            val nativeMarkupJson = """
                {
                    "assets": [{"id":1, "required":1, "title":{"text":"Native Ad Title"}}],
                    "link": {"url":"http://example.com/click"},
                    "imptrackers":["http://imptracker.com"]
                }
            """.trimIndent()

            val ortbResponseJson = """
                {
                    "id": "response-id", "cur": "USD",
                    "seatbid": [{
                        "bid": [{
                            "id": "bid-id", "impid": "imp-id", "price": 0.1,
                            "adm": "${nativeMarkupJson.replace("\"", "\\\"")}",
                             "ext": { "prebid": { "type": "native" } } 
                        }]
                    }]
                }
            """.trimIndent()
             // In AdLoader, if ext.sdkNativePayload is null, it tries to parse adm as NativeAdMarkup if format is NATIVE.
            simulateSuccessHttpResponse(ortbResponseJson)

            adLoader.loadAd(adRequest, mockListener)

            val adResponseCaptor = argumentCaptor<AdResponse>()
            verify(mockListener).onAdLoaded(adResponseCaptor.capture())
            val capturedResponse = adResponseCaptor.firstValue
            assertThat(capturedResponse.creativePayload).isEqualTo(nativeMarkupJson)
            assertThat(capturedResponse.adType).isEqualTo(AdType.NATIVE)
        }


        @Test
        fun `onError called for HTTP 204 No Bid response`() {
            val adRequest = createAdRequest(AdFormat.BANNER)
            simulateNoBidHttpResponse()
            adLoader.loadAd(adRequest, mockListener)
            
            val errorCaptor = argumentCaptor<AdError>()
            verify(mockListener).onError(errorCaptor.capture())
            assertThat(errorCaptor.firstValue.errorCode).isEqualTo(AdErrorCodes.NO_FILL)
        }
        
        @Test
        fun `onError called for No-Bid Reason (NBR) in response`() {
            val adRequest = createAdRequest(AdFormat.BANNER)
            val ortbResponseJson = """{"id": "response-id", "nbr": ${NoBidReasonCode.TECHNICAL_ERROR}}"""
            simulateSuccessHttpResponse(ortbResponseJson)

            adLoader.loadAd(adRequest, mockListener)

            val errorCaptor = argumentCaptor<AdError>()
            verify(mockListener).onError(errorCaptor.capture())
            assertThat(errorCaptor.firstValue.errorCode).isEqualTo(AdErrorCodes.SERVER_ERROR) // Mapped from NBR
        }

        @Test
        fun `onError called for server error HTTP 500`() {
            val adRequest = createAdRequest(AdFormat.BANNER)
            simulateHttpError(500, "Internal Server Error", "Server blew up")
            adLoader.loadAd(adRequest, mockListener)

            val errorCaptor = argumentCaptor<AdError>()
            verify(mockListener).onError(errorCaptor.capture())
            assertThat(errorCaptor.firstValue.errorCode).isEqualTo(500)
            assertThat(errorCaptor.firstValue.errorMessage).contains("Server error: Internal Server Error - Server blew up")
        }

        @Test
        fun `onError called for network IOException`() {
            val adRequest = createAdRequest(AdFormat.BANNER)
            simulateNetworkError(IOException("No internet"))
            adLoader.loadAd(adRequest, mockListener)

            val errorCaptor = argumentCaptor<AdError>()
            verify(mockListener).onError(errorCaptor.capture())
            assertThat(errorCaptor.firstValue.errorCode).isEqualTo(AdErrorCodes.NETWORK_ERROR)
            assertThat(errorCaptor.firstValue.errorMessage).isEqualTo("No internet")
        }
        
        @Test
        fun `onError called for invalid JSON response`() {
            val adRequest = createAdRequest(AdFormat.BANNER)
            simulateSuccessHttpResponse("This is not JSON")
            adLoader.loadAd(adRequest, mockListener)

            val errorCaptor = argumentCaptor<AdError>()
            verify(mockListener).onError(errorCaptor.capture())
            assertThat(errorCaptor.firstValue.errorCode).isEqualTo(AdErrorCodes.SERVER_ERROR)
            assertThat(errorCaptor.firstValue.errorMessage).contains("Error processing ad response")
        }
        
        @Test
        fun `loadAd when SDK not initialized should call onError`() {
            resetAdSdkSingleton() // Ensure AdSdk is not initialized
            val adRequest = createAdRequest(AdFormat.BANNER)
            adLoader.loadAd(adRequest, mockListener)

            val errorCaptor = argumentCaptor<AdError>()
            verify(mockListener).onError(errorCaptor.capture())
            assertThat(errorCaptor.firstValue.errorCode).isEqualTo(AdErrorCodes.SDK_NOT_INITIALIZED)
        }
    }
}
```

**Key aspects of `AdLoaderTest.kt`:**
-   **Mocking**: Mocks `OkHttpClient`, `Call`, `Context`, `PackageManager`, etc.
-   **Reflection for `OkHttpClient`**: Since `AdLoader` instantiates its own `OkHttpClient`, reflection is used to inject a mock client. This is a common pattern for testing classes with hard-to-mock internal dependencies. A cleaner way would be dependency injection into `AdLoader`.
-   **Simulating HTTP Responses**: Helper methods (`simulateHttpError`, `simulateNetworkError`, `simulateSuccessHttpResponse`, `simulateNoBidHttpResponse`) are used to control the behavior of the mocked `OkHttpClient` and its `Call`.
-   **Testing Request Construction**:
    -   Uses reflection to call the private `buildOpenRTBRequest` method. This allows testing this core logic unit directly.
    -   Verifies key fields in the generated `OpenRTBRequest` for different ad formats.
    -   Mocks Android framework classes (`Context`, `PackageManager`, `DisplayMetrics`) to provide necessary data for request building.
-   **Testing Response Handling**:
    -   Tests various scenarios: successful banner ad, successful native ad, HTTP 204 No Bid, NBR in response, server error, network error, invalid JSON.
    -   Uses `argumentCaptor` to capture arguments passed to `AdLoadListener` methods and assert their values.
-   **SDK Initialization Check**: Tests that `AdLoader` calls `onError` if the SDK is not initialized.
-   **`@AfterEach` and `resetAdSdkSingleton()`**: Ensures `AdSdk` state is reset, crucial because `AdLoader` depends on `AdSdk.isInitialized()` and `AdSdk.isTestMode()`.

This provides a good set of tests for `AdLoader.kt`. The main compromise is using reflection for the `OkHttpClient` and `buildOpenRTBRequest`.

Next, I will create tests for `MraidJsInterface.kt`.
I'll create `AdSDK/src/test/java/com/example/adsdk/MraidJsInterfaceTest.kt`.
