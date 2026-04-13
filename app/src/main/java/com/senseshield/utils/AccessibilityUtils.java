package com.senseshield.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.provider.Settings;
import android.view.View;

/**
 * AccessibilityUtils.java
 * Helper methods for accessibility compliance throughout SenseShield.
 *
 * This class ensures we honour:
 *   - System animation scale (users who disable animations)
 *   - Font scale (users who increase system text size)
 *   - TalkBack screen reader support
 */
public class AccessibilityUtils {

    /**
     * Returns true if the user has disabled or reduced animations at the system level.
     * Always check this before running ANY animation in the app.
     *
     * Usage:
     *   if (!AccessibilityUtils.areAnimationsEnabled(context)) {
     *       // skip animation, show final state immediately
     *   }
     */
    public static boolean areAnimationsEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            float scale = Settings.Global.getFloat(
                context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            );
            return scale > 0f;
        }
        return true; // Assume enabled on older devices
    }

    /**
     * Returns the user's system font scale factor.
     * SenseShield uses sp for all text, so Android handles this automatically.
     * Use this only if you need to manually size something based on font scale.
     */
    public static float getFontScale(Context context) {
        return context.getResources().getConfiguration().fontScale;
    }

    /**
     * Returns true if the device is currently in dark mode.
     */
    public static boolean isDarkMode(Context context) {
        int uiMode = context.getResources().getConfiguration().uiMode
                     & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Sets a content description on a view ONLY if one isn't already set.
     * Prevents accidental overwriting of descriptions set in XML.
     */
    public static void setContentDescriptionIfMissing(View view, String description) {
        if (view.getContentDescription() == null
                || view.getContentDescription().toString().isEmpty()) {
            view.setContentDescription(description);
        }
    }

    /**
     * Announces a message to TalkBack (AccessibilityEvent TYPE_ANNOUNCEMENT).
     * Use this for dynamic state changes that TalkBack won't pick up automatically,
     * e.g. "Breathing exercise started" when the calm screen appears.
     */
    public static void announce(View view, String message) {
        view.announceForAccessibility(message);
    }

    // Prevent instantiation
    private AccessibilityUtils() {}
}
