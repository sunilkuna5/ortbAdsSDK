package com.example.adsdk

enum class LogLevel {
    NONE,  // No logs
    ERROR, // Only errors
    WARNING, // Errors and warnings
    INFO,  // Errors, warnings, and informational logs (default)
    DEBUG, // Detailed logs for debugging
    VERBOSE // Most detailed logs
}
