package com.senseshield.ui.caregiver;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.senseshield.R;
import com.senseshield.SenseShieldApp;
import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.SensoryEvent;
import com.senseshield.models.SensoryProfile;
import com.senseshield.models.User;
import com.senseshield.utils.AppConstants;
import com.senseshield.utils.PinHashUtils;
import com.senseshield.utils.UiClickUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * CaregiverActivity.java
 * PIN-protected dashboard for caregivers and teachers.
 *
 * Flow:
 *   1. On open: check if a PIN is already saved for this user.
 *   2. No PIN set → "Create PIN" dialog → confirm → unlock dashboard.
 *   3. PIN set    → "Enter PIN" dialog  → verify  → unlock dashboard.
 *   4. Dashboard shows:
 *       • This-week stats (moments, avg intensity, best calm tool)
 *       • Last-7-days bar chart (proportional heights by day-of-week)
 *       • Last 5 moments with trigger, severity, timestamp, and any notes
 *       • Caregiver notes from the sensory profile
 *   5. Lock button re-shows the PIN dialog.
 */
public class CaregiverActivity extends AppCompatActivity {

    // ─── Views ────────────────────────────────────────────────────────────────
    private TextView     btnBack;
    private TextView     btnLock;
    private LinearLayout llDashboard;
    private TextView     tvWeekEvents;
    private TextView     tvAvgSeverity;
    private TextView     tvBestToolEmoji;
    private TextView     tvBestTool;
    private LinearLayout llHeatmap;
    private LinearLayout llRecentEvents;
    private TextView     tvCaregiverNotes;

    // ─── Data ─────────────────────────────────────────────────────────────────
    private DatabaseHelper db;
    private int            currentUserId = -1;
    private User           currentUser;
    private SensoryProfile profile;

    private int appliedThemeResId = -1;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedThemeResId = SenseShieldApp.getThemeResId(this);
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_caregiver);

        db = DatabaseHelper.getInstance(this);
        loadCurrentUser();
        bindViews();
        setupClickListeners();
        promptForPin();
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private void loadCurrentUser() {
        SharedPreferences prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE);
        currentUserId = prefs.getInt(AppConstants.PREF_CURRENT_USER_ID, -1);
        if (currentUserId != -1) {
            currentUser = db.getUserById(currentUserId);
            profile     = db.getProfileByUserId(currentUserId);
        }
    }

    private void bindViews() {
        btnBack          = findViewById(R.id.btn_back);
        btnLock          = findViewById(R.id.btn_lock);
        llDashboard      = findViewById(R.id.ll_dashboard);
        tvWeekEvents     = findViewById(R.id.tv_week_events);
        tvAvgSeverity    = findViewById(R.id.tv_avg_severity);
        tvBestToolEmoji  = findViewById(R.id.tv_best_tool_emoji);
        tvBestTool       = findViewById(R.id.tv_best_tool);
        llHeatmap        = findViewById(R.id.ll_heatmap);
        llRecentEvents   = findViewById(R.id.ll_recent_events);
        tvCaregiverNotes = findViewById(R.id.tv_caregiver_notes);
    }

    private void setupClickListeners() {
        UiClickUtils.setSafeClickListener(btnBack, v -> finish());
        UiClickUtils.setSafeClickListener(btnLock, v -> lockDashboard());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PIN FLOW
    // ═══════════════════════════════════════════════════════════════════════════

    private void promptForPin() {
        if (currentUser == null) { finish(); return; }
        if (!currentUser.hasCaregiverPin()) {
            showPinSetupDialog();
        } else {
            showPinEntryDialog(false);
        }
    }

    /** Step 1 of PIN setup — collect the new PIN. */
    private void showPinSetupDialog() {
        EditText pinInput = buildPinEditText();
        new MaterialAlertDialogBuilder(this, SenseShieldApp.getDialogThemeResId(this))
                .setTitle("Create a PIN")
                .setMessage("Set a 4-digit PIN to protect the caregiver view.")
                .setView(wrapWithPadding(pinInput))
                .setPositiveButton("Next", (d, w) -> {
                    String pin = pinInput.getText().toString().trim();
                    if (pin.length() != 4) {
                        showSimpleDialog("Please enter exactly 4 digits.", this::showPinSetupDialog);
                    } else {
                        showPinConfirmDialog(pin);
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    /** Step 2 of PIN setup — confirm the PIN matches. */
    private void showPinConfirmDialog(String original) {
        EditText pinInput = buildPinEditText();
        new MaterialAlertDialogBuilder(this, SenseShieldApp.getDialogThemeResId(this))
                .setTitle("Confirm PIN")
                .setMessage("Enter the same 4 digits again to confirm.")
                .setView(wrapWithPadding(pinInput))
                .setPositiveButton("Save PIN", (d, w) -> {
                    String pin = pinInput.getText().toString().trim();
                    if (!pin.equals(original)) {
                        showSimpleDialog("PINs don't match. Please try again.", this::showPinSetupDialog);
                    } else {
                        savePin(pin);
                        unlockDashboard();
                    }
                })
                .setNegativeButton("Back", (d, w) -> showPinSetupDialog())
                .setCancelable(false)
                .show();
    }

    /** PIN entry dialog — shown when a PIN is already set. */
    private void showPinEntryDialog(boolean showError) {
        EditText pinInput = buildPinEditText();
        String message = showError
                ? "Incorrect PIN. Please try again."
                : "Enter the caregiver PIN to continue.";
        new MaterialAlertDialogBuilder(this, SenseShieldApp.getDialogThemeResId(this))
                .setTitle("Caregiver View")
                .setMessage(message)
                .setView(wrapWithPadding(pinInput))
                .setPositiveButton("Unlock", (d, w) -> {
                    String pin = pinInput.getText().toString().trim();
                    if (PinHashUtils.verify(pin, currentUser.getCaregiverPinHash())) {
                        unlockDashboard();
                    } else {
                        showPinEntryDialog(true);
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void savePin(String pin) {
        if (currentUser == null) return;
        currentUser.setCaregiverPinHash(PinHashUtils.hash(pin));
        db.updateUser(currentUser);
    }

    private void unlockDashboard() {
        if (llDashboard != null) llDashboard.setVisibility(View.VISIBLE);
        if (btnLock     != null) btnLock.setVisibility(View.VISIBLE);
        loadDashboard();
    }

    private void lockDashboard() {
        if (llDashboard != null) llDashboard.setVisibility(View.GONE);
        if (btnLock     != null) btnLock.setVisibility(View.GONE);
        finish();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DASHBOARD
    // ═══════════════════════════════════════════════════════════════════════════

    private void loadDashboard() {
        if (currentUserId == -1) return;
        loadWeekStats();
        loadHeatmap();
        loadRecentEvents();
        loadNotes();
    }

    // ─── This-week stats ──────────────────────────────────────────────────────

    private void loadWeekStats() {
        int   weekCount   = db.getEventCountForDays(currentUserId, 7);
        float avgSeverity = db.getAverageSeverity(currentUserId, 7);
        String bestTool   = db.getMostEffectiveCalmTool(currentUserId);

        if (tvWeekEvents != null) {
            tvWeekEvents.setText(String.valueOf(weekCount));
        }
        if (tvAvgSeverity != null) {
            tvAvgSeverity.setText(weekCount == 0 || avgSeverity == 0f
                    ? "—"
                    : String.format(Locale.getDefault(), "%.1f", avgSeverity));
        }
        if (tvBestToolEmoji != null) tvBestToolEmoji.setText(calmToolEmoji(bestTool));
        if (tvBestTool      != null) tvBestTool.setText(calmToolLabel(bestTool));
    }

    // ─── 7-day bar chart ──────────────────────────────────────────────────────

    private void loadHeatmap() {
        if (llHeatmap == null) return;
        llHeatmap.removeAllViews();

        // Count events per Calendar.DAY_OF_WEEK (0=Sun … 6=Sat)
        int[] counts = new int[7];
        List<SensoryEvent> recent = db.getRecentEvents(currentUserId, 7);
        for (SensoryEvent e : recent) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(e.getTimestamp());
            int idx = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0-based
            if (idx >= 0 && idx < 7) counts[idx]++;
        }

        int max = 1;
        for (int c : counts) if (c > max) max = c;

        int todayIdx = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
        String[] dayLabels = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
        float density = getResources().getDisplayMetrics().density;

        for (int i = 6; i >= 0; i--) {
            int dayIdx = ((todayIdx - i) + 7) % 7;
            int count  = counts[dayIdx];

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            colLp.setMargins((int)(3 * density), 0, (int)(3 * density), 0);
            col.setLayoutParams(colLp);

            // Bar
            View bar = new View(this);
            int barDp = count == 0 ? 4 : Math.max(8, (int)(80f * count / max));
            int barPx = (int)(barDp * density);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, barPx);
            barLp.setMargins(0, 0, 0, (int)(4 * density));
            bar.setLayoutParams(barLp);
            bar.setBackground(roundedRect(heatmapColor(count), 6, density));
            col.addView(bar);

            // Day label
            TextView label = new TextView(this);
            label.setText(dayLabels[dayIdx]);
            label.setTextSize(10f);
            label.setGravity(Gravity.CENTER);
            boolean isToday = (dayIdx == todayIdx);
            label.setTextColor(isToday
                    ? Color.parseColor("#534AB7")
                    : Color.parseColor("#9090A8"));
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            col.addView(label);

            llHeatmap.addView(col);
        }
    }

    // ─── Recent events ────────────────────────────────────────────────────────

    private void loadRecentEvents() {
        if (llRecentEvents == null) return;
        llRecentEvents.removeAllViews();

        List<SensoryEvent> events = db.getEventsByUser(currentUserId);
        if (events.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No moments logged yet.");
            empty.setTextSize(14f);
            empty.setTextColor(Color.parseColor("#9090A8"));
            llRecentEvents.addView(empty);
            return;
        }

        int limit = Math.min(5, events.size());
        float density = getResources().getDisplayMetrics().density;
        SimpleDateFormat sdf = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault());

        for (int i = 0; i < limit; i++) {
            SensoryEvent e = events.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, (int)(12 * density));
            row.setLayoutParams(rowLp);

            // Severity dot
            View dot = new View(this);
            int dotPx = (int)(12 * density);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotPx, dotPx);
            dotLp.setMargins(0, 0, (int)(12 * density), 0);
            dot.setLayoutParams(dotLp);
            dot.setBackground(roundedRect(severityColor(e.getSeverity()), 6, density));
            row.addView(dot);

            // Trigger emoji
            TextView emoji = new TextView(this);
            emoji.setText(triggerEmoji(e.getTriggerType()));
            emoji.setTextSize(18f);
            emoji.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams emLp = new LinearLayout.LayoutParams(
                    (int)(32 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            emLp.setMargins(0, 0, (int)(10 * density), 0);
            emoji.setLayoutParams(emLp);
            emoji.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(emoji);

            // Text block
            LinearLayout textBlock = new LinearLayout(this);
            textBlock.setOrientation(LinearLayout.VERTICAL);
            textBlock.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView title = new TextView(this);
            title.setText(triggerLabel(e.getTriggerType())
                    + "  ·  " + severityLabel(e.getSeverity()));
            title.setTextSize(13f);
            title.setTextColor(Color.parseColor("#1A1A2E"));
            textBlock.addView(title);

            TextView sub = new TextView(this);
            String timeStr = sdf.format(new Date(e.getTimestamp()));
            String noteStr = (e.getNotes() != null && !e.getNotes().isEmpty())
                    ? "  \"" + e.getNotes() + "\""
                    : "";
            sub.setText(timeStr + noteStr);
            sub.setTextSize(11f);
            sub.setTextColor(Color.parseColor("#9090A8"));
            textBlock.addView(sub);

            row.addView(textBlock);
            llRecentEvents.addView(row);

            // Divider (except after last)
            if (i < limit - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                divLp.setMargins(0, 0, 0, (int)(12 * density));
                divider.setLayoutParams(divLp);
                divider.setBackgroundColor(Color.parseColor("#EEEEF5"));
                llRecentEvents.addView(divider);
            }
        }
    }

    // ─── Caregiver notes ──────────────────────────────────────────────────────

    private void loadNotes() {
        if (tvCaregiverNotes == null) return;

        if (profile == null) {
            tvCaregiverNotes.setText("No profile found.");
            return;
        }

        String notes = profile.getNotes();

        if (notes == null || notes.isEmpty()) {
            notes = "No notes added yet.";
        }

        tvCaregiverNotes.setText(notes);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS — label / color / drawable
    // ═══════════════════════════════════════════════════════════════════════════

    private String calmToolEmoji(String tool) {
        if (tool == null) return "🌬️";
        switch (tool) {
            case AppConstants.CALM_BREATHING:  return "🌬️";
            case AppConstants.CALM_SOUND:      return "🎵";
            case AppConstants.CALM_VISUAL:     return "👁️";
            case AppConstants.CALM_COUNTDOWN:  return "🔢";
            default:                           return "🌬️";
        }
    }

    private String calmToolLabel(String tool) {
        if (tool == null) return "Breathing";
        switch (tool) {
            case AppConstants.CALM_BREATHING:  return "Breathing";
            case AppConstants.CALM_SOUND:      return "Sound therapy";
            case AppConstants.CALM_VISUAL:     return "Visual grounding";
            case AppConstants.CALM_COUNTDOWN:  return "Countdown";
            default:                           return "Breathing";
        }
    }

    private String triggerEmoji(String trigger) {
        if (trigger == null) return "❓";
        switch (trigger) {
            case AppConstants.TRIGGER_NOISE:    return "🔊";
            case AppConstants.TRIGGER_LIGHT:    return "💡";
            case AppConstants.TRIGGER_CROWD:    return "👥";
            case AppConstants.TRIGGER_CHANGE:   return "⚡";
            case AppConstants.TRIGGER_TEXTURE:  return "🤚";
            case AppConstants.TRIGGER_SMELL:    return "👃";
            case AppConstants.TRIGGER_MULTIPLE: return "🌀";
            default:                            return "❓";
        }
    }

    private String triggerLabel(String trigger) {
        if (trigger == null) return "Unknown";
        switch (trigger) {
            case AppConstants.TRIGGER_NOISE:    return "Loud sounds";
            case AppConstants.TRIGGER_LIGHT:    return "Bright lights";
            case AppConstants.TRIGGER_CROWD:    return "Crowds";
            case AppConstants.TRIGGER_CHANGE:   return "Sudden change";
            case AppConstants.TRIGGER_TEXTURE:  return "Texture";
            case AppConstants.TRIGGER_SMELL:    return "Smell";
            case AppConstants.TRIGGER_MULTIPLE: return "Multiple";
            default:                            return "Unknown";
        }
    }

    private String severityLabel(int severity) {
        switch (severity) {
            case 1: return "Minimal";
            case 2: return "Mild";
            case 3: return "Moderate";
            case 4: return "Strong";
            case 5: return "Extreme";
            default: return "" + severity;
        }
    }

    private int severityColor(int severity) {
        switch (severity) {
            case 1: return Color.parseColor("#5DCAA5");
            case 2: return Color.parseColor("#97C459");
            case 3: return Color.parseColor("#EF9F27");
            case 4: return Color.parseColor("#D85A30");
            case 5: return Color.parseColor("#E24B4A");
            default: return Color.parseColor("#BBBBBB");
        }
    }

    private int heatmapColor(int count) {
        if (count == 0) return Color.parseColor("#F1EFE8");
        if (count == 1) return Color.parseColor("#FAC775");
        if (count <= 3) return Color.parseColor("#EF9F27");
        return Color.parseColor("#E24B4A");
    }

    private GradientDrawable roundedRect(int color, int radiusDp, float density) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radiusDp * density);
        return gd;
    }

    // ─── PIN input helpers ────────────────────────────────────────────────────

    private EditText buildPinEditText() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        et.setGravity(Gravity.CENTER);
        et.setTextSize(28f);
        et.setHint("• • • •");
        return et;
    }

    private View wrapWithPadding(View v) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        int px = (int)(24 * d);
        container.setPadding(px, (int)(8 * d), px, 0);
        container.addView(v);
        return container;
    }

    private void showSimpleDialog(String message, Runnable onDismiss) {
        new MaterialAlertDialogBuilder(this, SenseShieldApp.getDialogThemeResId(this))
                .setMessage(message)
                .setPositiveButton("OK", (d, w) -> { if (onDismiss != null) onDismiss.run(); })
                .setCancelable(false)
                .show();
    }

    private String getCurrentState() {
        int recent = db.getEventCountForDays(currentUserId, 1);

        if (recent >= 5) return "🔴 High risk";
        if (recent >= 2) return "🟡 Moderate";
        return "🟢 Calm";
    }
}