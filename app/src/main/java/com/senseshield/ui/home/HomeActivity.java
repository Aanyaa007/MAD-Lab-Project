package com.senseshield.ui.home;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import android.graphics.Color;
import android.view.ViewGroup;

import com.senseshield.ai.GroqApiClient;
import com.senseshield.models.SensoryEvent;

import com.senseshield.services.NoiseMonitor;
import android.os.Handler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.senseshield.R;
import com.senseshield.SenseShieldApp;
import com.senseshield.ai.PatternAnalyzer;
import com.senseshield.ai.SensoryRiskPredictor;
import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.SensoryProfile;
import com.senseshield.models.User;
import com.senseshield.ui.calm.EmergencyCalmActivity;
import com.senseshield.ui.caregiver.CaregiverActivity;
import com.senseshield.ui.help.HelpActivity;
import com.senseshield.ui.history.HistoryActivity;
import com.senseshield.ui.profile.ProfileActivity;
import com.senseshield.utils.AccessibilityUtils;
import com.senseshield.services.AlertScheduler;
import com.senseshield.utils.AppConstants;
import com.senseshield.utils.UiClickUtils;

import java.util.Calendar;

public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "HomeActivity";

    private TextView         tvGreeting;
    private TextView         tvUserName;
    private TextView         tvAvatar;
    private TextView         btnHelp;
    private MaterialCardView cardAlertBanner;
    private TextView         tvAlertMessage;
    private TextView         tvAlertExplanation;
    private TextView         tvAlertRecommendation;
    private TextView         btnDismissAlert;
    private MaterialCardView cardEmergencyCalm;
    private MaterialCardView cardLogEvent;
    private MaterialCardView cardHistory;
    private MaterialCardView cardCaregiver;
    private MaterialCardView cardProfile;
    private TextView         tvStatEventsToday;
    private TextView         tvStatWeek;
    private TextView         tvStatBestTool;
    private MaterialCardView cardAiRisk;
    private TextView         tvRiskScore;
    private TextView         tvRiskLabel;
    private TextView         tvRiskAdvice;
    private View             viewRiskBar;
    private MaterialButton   btnExplainPatterns;
    private MaterialButton   btnCopingNow;

    private float lastRiskScore = 0f;

    private DatabaseHelper       db;
    private SensoryRiskPredictor riskPredictor;
    private int                  currentUserId = -1;
    private User                 currentUser;
    private SensoryProfile       profile;

    private boolean isInHighNoiseState = false;
    private int highNoiseCount = 0;
    private int lowNoiseCount = 0;

    private static final int REQUIRED_HIGH_COUNT = 3;
    private static final int REQUIRED_LOW_COUNT = 3;
    private static final int HYSTERESIS_MARGIN = 5;
    private int threshold = 80;

    private int heartRate = 70;
    private int baselineHR = 80;

    private boolean isSuggestionActive = false;
    private boolean isEmergencyActive = false;

    private int highHRCount = 0;
    private int normalHRCount = 0;
    private boolean isInHighHRState = false;

    private static final int REQUIRED_HIGH_HR_COUNT = 3;
    private static final int REQUIRED_NORMAL_HR_COUNT = 3;

    private long lastSuggestionTime = 0;
    private static final long SUGGESTION_COOLDOWN = 10000;
    private long lastEmergencyTime = 0;
    private static final long EMERGENCY_COOLDOWN = 30000;
    private int appliedThemeResId = -1;

    NoiseMonitor noiseMonitor;
    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedThemeResId = SenseShieldApp.getThemeResId(this);
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Log.d("TEST_LOG", "App started");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
            } else {
                startNoiseSystem();
            }
        } else {
            startNoiseSystem();
        }

        db = DatabaseHelper.getInstance(this);
        try {
            riskPredictor = new SensoryRiskPredictor(this);
        } catch (Throwable t) {
            Log.w(TAG, "Risk predictor unavailable, continuing without ML card", t);
            riskPredictor = null;
        }

        loadCurrentUser();
        if (profile != null) {
            threshold = profile.getNoiseSensitivity() * 10 + 50;
        }
        bindViews();
        populateUI();
        setupClickListeners();
        checkProactiveAlert();
        AlertScheduler.scheduleHourlyCheck(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SenseShieldApp.getThemeResId(this) != appliedThemeResId) {
            recreate();
            return;
        }
        if (tvGreeting != null) setGreeting();
        populateStats();
        checkProactiveAlert();
        refreshRiskScore();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (noiseMonitor != null) {
            noiseMonitor.stop();
        }
        if (riskPredictor != null) riskPredictor.close();
    }

    private void loadCurrentUser() {
        SharedPreferences prefs = getSharedPreferences(
                AppConstants.PREFS_NAME, MODE_PRIVATE);
        currentUserId = prefs.getInt(AppConstants.PREF_CURRENT_USER_ID, -1);
        if (currentUserId != -1) {
            currentUser = db.getUserById(currentUserId);
            profile     = db.getProfileByUserId(currentUserId);
        }
    }

    private void bindViews() {
        tvGreeting        = findViewById(R.id.tv_greeting);
        tvUserName        = findViewById(R.id.tv_user_name);
        tvAvatar          = findViewById(R.id.tv_avatar);
        btnHelp           = findViewById(R.id.btn_help);
        cardAlertBanner       = findViewById(R.id.card_alert_banner);
        tvAlertMessage        = findViewById(R.id.tv_alert_message);
        tvAlertExplanation    = findViewById(R.id.tv_alert_explanation);
        tvAlertRecommendation = findViewById(R.id.tv_alert_recommendation);
        btnDismissAlert       = findViewById(R.id.btn_dismiss_alert);
        cardEmergencyCalm = findViewById(R.id.card_emergency_calm);
        cardLogEvent      = findViewById(R.id.card_log_event);
        cardHistory       = findViewById(R.id.card_history);
        cardCaregiver     = findViewById(R.id.card_caregiver);
        cardProfile       = findViewById(R.id.card_profile);
        tvStatEventsToday = findViewById(R.id.tv_stat_events_today);
        tvStatWeek        = findViewById(R.id.tv_stat_week);
        tvStatBestTool    = findViewById(R.id.tv_stat_best_tool);
        cardAiRisk          = findViewById(R.id.card_ai_risk);
        tvRiskScore         = findViewById(R.id.tv_risk_score);
        tvRiskLabel         = findViewById(R.id.tv_risk_label);
        tvRiskAdvice        = findViewById(R.id.tv_risk_advice);
        viewRiskBar         = findViewById(R.id.view_risk_bar);
        btnExplainPatterns  = findViewById(R.id.btn_explain_patterns);
        btnCopingNow        = findViewById(R.id.btn_coping_now);
    }

    private void populateUI() {
        setGreeting();
        if (currentUser != null) {
            tvUserName.setText(currentUser.getName());
            tvAvatar.setText(currentUser.getInitials());
            try {
                tvAvatar.getBackground().setTint(
                        Color.parseColor(currentUser.getAvatarColor()));
            } catch (Exception e) {
                tvAvatar.getBackground().setTint(Color.parseColor("#7F77DD"));
            }
        }
        populateStats();
    }

    private void populateStats() {
        if (currentUserId == -1) return;

        int eventsToday = db.getEventCountForDays(currentUserId, 1);
        if (tvStatEventsToday != null)
            tvStatEventsToday.setText(String.valueOf(eventsToday));

        int eventsWeek = db.getEventCountForDays(currentUserId, 7);
        if (tvStatWeek != null)
            tvStatWeek.setText(String.valueOf(eventsWeek));

        if (tvStatBestTool != null) {
            int totalEvents = db.getEventCount(currentUserId);
            if (totalEvents > 0) {
                String bestTool = db.getMostEffectiveCalmTool(currentUserId);
                tvStatBestTool.setText(PatternAnalyzer.calmToolToLabel(bestTool));
            } else {
                tvStatBestTool.setText("—");
            }
        }
    }

    private void refreshRiskScore() {
        if (currentUserId == -1 || cardAiRisk == null || riskPredictor == null) {
            if (cardAiRisk != null) cardAiRisk.setVisibility(View.GONE);
            return;
        }

        new AsyncTask<Void, Void, SensoryRiskPredictor.RiskResult>() {
            @Override
            protected SensoryRiskPredictor.RiskResult doInBackground(Void... v) {
                return riskPredictor.predict(currentUserId);
            }

            @Override
            protected void onPostExecute(SensoryRiskPredictor.RiskResult result) {
                if (result == null || tvRiskScore == null) return;

                lastRiskScore = result.score;
                int pct   = Math.round(result.score * 100);
                int color = android.graphics.Color.parseColor(result.color);

                tvRiskScore.setText(pct + "%");
                tvRiskScore.setTextColor(color);
                tvRiskLabel.setText(result.label);
                tvRiskLabel.setTextColor(color);
                tvRiskLabel.getBackground().setTint(
                        android.graphics.Color.parseColor(result.color + "33"));
                tvRiskAdvice.setText(result.advice);

                viewRiskBar.post(() -> {
                    int parentWidth = ((android.view.View) viewRiskBar.getParent()).getWidth();
                    int barWidth    = (int)(parentWidth * result.score);
                    ViewGroup.LayoutParams lp = viewRiskBar.getLayoutParams();
                    lp.width = Math.max(barWidth, 8);
                    viewRiskBar.setLayoutParams(lp);
                    viewRiskBar.getBackground().setTint(color);
                });

                if (btnCopingNow != null) {
                    btnCopingNow.setVisibility(result.score >= 0.60f ? View.VISIBLE : View.GONE);
                }

                cardAiRisk.setVisibility(View.VISIBLE);
            }
        }.execute();
    }

    private void setGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if      (hour >= 5  && hour < 12) greeting = getString(R.string.home_greeting_morning);
        else if (hour >= 12 && hour < 17) greeting = getString(R.string.home_greeting_afternoon);
        else                              greeting = getString(R.string.home_greeting_evening);
        tvGreeting.setText(greeting);
    }

    private void setupClickListeners() {
        UiClickUtils.setSafeClickListener(cardEmergencyCalm, v -> launchEmergencyCalm(false));
        UiClickUtils.setSafeClickListener(btnDismissAlert, v -> hideAlertBanner());
        UiClickUtils.setSafeClickListener(cardAlertBanner, v -> launchEmergencyCalm(true));
        UiClickUtils.setSafeClickListener(cardLogEvent, v ->
                startActivity(new Intent(this, LogEventActivity.class)));
        UiClickUtils.setSafeClickListener(cardHistory, v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        UiClickUtils.setSafeClickListener(tvAvatar, v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        UiClickUtils.setSafeClickListener(cardCaregiver, v ->
                startActivity(new Intent(this, CaregiverActivity.class)));
        UiClickUtils.setSafeClickListener(cardProfile, v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        UiClickUtils.setSafeClickListener(btnHelp, v ->
                startActivity(new Intent(this, HelpActivity.class)));

        if (btnExplainPatterns != null)
            UiClickUtils.setSafeClickListener(btnExplainPatterns, v -> triggerExplainPatterns());

        if (btnCopingNow != null)
            UiClickUtils.setSafeClickListener(btnCopingNow, v -> triggerCopingSuggestion());
    }

    // ── LLM: Explain my patterns ────────────────────────────────────────────

    private void triggerExplainPatterns() {
        if (currentUserId == -1) return;

        if (AppConstants.GROQ_API_KEY.startsWith("YOUR_")) {
            showApiKeyMissingDialog(); return;
        }

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.layout_llm_insight, null);
        LinearLayout llLoading = dialogView.findViewById(R.id.ll_llm_loading);
        LinearLayout llResult  = dialogView.findViewById(R.id.ll_llm_result);
        TextView     tvBody    = dialogView.findViewById(R.id.tv_llm_body);
        TextView     tvLoading = dialogView.findViewById(R.id.tv_llm_loading_text);
        tvLoading.setText("Analysing your patterns…");

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("✨  Pattern Insights")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .create();
        dialog.show();

        List<SensoryEvent> events = db.getRecentEvents(currentUserId, 30);
        String systemPrompt = "You are a compassionate assistant helping someone with autism "
                + "understand their sensory patterns. Analyse the events and give 3-4 warm, "
                + "personal sentences about patterns you notice. Use second person ('You tend "
                + "to...'). Be encouraging and specific about times, triggers, or trends.";
        String userMessage = buildEventSummary(events, "past 30 days");

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
                        tvBody.setText("Could not load insights right now.\n\n" + message);
                    }
                });
    }

    // ── LLM: What to do now ──────────────────────────────────────────────────

    private void triggerCopingSuggestion() {
        if (currentUserId == -1) return;

        if (AppConstants.GROQ_API_KEY.startsWith("YOUR_")) {
            showApiKeyMissingDialog(); return;
        }

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.layout_llm_insight, null);
        LinearLayout llLoading = dialogView.findViewById(R.id.ll_llm_loading);
        LinearLayout llResult  = dialogView.findViewById(R.id.ll_llm_result);
        TextView     tvBody    = dialogView.findViewById(R.id.tv_llm_body);
        TextView     tvLoading = dialogView.findViewById(R.id.tv_llm_loading_text);
        tvLoading.setText("Getting a personalised suggestion…");

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("💡  Right Now")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .create();
        dialog.show();

        List<SensoryEvent> recent = db.getRecentEvents(currentUserId, 3);
        String tools = (profile != null && profile.getPreferredCalmTools() != null)
                ? profile.getPreferredCalmTools() : "Breathing, Sound therapy";
        int riskPct = Math.round(lastRiskScore * 100);

        String systemPrompt = "You are a calm, caring assistant for someone with autism who is "
                + "currently feeling overwhelmed. Suggest ONE specific calming technique and "
                + "explain in 2 sentences why it suits this exact moment. Be warm and direct.";
        String userMessage = "Current risk score: " + riskPct + "%\n"
                + "Preferred calm tools: " + tools + "\n\n"
                + buildEventSummary(recent, "last few hours");

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
                        tvBody.setText("Could not load suggestion right now.\n\n" + message);
                    }
                });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildEventSummary(List<SensoryEvent> events, String window) {
        if (events == null || events.isEmpty())
            return "No events logged in the " + window + ".";

        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d 'at' h:mm a", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        sb.append("Events logged in the ").append(window).append(":\n\n");
        for (SensoryEvent e : events) {
            sb.append("• ").append(sdf.format(new Date(e.getTimestamp())))
                    .append(" | Trigger: ").append(triggerLabel(e.getTriggerType()))
                    .append(" | Intensity: ").append(e.getSeverity()).append("/5");
            if (e.getNotes() != null && !e.getNotes().isEmpty())
                sb.append(" | Notes: \"").append(e.getNotes()).append("\"");
            sb.append("\n");
        }
        if (profile != null) {
            sb.append("\nSensitivity profile: ")
                    .append("Noise=").append(profile.getNoiseSensitivity()).append("/5, ")
                    .append("Light=").append(profile.getLightSensitivity()).append("/5, ")
                    .append("Crowds=").append(profile.getCrowdSensitivity()).append("/5, ")
                    .append("Touch=").append(profile.getTextureSensitivity()).append("/5, ")
                    .append("Smell=").append(profile.getSmellSensitivity()).append("/5, ")
                    .append("Change=").append(profile.getChangeSensitivity()).append("/5");
        }
        return sb.toString();
    }

    private String triggerLabel(String t) {
        if (t == null) return "Unknown";
        switch (t) {
            case AppConstants.TRIGGER_NOISE:    return "Loud noise";
            case AppConstants.TRIGGER_LIGHT:    return "Bright light";
            case AppConstants.TRIGGER_CROWD:    return "Crowded place";
            case AppConstants.TRIGGER_CHANGE:   return "Sudden change";
            case AppConstants.TRIGGER_TEXTURE:  return "Texture / touch";
            case AppConstants.TRIGGER_SMELL:    return "Strong smell";
            case AppConstants.TRIGGER_MULTIPLE: return "Multiple triggers";
            default:                            return t;
        }
    }

    private void showApiKeyMissingDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Groq API key needed")
                .setMessage("To use AI insights, add your free Groq API key:\n\n"
                        + "1. Go to console.groq.com\n"
                        + "2. Sign up for free (no card needed)\n"
                        + "3. Create an API key\n"
                        + "4. Open AppConstants.java and replace\n"
                        + "   YOUR_GROQ_API_KEY_HERE\n"
                        + "   with your key\n"
                        + "5. Rebuild the app")
                .setPositiveButton("Got it", null)
                .show();
    }

    private void launchEmergencyCalm(boolean fromAlert) {
        Intent intent = new Intent(this, EmergencyCalmActivity.class);
        intent.putExtra(EmergencyCalmActivity.EXTRA_FROM_ALERT, fromAlert);
        intent.putExtra(EmergencyCalmActivity.EXTRA_USER_ID, currentUserId);
        if (profile != null)
            intent.putExtra(EmergencyCalmActivity.EXTRA_PREFERRED_SOUND,
                    profile.getPreferredSoundTrack());
        startActivity(intent);
    }

    private void checkProactiveAlert() {
        if (cardAlertBanner == null || currentUserId == -1) { hideAlertBanner(); return; }
        if (profile != null && !profile.isProactiveAlertsEnabled()) { hideAlertBanner(); return; }
        if (db.getEventCount(currentUserId) < AppConstants.AI_MIN_EVENTS_FOR_PREDICTION) {
            hideAlertBanner(); return;
        }
        PatternAnalyzer analyzer = new PatternAnalyzer(this, currentUserId);
        if (analyzer.isCurrentTimeSlotRisky()) {
            String description  = analyzer.getRiskSlotDescription();
            String explanation  = analyzer.getRiskSlotExplanation();
            String bestTool     = analyzer.getBestCalmTool();
            String toolLabel    = PatternAnalyzer.calmToolToLabel(bestTool);
            showAlertBanner(description, explanation, "💡 Try: " + toolLabel);
        } else {
            hideAlertBanner();
        }
    }

    private void showAlertBanner(String message, String explanation, String recommendation) {
        if (cardAlertBanner == null) return;
        if (tvAlertMessage != null && message != null)
            tvAlertMessage.setText(message);
        if (tvAlertExplanation != null && explanation != null && !explanation.isEmpty()) {
            tvAlertExplanation.setText(explanation);
            tvAlertExplanation.setVisibility(View.VISIBLE);
        }
        if (tvAlertRecommendation != null && recommendation != null && !recommendation.isEmpty()) {
            tvAlertRecommendation.setText(recommendation);
            tvAlertRecommendation.setVisibility(View.VISIBLE);
        }
        cardAlertBanner.setVisibility(View.VISIBLE);
        AccessibilityUtils.announce(cardAlertBanner, "Alert: " + message + ". " + recommendation);
    }

    private void hideAlertBanner() {
        if (cardAlertBanner != null) cardAlertBanner.setVisibility(View.GONE);
    }

    private void startMonitoringSystem() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateHeartRateState();
                updateNoiseState();
                evaluateSystemState();

                Log.d("SYSTEM_STATE",
                        "HR=" + heartRate +
                                " | HRState=" + isInHighHRState +
                                " | NoiseState=" + isInHighNoiseState +
                                " | Suggestion=" + isSuggestionActive +
                                " | Emergency=" + isEmergencyActive
                );

                handler.postDelayed(this, 2000);
            }
        }, 3000);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("TEST_MONITOR", "Permission granted, starting monitor");
                startNoiseSystem();
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startNoiseSystem() {
        noiseMonitor = new NoiseMonitor();
        noiseMonitor.start();
        startMonitoringSystem();
    }

    private int getSimulatedHeartRate() {
        int base = baselineHR;
        if (isInHighNoiseState) {
            return base + 20 + (int)(Math.random() * 15);
        } else {
            return base + (int)(Math.random() * 10);
        }
    }

    private void showSuggestionUI(boolean isNoiseHigh, boolean isInHighHRState) {
        if (isNoiseHigh && isInHighHRState) {
            showAlertBanner(
                    "Things seem overwhelming",
                    "Both your environment and body signals suggest rising stress.",
                    "Consider taking a moment to reset"
            );
        } else if (isNoiseHigh) {
            showAlertBanner(
                    "Environment is getting loud",
                    "The noise around you might be overwhelming.",
                    "You could move to a quieter space"
            );
        } else if (isInHighHRState) {
            showAlertBanner(
                    "Your body seems stressed",
                    "Your heart rate is elevated, which may indicate rising stress.",
                    "Try slowing your breathing for a moment"
            );
        }
    }

    private void hideSuggestionUI() {
        hideAlertBanner();
    }

    private void triggerEmergencyCalm(boolean isNoiseHigh, boolean isInHighHRState) {
        String triggerType;
        if (isNoiseHigh && isInHighHRState) {
            triggerType = AppConstants.TRIGGER_MULTIPLE;
        } else if (isNoiseHigh) {
            triggerType = AppConstants.TRIGGER_NOISE;
        } else {
            triggerType = AppConstants.TRIGGER_HEART_RATE;
        }

        int severity = 4;
        String notes = "Auto-detected: ";
        if (isNoiseHigh) notes += "High noise ";
        if (isInHighHRState) notes += "+ elevated heart rate";

        SensoryEvent event = new SensoryEvent();
        event.setUserId(currentUserId);
        event.setTriggerType(triggerType);
        event.setSeverity(severity);
        event.setLocationTag(AppConstants.LOCATION_OTHER);
        event.setCalmToolUsed(AppConstants.CALM_NONE);
        event.setEffectiveness(0);

        long now = System.currentTimeMillis();
        event.setTimestamp(now);

        Calendar cal = Calendar.getInstance();
        event.setHourOfDay(cal.get(Calendar.HOUR_OF_DAY));
        event.setDayOfWeek(cal.get(Calendar.DAY_OF_WEEK));
        event.setNotes(notes.trim());
        event.setFromProactiveAlert(false);
        db.insertEvent(event);

        Intent intent = new Intent(this, EmergencyCalmActivity.class);
        intent.putExtra("FROM_AUTO_TRIGGER", true);
        intent.putExtra("NOISE_HIGH", isNoiseHigh);
        intent.putExtra("HR_HIGH", isInHighHRState);
        startActivity(intent);
    }

    private void updateHeartRateState() {
        heartRate = getSimulatedHeartRate();
        boolean isHigh = heartRate > Math.max(baselineHR + 20, 95);

        if (isHigh) {
            highHRCount++;
            normalHRCount = 0;
        } else {
            normalHRCount++;
            highHRCount = 0;
        }

        if (!isInHighHRState && highHRCount >= REQUIRED_HIGH_HR_COUNT) {
            isInHighHRState = true;
        }
        if (isInHighHRState && normalHRCount >= REQUIRED_NORMAL_HR_COUNT) {
            isInHighHRState = false;
        }
    }

    private void updateNoiseState() {
        double db = noiseMonitor.getDb();

        if (db > threshold + HYSTERESIS_MARGIN) {
            highNoiseCount++;
            lowNoiseCount = 0;
        } else if (db < threshold - HYSTERESIS_MARGIN) {
            lowNoiseCount++;
            highNoiseCount = 0;
        }

        if (!isInHighNoiseState && highNoiseCount >= REQUIRED_HIGH_COUNT) {
            isInHighNoiseState = true;
        }
        if (isInHighNoiseState && lowNoiseCount >= REQUIRED_LOW_COUNT) {
            isInHighNoiseState = false;
        }
    }

    private void evaluateSystemState() {
        boolean isNoiseHigh = isInHighNoiseState;
        long now = System.currentTimeMillis();

        if (!isSuggestionActive &&
                (isNoiseHigh || isInHighHRState) &&
                (now - lastSuggestionTime > SUGGESTION_COOLDOWN)) {
            isSuggestionActive = true;
            lastSuggestionTime = now;
            showSuggestionUI(isNoiseHigh, isInHighHRState);
        }

        if (!isEmergencyActive &&
                isNoiseHigh && isInHighHRState &&
                (now - lastEmergencyTime > EMERGENCY_COOLDOWN)) {
            isEmergencyActive = true;
            lastEmergencyTime = now;
            triggerEmergencyCalm(isNoiseHigh, isInHighHRState);
        }

        if (isEmergencyActive && (!isNoiseHigh || !isInHighHRState)) {
            isEmergencyActive = false;
        }

        if (isSuggestionActive && !isNoiseHigh && !isInHighHRState) {
            isSuggestionActive = false;
            hideSuggestionUI();
        }
    }
}