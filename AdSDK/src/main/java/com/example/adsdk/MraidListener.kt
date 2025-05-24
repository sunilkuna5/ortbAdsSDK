package com.example.adsdk

interface MraidListener {
    fun onMraidReady(mraidWebView: MraidWebView)
    fun onMraidOpen(mraidWebView: MraidWebView, url: String)
    fun onMraidClose(mraidWebView: MraidWebView)
    fun onMraidExpand(mraidWebView: MraidWebView, url: String?) // url is for two-part
    fun onMraidResize(
        mraidWebView: MraidWebView,
        width: Int,
        height: Int,
        offsetX: Int,
        offsetY: Int,
        customClosePosition: String, // e.g., "top-right"
        allowOffscreen: Boolean
    )
    fun onMraidPlayVideo(mraidWebView: MraidWebView, url: String)
    fun onMraidStorePicture(mraidWebView: MraidWebView, url: String)
    fun onMraidCreateCalendarEvent(mraidWebView: MraidWebView, eventJson: String)
    fun onMraidSetUseCustomClose(mraidWebView: MraidWebView, useCustomClose: Boolean)
    fun onMraidSetOrientationProperties(mraidWebView: MraidWebView, allowOrientationChange: Boolean, forceOrientation: String)
    fun onMraidLocation(mraidWebView: MraidWebView, lat: Double, lon: Double, accuracy: Float, lastFix: Long) // MRAID 3.0
    fun onMraidError(mraidWebView: MraidWebView, message: String, action: String)
}
