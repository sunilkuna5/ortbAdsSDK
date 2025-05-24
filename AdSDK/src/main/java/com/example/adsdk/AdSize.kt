package com.example.adsdk

import android.content.Context
import android.util.DisplayMetrics

/**
 * Represents the size of an ad in density-independent pixels (dp).
 */
data class AdSize(val widthDp: Int, val heightDp: Int) {

    fun getWidthPx(context: Context): Int {
        return dpToPx(context, widthDp)
    }

    fun getHeightPx(context: Context): Int {
        return dpToPx(context, heightDp)
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }

    companion object {
        @JvmField val BANNER_320X50 = AdSize(320, 50)
        @JvmField val LARGE_BANNER_320X100 = AdSize(320, 100)
        @JvmField val MEDIUM_RECTANGLE_300X250 = AdSize(300, 250)
        @JvmField val FULL_BANNER_468X60 = AdSize(468, 60)
        @JvmField val LEADERBOARD_728X90 = AdSize(728, 90)
        // Common alias for default banner size
        @JvmField val BANNER = BANNER_320X50 

        fun fromString(sizeString: String?): AdSize {
            return when (sizeString?.uppercase()?.replace("X", "X")) { // Normalize 'x' to 'X'
                "BANNER_320X50" -> BANNER_320X50
                "LARGE_BANNER_320X100" -> LARGE_BANNER_320X100
                "MEDIUM_RECTANGLE_300X250" -> MEDIUM_RECTANGLE_300X250
                "FULL_BANNER_468X60" -> FULL_BANNER_468X60
                "LEADERBOARD_728X90" -> LEADERBOARD_728X90
                "BANNER" -> BANNER_320X50 // Alias
                else -> BANNER_320X50 // Default or throw IllegalArgumentException("Unknown AdSize string: $sizeString")
            }
        }
    }
}
