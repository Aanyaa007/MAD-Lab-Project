package com.senseshield;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.senseshield.utils.AppConstants;

/**
 * SenseShieldApp.java
 * Application class — runs once when the app process starts.
 * Responsibilities:
 *   1. Create notification channels
 *   2. Apply the saved theme globally (before any Activity inflates)
 *   3. Provide a static context accessor for singletons (DatabaseHelper, etc.)
 */
public class SenseShieldApp extends Application {

    private static SenseShieldApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannels();
    }

    // ─── Static accessor ─────────────────────────────────────────────────────────

    public static SenseShieldApp getInstance() {
        return instance;
    }

    // ─── Theme resolution ────────────────────────────────────────────────────────

    /**
     * Returns the correct style resource for the saved theme preference.
     * Call this in every Activity's onCreate() BEFORE setContentView().
     *
     * Usage:
     *   setTheme(SenseShieldApp.getThemeResId(this));
     */
    public static int getThemeResId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        int theme = prefs.getInt(AppConstants.PREF_THEME, AppConstants.THEME_STANDARD);

        switch (theme) {
            case AppConstants.THEME_WARM_DIM:      return R.style.Theme_SenseShield_Warm;
            case AppConstants.THEME_COOL_MUTED:    return R.style.Theme_SenseShield_Cool;
            case AppConstants.THEME_HIGH_CONTRAST: return R.style.Theme_SenseShield_HighContrast;
            default:                               return R.style.Theme_SenseShield;
        }
    }

    /**
     * Returns the correct dialog theme overlay for the saved theme preference.
     * Pass this as the second argument to MaterialAlertDialogBuilder so dialogs
     * always match the active theme instead of defaulting to Material's dark overlay.
     *
     * Usage:
     *   new MaterialAlertDialogBuilder(this, SenseShieldApp.getDialogThemeResId(this))
     */
    public static int getDialogThemeResId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        int theme = prefs.getInt(AppConstants.PREF_THEME, AppConstants.THEME_STANDARD);

        switch (theme) {
            case AppConstants.THEME_WARM_DIM:      return R.style.SenseShield_Dialog_Warm;
            case AppConstants.THEME_COOL_MUTED:    return R.style.SenseShield_Dialog_Cool;
            case AppConstants.THEME_HIGH_CONTRAST: return R.style.SenseShield_Dialog_Dark;
            default:                               return R.style.SenseShield_Dialog_Standard;
        }
    }

    /**
     * Saves the chosen theme to SharedPreferences.
     * The change takes effect on the next Activity start (or after recreate()).
     */
    public static void saveTheme(Context context, int themeConstant) {
        context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(AppConstants.PREF_THEME, themeConstant)
                .apply();
    }

    // ─── Notification channels ───────────────────────────────────────────────────

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = getSystemService(NotificationManager.class);

        // Proactive alerts — lower importance so they don't startle
        NotificationChannel proactive = new NotificationChannel(
                AppConstants.NOTIF_CHANNEL_PROACTIVE,
                "Proactive Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        proactive.setDescription("Gentle heads-up before predicted difficult time slots");
        proactive.enableVibration(false);   // No vibration for proactive — non-alarming
        nm.createNotificationChannel(proactive);

        // Emergency — higher importance, vibration on
        NotificationChannel emergency = new NotificationChannel(
                AppConstants.NOTIF_CHANNEL_EMERGENCY,
                "Emergency Calm",
                NotificationManager.IMPORTANCE_HIGH
        );
        emergency.setDescription("Quick access to Emergency Calm Mode");
        emergency.enableVibration(true);
        nm.createNotificationChannel(emergency);
    }
}