package com.senseshield.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.senseshield.R;
import com.senseshield.ai.PatternAnalyzer;
import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.SensoryProfile;
import com.senseshield.models.User;
import com.senseshield.ui.calm.EmergencyCalmActivity;
import com.senseshield.ui.caregiver.CaregiverHomeActivity;
import com.senseshield.utils.AppConstants;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AlertReceiver.java
 * BroadcastReceiver that fires on the hourly alarm set by AlertScheduler.
 *
 * On receive:
 *   1. Look up the current user and their proactive-alert preference.
 *   2. Ask PatternAnalyzer if the current time slot is risky.
 *   3. If yes, post a gentle heads-up notification.
 *   4. Re-schedule the next hourly check (exact alarms on API 23+ are one-shot).
 *
 * Tapping the notification launches EmergencyCalmActivity directly so the
 * user can start a calming session with one tap.
 */
public class AlertReceiver extends BroadcastReceiver {

    private static final int NOTIFICATION_ID = 1001;
    private static final ExecutorService RECEIVER_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        RECEIVER_EXECUTOR.execute(() -> {
            try {
                handleReceive(appContext);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void handleReceive(Context context) {

        // ── 1. Find the current user ──────────────────────────────────────────
        SharedPreferences prefs = context.getSharedPreferences(
                AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        int userId = prefs.getInt(AppConstants.PREF_CURRENT_USER_ID, -1);
        if (userId == -1) return;

        // ── 2. Check alert preference ─────────────────────────────────────────
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        SensoryProfile profile = db.getProfileByUserId(userId);
        if (profile == null || !profile.isProactiveAlertsEnabled()) return;

        // ── 3. Ask AI if this slot is risky ───────────────────────────────────
        PatternAnalyzer analyzer = new PatternAnalyzer(context, userId);
        if (!analyzer.isCurrentTimeSlotRisky()) return;

        // ── 4. Post notification ──────────────────────────────────────────────
        String message = analyzer.getRiskSlotDescription();
        postNotification(context, message, userId, profile);

        // ── 5. Notify linked caregiver (if any) ───────────────────────────────
        User user = db.getUserById(userId);
        if (user != null && user.getCaregiverId() != -1) {
            User caregiver = db.getUserById(user.getCaregiverId());
            if (caregiver != null) {
                postCaregiverNotification(context, user.getName(), message);
            }
        }

        // ── 6. Re-schedule next check (for API 23+ one-shot exact alarms) ─────
        AlertScheduler.scheduleNextHourlyCheck(context);
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private void postNotification(Context context, String message,
                                  int userId, SensoryProfile profile) {
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Create channel (safe to call multiple times — no-op if already exists)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    AppConstants.NOTIF_CHANNEL_PROACTIVE,
                    "Proactive Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Gentle reminders before predicted difficult times");
            channel.enableVibration(false);
            nm.createNotificationChannel(channel);
        }

        // Tap → open EmergencyCalmActivity
        Intent tapIntent = new Intent(context, EmergencyCalmActivity.class);
        tapIntent.putExtra(EmergencyCalmActivity.EXTRA_FROM_ALERT, true);
        tapIntent.putExtra(EmergencyCalmActivity.EXTRA_USER_ID, userId);
        tapIntent.putExtra(EmergencyCalmActivity.EXTRA_PREFERRED_SOUND,
                profile.getPreferredSoundTrack());
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                context, NOTIFICATION_ID, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, AppConstants.NOTIF_CHANNEL_PROACTIVE)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Heads up \uD83D\uDD14")
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    // ─── Caregiver Notification ───────────────────────────────────────────────

    private void postCaregiverNotification(Context context, String patientName, String riskMsg) {
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    AppConstants.NOTIF_CHANNEL_PROACTIVE,
                    "Proactive Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.enableVibration(false);
            nm.createNotificationChannel(channel);
        }

        // Tap → open CaregiverHomeActivity
        Intent tapIntent = new Intent(context, CaregiverHomeActivity.class);
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                context, NOTIFICATION_ID + 1, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = "🔔 " + patientName + " may need support";
        String body  = riskMsg + " Tap to open their dashboard.";

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, AppConstants.NOTIF_CHANNEL_PROACTIVE)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        nm.notify(NOTIFICATION_ID + 1, builder.build());
    }
}
