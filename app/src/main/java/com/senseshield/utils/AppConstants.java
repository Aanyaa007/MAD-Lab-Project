package com.senseshield.utils;

/**
 * AppConstants.java
 * Central place for all constant values used across SenseShield.
 * Change values here to affect the entire app.
 */
public class AppConstants {

    // ─── Database ───────────────────────────────────────────────────────────────
    public static final String DB_NAME          = "senseshield.db";
    public static final int    DB_VERSION       = 3;

    // ─── Table names ────────────────────────────────────────────────────────────
    public static final String TABLE_USERS           = "users";
    public static final String TABLE_SENSORY_PROFILE = "sensory_profile";
    public static final String TABLE_SENSORY_EVENTS  = "sensory_events";

    // ─── Shared Prefs ────────────────────────────────────────────────────────────
    public static final String PREFS_NAME            = "senseshield_prefs";
    public static final String PREF_CURRENT_USER_ID  = "current_user_id";
    public static final String PREF_ONBOARDING_DONE  = "onboarding_complete";
    public static final String PREF_THEME             = "app_theme";

    // ─── Themes ──────────────────────────────────────────────────────────────────
    // These map to actual themes defined in themes.xml
    public static final int THEME_WARM_DIM       = 0;  // Warm oranges, low brightness
    public static final int THEME_COOL_MUTED     = 1;  // Cool blues, desaturated
    public static final int THEME_HIGH_CONTRAST  = 2;  // Dark bg, strong contrast
    public static final int THEME_STANDARD       = 3;  // Default Material light

    // ─── Trigger types (what caused the sensory event) ───────────────────────────
    public static final String TRIGGER_NOISE      = "NOISE";
    public static final String TRIGGER_LIGHT      = "LIGHT";
    public static final String TRIGGER_CROWD      = "CROWD";
    public static final String TRIGGER_CHANGE     = "SUDDEN_CHANGE";
    public static final String TRIGGER_TEXTURE    = "TEXTURE";
    public static final String TRIGGER_SMELL      = "SMELL";
    public static final String TRIGGER_MULTIPLE   = "MULTIPLE";
    public static final String TRIGGER_UNKNOWN    = "UNKNOWN";

    public static final String TRIGGER_HEART_RATE = "HEART_RATE";

    // ─── Calming tools ───────────────────────────────────────────────────────────
    public static final String CALM_BREATHING     = "BREATHING";
    public static final String CALM_SOUND         = "SOUND_THERAPY";
    public static final String CALM_VISUAL        = "VISUAL_GROUNDING";
    public static final String CALM_COUNTDOWN     = "COUNTDOWN";
    public static final String CALM_NONE          = "NONE";

    // ─── Severity scale (1-5) ────────────────────────────────────────────────────
    public static final int SEVERITY_MINIMAL  = 1;
    public static final int SEVERITY_MILD     = 2;
    public static final int SEVERITY_MODERATE = 3;
    public static final int SEVERITY_STRONG   = 4;
    public static final int SEVERITY_EXTREME  = 5;

    // ─── Location tags ───────────────────────────────────────────────────────────
    public static final String LOCATION_HOME    = "HOME";
    public static final String LOCATION_SCHOOL  = "SCHOOL";
    public static final String LOCATION_PUBLIC  = "PUBLIC";
    public static final String LOCATION_OTHER   = "OTHER";

    // ─── AI / Pattern analysis ───────────────────────────────────────────────────
    // Minimum events needed before AI starts making predictions
    public static final int    AI_MIN_EVENTS_FOR_PREDICTION = 5;
    // How many days of history to analyze
    public static final int    AI_ANALYSIS_WINDOW_DAYS      = 30;
    // If a time slot has >= this many events, flag it as a risk slot
    public static final int    AI_RISK_THRESHOLD            = 3;

    // ─── Notification channels ───────────────────────────────────────────────────
    public static final String NOTIF_CHANNEL_PROACTIVE  = "senseshield_proactive";
    public static final String NOTIF_CHANNEL_EMERGENCY  = "senseshield_emergency";

    // ─── Sensitivity scale labels (used in profile UI) ───────────────────────────
    public static final String[] SENSITIVITY_LABELS = {
        "Not sensitive",
        "Slightly sensitive",
        "Moderately sensitive",
        "Very sensitive",
        "Extremely sensitive"
    };

    // ─── Sound therapy tracks (filenames in res/raw/) ────────────────────────────
    public static final String SOUND_RAIN       = "sound_rain";
    public static final String SOUND_OCEAN      = "sound_ocean";
    public static final String SOUND_FOREST     = "sound_forest";
    public static final String SOUND_WHITE_NOISE = "sound_white_noise";
    public static final String SOUND_GENTLE_MUSIC = "sound_gentle_music";

    // ─── User modes ─────────────────────────────────────────────────────────────
    public static final String PREF_USER_MODE       = "user_mode";
    public static final int    MODE_PERSON           = 0;  // Person with autism
    public static final int    MODE_CAREGIVER        = 1;  // Caregiver or teacher

    // ─── Groq LLM API ────────────────────────────────────────────────────────
    // Free key from console.groq.com — no credit card needed.
    // Replace the placeholder below with your actual key.
    public static final String GROQ_API_KEY = "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";

    // Prevent instantiation
    private AppConstants() {}
}
