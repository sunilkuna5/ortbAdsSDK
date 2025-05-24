package com.example.adsdksample

import android.os.IBinder
import android.view.WindowManager
import androidx.test.espresso.Root
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher

// Custom Matcher for Espresso to check Toasts
class ToastMatcher : TypeSafeMatcher<Root>() {

    override fun describeTo(description: Description) {
        description.appendText("is toast")
    }

    override fun matchesSafely(root: Root): Boolean {
        val type = root.windowLayoutParams.get().type
        if (type == WindowManager.LayoutParams.TYPE_TOAST || type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
            val windowToken: IBinder = root.decorView.windowToken
            val appToken: IBinder = root.decorView.applicationWindowToken
            if (windowToken === appToken) {
                // Means window isn't contained by any other window.
                return true
            }
        }
        return false
    }
}
