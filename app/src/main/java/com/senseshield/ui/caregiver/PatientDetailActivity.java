package com.senseshield.ui.caregiver;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.senseshield.ai.GroqApiClient;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.senseshield.R;
import com.senseshield.SenseShieldApp;
import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.SensoryEvent;
import com.senseshield.models.SensoryProfile;
import com.senseshield.models.User;
import com.senseshield.ui.home.LogEventActivity;
import com.senseshield.utils.AppConstants;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * PatientDetailActivity — caregiver's view of a single patient.
 *
 * Sections rendered:
 *   1. Header  — patient avatar, name, "N total events", Log + Report buttons
 *   2. Stats   — events this week, 30-day avg severity
 *   3. Heatmap — 7-day bar chart
 *   4. Recent events (last 5)
 *   5. Patient sensitivities — 6 sliders; caregiver records how strongly each
 *      trigger affects THIS patient and saves to SensoryProfile.
 *   6. Notes   — free-text observations saved to SensoryProfile.notes
 */
public class PatientDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PATIENT_ID = "patient_id";

    // ── Views ──────────────────────────────────────────────────────────────────
    private TextView       tvAvatar, tvName, tvSubtitle;
    private TextView       tvStatWeek, tvStatSeverity;
    private LinearLayout   llHeatmap, llRecentEvents;
    private EditText       etNotes;
    private MaterialButton btnSaveNotes, btnSaveSensitivities;
    private MaterialButton btnLogForPatient, btnWeeklyReport;
    private TextView       btnBack;

    // Sensitivity sliders — correspond to the 6 includes in the XML
    private final Slider[] sensitivitySliders = new Slider[6];

    // ── Data ───────────────────────────────────────────────────────────────────
    private DatabaseHelper db;
    private User           patient;
    private SensoryProfile profile;

    // Label arrays shared between populateSensitivities() and saveSensitivities()
    private static final String[] LEVEL_LABELS =
            { "", "Not sensitive", "Slightly", "Moderate", "Very sensitive", "Extreme" };
    private static final String[] SENSITIVITY_ICONS  =
            { "🔊", "💡", "👥", "✋", "👃", "🔄" };
    private static final String[] SENSITIVITY_LABELS =
            { "Loud sounds", "Bright lights", "Crowded places", "Touch", "Strong smells", "Sudden changes" };

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(SenseShieldApp.getThemeResId(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_detail);

        db = DatabaseHelper.getInstance(this);

        int patientId = getIntent().getIntExtra(EXTRA_PATIENT_ID, -1);
        if (patientId == -1) { finish(); return; }

        patient = db.getUserById(patientId);
        if (patient == null) { finish(); return; }

        profile = db.getProfileByUserId(patientId);
        // If no profile exists yet, create an in-memory one; it will be
        // persisted the first time the caregiver hits "Save sensitivities".
        if (profile == null) {
            profile = new SensoryProfile(patientId);
        }

        bindViews();
        populateHeader();
        populateStats();
        populateHeatmap();
        populateRecentEvents();
        populateSensitivities();
        populateNotes();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        populateStats();
        populateHeatmap();
        populateRecentEvents();
    }

    // ── View binding ───────────────────────────────────────────────────────────

    private void bindViews() {
        tvAvatar            = findViewById(R.id.tv_detail_avatar);
        tvName              = findViewById(R.id.tv_detail_name);
        tvSubtitle          = findViewById(R.id.tv_detail_subtitle);
        tvStatWeek          = findViewById(R.id.tv_stat_week);
        tvStatSeverity      = findViewById(R.id.tv_stat_severity);
        llHeatmap           = findViewById(R.id.ll_heatmap);
        llRecentEvents      = findViewById(R.id.ll_recent_events);
        etNotes             = findViewById(R.id.et_notes);
        btnSaveNotes        = findViewById(R.id.btn_save_notes);
        btnSaveSensitivities = findViewById(R.id.btn_save_sensitivities);
        btnLogForPatient    = findViewById(R.id.btn_log_for_patient);
        btnBack             = findViewById(R.id.btn_back);
        btnWeeklyReport     = findViewById(R.id.btn_weekly_report);

        // Bind each slider from its include row
        int[] sliderRowIds = {
                R.id.slider_noise, R.id.slider_light, R.id.slider_crowd,
                R.id.slider_texture, R.id.slider_smell, R.id.slider_change
        };
        for (int i = 0; i < sliderRowIds.length; i++) {
            View row = findViewById(sliderRowIds[i]);
            if (row != null) {
                sensitivitySliders[i] = row.findViewById(R.id.slider_sensitivity_value);
            }
        }
    }

    // ── Header ─────────────────────────────────────────────────────────────────

    private void populateHeader() {
        tvName.setText(patient.getName());
        tvAvatar.setText(patient.getInitials());
        try {
            tvAvatar.getBackground().setTint(Color.parseColor(patient.getAvatarColor()));
        } catch (Exception e) {
            tvAvatar.getBackground().setTint(Color.parseColor("#7F77DD"));
        }
        int totalEvents = db.getEventCount(patient.getId());
        tvSubtitle.setText(totalEvents + " total events logged");
    }

    // ── Stats ──────────────────────────────────────────────────────────────────

    private void populateStats() {
        int weekEvents = db.getEventCountForDays(patient.getId(), 7);
        tvStatWeek.setText(String.valueOf(weekEvents));

        float avgSev = db.getAverageSeverity(patient.getId(), 30);
        tvStatSeverity.setText(
                avgSev == 0f ? "—"
                        : String.format(Locale.getDefault(), "%.1f", avgSev));
    }

    // ── Heatmap ────────────────────────────────────────────────────────────────

    private void populateHeatmap() {
        llHeatmap.removeAllViews();
        List<SensoryEvent> events = db.getRecentEvents(patient.getId(), 7);

        int[] counts = new int[7];
        long now   = System.currentTimeMillis();
        long dayMs = 24 * 60 * 60 * 1000L;
        for (SensoryEvent e : events) {
            int daysAgo = (int) ((now - e.getTimestamp()) / dayMs);
            if (daysAgo >= 0 && daysAgo < 7) counts[daysAgo]++;
        }

        int maxCount = 1;
        for (int c : counts) if (c > maxCount) maxCount = c;

        String[] labels = getDayLabels();
        for (int i = 6; i >= 0; i--) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

            View bar = new View(this);
            int barHeight = counts[i] == 0 ? dpToPx(4)
                    : (int) (dpToPx(60) * ((float) counts[i] / maxCount));
            LinearLayout.LayoutParams barLp =
                    new LinearLayout.LayoutParams(dpToPx(22), barHeight);
            barLp.bottomMargin = dpToPx(4);
            bar.setLayoutParams(barLp);
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadii(new float[]{ 6, 6, 6, 6, 2, 2, 2, 2 });
            gd.setColor(Color.parseColor(heatmapColor(counts[i])));
            bar.setBackground(gd);
            col.addView(bar);

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(i == 0
                    ? Color.parseColor("#534AB7")
                    : Color.parseColor("#888888"));
            label.setTypeface(null, i == 0
                    ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
            col.addView(label);

            llHeatmap.addView(col);
        }
    }

    private String[] getDayLabels() {
        String[] result = new String[7];
        String[] names = { "Su", "Mo", "Tu", "We", "Th", "Fr", "Sa" };
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            result[i] = i == 0 ? "Today" : names[cal.get(Calendar.DAY_OF_WEEK) - 1];
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return result;
    }

    // ── Recent events ──────────────────────────────────────────────────────────

    private void populateRecentEvents() {
        llRecentEvents.removeAllViews();
        List<SensoryEvent> events = db.getRecentEvents(patient.getId(), 30);
        List<SensoryEvent> recent = events.size() > 5 ? events.subList(0, 5) : events;

        if (recent.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No events logged yet.");
            empty.setTextSize(13);
            empty.setTextColor(Color.parseColor("#888888"));
            empty.setPadding(0, dpToPx(8), 0, dpToPx(8));
            llRecentEvents.addView(empty);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        for (int i = 0; i < recent.size(); i++) {
            SensoryEvent e = recent.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dpToPx(10), 0, dpToPx(10));

            // Severity dot
            View dot = new View(this);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dpToPx(10), dpToPx(10));
            dotLp.setMarginEnd(dpToPx(12));
            dot.setLayoutParams(dotLp);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.parseColor(severityColor(e.getSeverity())));
            dot.setBackground(circle);
            row.addView(dot);

            // Info column
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

            TextView tvTrigger = new TextView(this);
            tvTrigger.setText(triggerEmoji(e.getTriggerType()) + "  "
                    + triggerLabel(e.getTriggerType())
                    + "  ·  " + severityLabel(e.getSeverity()));
            tvTrigger.setTextSize(13);
            tvTrigger.setTextColor(
                    getResources().getColor(android.R.color.primary_text_light, getTheme()));
            info.addView(tvTrigger);

            TextView tvTime = new TextView(this);
            tvTime.setText(sdf.format(new java.util.Date(e.getTimestamp())));
            tvTime.setTextSize(11);
            tvTime.setTextColor(Color.parseColor("#888888"));
            tvTime.setPadding(0, dpToPx(2), 0, 0);
            info.addView(tvTime);

            row.addView(info);
            llRecentEvents.addView(row);

            if (i < recent.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
                llRecentEvents.addView(divider);
            }
        }
    }

    // ── Patient sensitivities ──────────────────────────────────────────────────

    /**
     * Loads the patient's existing SensoryProfile values into the sliders and
     * sets the icon / label / level text on each row.
     */
    private void populateSensitivities() {
        int[] values = {
                profile.getNoiseSensitivity(),   profile.getLightSensitivity(),
                profile.getCrowdSensitivity(),   profile.getTextureSensitivity(),
                profile.getSmellSensitivity(),   profile.getChangeSensitivity()
        };
        int[] rowIds = {
                R.id.slider_noise, R.id.slider_light, R.id.slider_crowd,
                R.id.slider_texture, R.id.slider_smell, R.id.slider_change
        };

        for (int i = 0; i < sensitivitySliders.length; i++) {
            if (sensitivitySliders[i] == null) continue;

            // Clamp stored value into slider range [1, 5]
            int clamped = Math.max(1, Math.min(5, values[i]));
            sensitivitySliders[i].setValue(clamped);

            View row = findViewById(rowIds[i]);
            if (row == null) continue;

            TextView tvIcon  = row.findViewById(R.id.tv_sensitivity_icon);
            TextView tvLabel = row.findViewById(R.id.tv_sensitivity_label);
            TextView tvLevel = row.findViewById(R.id.tv_sensitivity_level);

            if (tvIcon  != null) tvIcon.setText(SENSITIVITY_ICONS[i]);
            if (tvLabel != null) tvLabel.setText(SENSITIVITY_LABELS[i]);
            if (tvLevel != null) tvLevel.setText(LEVEL_LABELS[clamped]);

            final TextView levelView = tvLevel;
            sensitivitySliders[i].addOnChangeListener((slider, value, fromUser) -> {
                if (levelView != null) levelView.setText(LEVEL_LABELS[(int) value]);
            });
        }
    }

    /**
     * Reads the slider values and writes them back to the patient's SensoryProfile
     * in the database.  If no profile row existed yet, it is inserted now.
     */
    private void saveSensitivities() {
        if (sensitivitySliders[0] != null) profile.setNoiseSensitivity(   (int) sensitivitySliders[0].getValue());
        if (sensitivitySliders[1] != null) profile.setLightSensitivity(   (int) sensitivitySliders[1].getValue());
        if (sensitivitySliders[2] != null) profile.setCrowdSensitivity(   (int) sensitivitySliders[2].getValue());
        if (sensitivitySliders[3] != null) profile.setTextureSensitivity( (int) sensitivitySliders[3].getValue());
        if (sensitivitySliders[4] != null) profile.setSmellSensitivity(   (int) sensitivitySliders[4].getValue());
        if (sensitivitySliders[5] != null) profile.setChangeSensitivity(  (int) sensitivitySliders[5].getValue());

        if (profile.getId() == 0) {
            db.insertProfile(profile);
        } else {
            db.updateProfile(profile);
        }

        if (btnSaveSensitivities != null) {
            btnSaveSensitivities.setText("Saved ✓");
            btnSaveSensitivities.postDelayed(
                    () -> btnSaveSensitivities.setText("Save sensitivities"), 2000);
        }
    }

    // ── Notes ──────────────────────────────────────────────────────────────────

    private void populateNotes() {
        if (profile != null && profile.getNotes() != null) {
            etNotes.setText(profile.getNotes());
        }
    }

    // ── Click listeners ────────────────────────────────────────────────────────

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        if (btnSaveSensitivities != null) {
            btnSaveSensitivities.setOnClickListener(v -> saveSensitivities());
        }

        btnSaveNotes.setOnClickListener(v -> {
            String notes = etNotes.getText() != null ? etNotes.getText().toString() : "";
            profile.setNotes(notes);
            if (profile.getId() == 0) {
                db.insertProfile(profile);
            } else {
                db.updateProfile(profile);
            }
            btnSaveNotes.setText("Saved ✓");
            btnSaveNotes.postDelayed(() -> btnSaveNotes.setText("Save notes"), 2000);
        });

        btnLogForPatient.setOnClickListener(v -> {
            Intent intent = new Intent(this, LogEventActivity.class);
            intent.putExtra(LogEventActivity.EXTRA_USER_ID, patient.getId());
            startActivity(intent);
        });

        if (btnWeeklyReport != null) {
            btnWeeklyReport.setOnClickListener(v -> triggerWeeklyReport());
        }
    }

    // ── Weekly AI report ───────────────────────────────────────────────────────

    private void triggerWeeklyReport() {
        if (AppConstants.GROQ_API_KEY.startsWith("YOUR_")) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Groq API key needed")
                    .setMessage("Add your free Groq API key in AppConstants.java to use AI reports.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.layout_llm_insight, null);
        LinearLayout llLoading = dialogView.findViewById(R.id.ll_llm_loading);
        LinearLayout llResult  = dialogView.findViewById(R.id.ll_llm_result);
        TextView     tvBody    = dialogView.findViewById(R.id.tv_llm_body);
        TextView     tvLoading = dialogView.findViewById(R.id.tv_llm_loading_text);
        tvLoading.setText("Generating weekly report…");

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("📋  Weekly Report — " + patient.getName())
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .create();
        dialog.show();

        List<SensoryEvent> events = db.getRecentEvents(patient.getId(), 7);
        String systemPrompt = "You are helping a caregiver understand their patient's sensory "
                + "patterns for the past week. Give a concise professional summary (4-5 sentences) "
                + "covering: most common triggers, peak times of day, intensity trends, and one "
                + "actionable recommendation for the caregiver. Be factual and specific.";
        String userMessage = buildPatientEventSummary(events);

        GroqApiClient.ask(AppConstants.GROQ_API_KEY, systemPrompt, userMessage,
                new GroqApiClient.Callback() {
                    @Override public void onSuccess(String response) {
                        if (!dialog.isShowing()) return;
                        llLoading.setVisibility(View.GONE);
                        llResult.setVisibility(View.VISIBLE);
                        tvBody.setText(response);
                    }
                    @Override public void onError(String message) {
                        if (!dialog.isShowing()) return;
                        llLoading.setVisibility(View.GONE);
                        llResult.setVisibility(View.VISIBLE);
                        tvBody.setText("Could not generate report.\n\n" + message);
                    }
                });
    }

    private String buildPatientEventSummary(List<SensoryEvent> events) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d 'at' h:mm a", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        sb.append("Patient: ").append(patient.getName()).append("\n");
        sb.append("Events this week (").append(events.size()).append(" total):\n\n");
        if (events.isEmpty()) {
            sb.append("No events logged this week.\n");
        } else {
            for (SensoryEvent e : events) {
                sb.append("• ").append(sdf.format(new java.util.Date(e.getTimestamp())))
                        .append(" | Trigger: ").append(e.getTriggerType())
                        .append(" | Intensity: ").append(e.getSeverity()).append("/5");
                if (e.getNotes() != null && !e.getNotes().isEmpty())
                    sb.append(" | \"").append(e.getNotes()).append("\"");
                sb.append("\n");
            }
        }
        if (profile != null) {
            sb.append("\nSensitivity profile: ")
                    .append("Noise=").append(profile.getNoiseSensitivity()).append("/5, ")
                    .append("Light=").append(profile.getLightSensitivity()).append("/5, ")
                    .append("Crowds=").append(profile.getCrowdSensitivity()).append("/5");
        }
        return sb.toString();
    }

    // ── Label / colour helpers ─────────────────────────────────────────────────

    private String triggerEmoji(String t) {
        if (t == null) return "❓";
        switch (t) {
            case AppConstants.TRIGGER_NOISE:    return "🔊";
            case AppConstants.TRIGGER_LIGHT:    return "💡";
            case AppConstants.TRIGGER_CROWD:    return "👥";
            case AppConstants.TRIGGER_CHANGE:   return "🔄";
            case AppConstants.TRIGGER_TEXTURE:  return "✋";
            case AppConstants.TRIGGER_SMELL:    return "👃";
            case AppConstants.TRIGGER_MULTIPLE: return "⚡";
            default:                            return "❓";
        }
    }

    private String triggerLabel(String t) {
        if (t == null) return "Unknown";
        switch (t) {
            case AppConstants.TRIGGER_NOISE:    return "Noise";
            case AppConstants.TRIGGER_LIGHT:    return "Light";
            case AppConstants.TRIGGER_CROWD:    return "Crowd";
            case AppConstants.TRIGGER_CHANGE:   return "Sudden change";
            case AppConstants.TRIGGER_TEXTURE:  return "Texture";
            case AppConstants.TRIGGER_SMELL:    return "Smell";
            case AppConstants.TRIGGER_MULTIPLE: return "Multiple";
            default:                            return "Unknown";
        }
    }

    private String severityLabel(int s) {
        switch (s) {
            case 1:  return "Minimal";
            case 2:  return "Mild";
            case 3:  return "Moderate";
            case 4:  return "Strong";
            case 5:  return "Extreme";
            default: return "—";
        }
    }

    private String severityColor(int s) {
        switch (s) {
            case 1:  return "#4CAF50";
            case 2:  return "#8BC34A";
            case 3:  return "#FFC107";
            case 4:  return "#FF5722";
            case 5:  return "#B71C1C";
            default: return "#AAAAAA";
        }
    }

    private String heatmapColor(int count) {
        if (count == 0) return "#E0E0E0";
        if (count <= 1) return "#4CAF50";
        if (count <= 3) return "#FFC107";
        return "#F44336";
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}