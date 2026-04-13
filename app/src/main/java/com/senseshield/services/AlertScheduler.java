package com.senseshield.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * AlertScheduler.java
 * Manages hourly AlarmManager checks that drive proactive alerts.
 *
 * Uses setInexactRepeating on all API levels — no special permissions needed,
 * battery-friendly, and hourly precision is more than enough for this use case.
 *
 * Call scheduleHourlyCheck() from HomeActivity.onCreate() to ensure the
 * schedule is active whenever the user opens the app.
 * Call cancelSchedule() when proactive alerts are toggled off in settings.
 */
public final class AlertScheduler {

    private static final int REQUEST_CODE = 42;

    private AlertScheduler() {}

    /**
     * Schedules an inexact repeating hourly check.
     * Safe to call repeatedly — replaces any existing alarm with the same PendingIntent.
     */
    public static void scheduleHourlyCheck(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                getNextHourMillis(),
                AlarmManager.INTERVAL_HOUR,
                buildPendingIntent(context)
        );
    }

    /**
     * No-op alias kept so AlertReceiver can call it without needing to know the API level.
     * setInexactRepeating auto-repeats, so no re-scheduling is needed.
     */
    public static void scheduleNextHourlyCheck(Context context) {
        // setInexactRepeating repeats automatically — nothing to do here.
    }

    /** Cancels all pending proactive alert alarms. */
    public static void cancelSchedule(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(buildPendingIntent(context));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, AlertReceiver.class);
        return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    /** Returns the Unix epoch time (ms) of the start of the next clock hour. */
    private static long getNextHourMillis() {
        long hourMs = 60L * 60 * 1000;
        long now    = System.currentTimeMillis();
        return (now / hourMs + 1) * hourMs;
    }
}
