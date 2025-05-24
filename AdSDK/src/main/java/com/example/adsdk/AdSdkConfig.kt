package com.example.adsdk

data class AdSdkConfig(
    val apiKey: String,
    val testMode: Boolean = false,
    val logLevel: LogLevel = LogLevel.INFO,
    val publisherId: String? = null // Example of another potential global setting
)
