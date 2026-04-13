package com.senseshield.ai;

import android.content.Context;

import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.SensoryProfile;
import com.senseshield.utils.AppConstants;

import java.util.Calendar;

/**
 * SensoryRiskPredictor.java
 *
 * Computes a real-time sensory overload risk score (0–1) using a
 * research-grounded rule-based model. The same 12-feature vector
 * used to train the TFLite model is evaluated here with explicit
 * weights drawn from the synthetic training data generator.
 *
 * When the TFLite model asset is available (sensory_overload_model.tflite
 * placed in app/src/main/assets/), re-enable the TFLite dependency in
 * build.gradle and swap in the interpreter call in runModel().
 *
 * Feature vector (all normalised 0–1):
 *   [0]  time_of_day_norm      hour / 23
 *   [1]  is_weekend            1.0 if Sat/Sun
 *   [2]  noise_exposure        noise_sens × recent_noise_events
 *   [3]  light_exposure
 *   [4]  crowd_exposure
 *   [5]  texture_exposure
 *   [6]  smell_exposure
 *   [7]  change_exposure
 *   [8]  event_freq_1h         events in last 60 min / 10
 *   [9]  event_freq_3h         events in last 180 min / 20
 *   [10] avg_intensity         (avg severity − 1) / 4
 *   [11] overall_sensitivity   mean of 6 sensitivity scores
 */
public class SensoryRiskPredictor {

    // ── Risk band thresholds ──────────────────────────────────────────────────
    public static final float THRESHOLD_LOW      = 0.35f;
    public static final float THRESHOLD_MODERATE = 0.60f;
    public static final float THRESHOLD_HIGH     = 0.80f;

    public static final int BAND_LOW      = 0;
    public static final int BAND_MODERATE = 1;
    public static final int BAND_HIGH     = 2;
    public static final int BAND_CRITICAL = 3;

    private static final String[] LABELS = {"Low", "Moderate", "High", "Critical"};
    private static final String[] ADVICE = {
        "You're doing well. Keep up your routine.",
        "Things might feel a bit much soon. Consider a short break.",
        "Your senses may be getting overloaded. Try a calming tool.",
        "High overload risk — tap Emergency Calm now."
    };
    private static final String[] COLORS = {"#4CAF50", "#FF9800", "#F44336", "#9C27B0"};

    // ── Time-of-day risk multipliers (from WESAD stress / ASD fatigue research)
    // Peak risk: morning transition 7-9h and afternoon fatigue 14-17h
    private static final float[] HOUR_WEIGHT = {
        0.30f, 0.25f, 0.20f, 0.20f, 0.25f, 0.35f,  // 00-05 (night → early morning)
        0.55f, 0.80f, 0.85f, 0.65f, 0.55f, 0.50f,  // 06-11 (morning transition peak)
        0.50f, 0.55f, 0.75f, 0.85f, 0.80f, 0.70f,  // 12-17 (afternoon fatigue peak)
        0.60f, 0.55f, 0.50f, 0.45f, 0.40f, 0.35f   // 18-23 (evening wind-down)
    };

    // ── Result container ──────────────────────────────────────────────────────

    public static class RiskResult {
        public final float   score;
        public final int     band;
        public final String  label;
        public final String  advice;
        public final String  color;

        RiskResult(float score, int band) {
            this.score  = score;
            this.band   = band;
            this.label  = LABELS[band];
            this.advice = ADVICE[band];
            this.color  = COLORS[band];
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Context        context;
    private final DatabaseHelper db;

    public SensoryRiskPredictor(Context context) {
        this.context = context.getApplicationContext();
        this.db      = DatabaseHelper.getInstance(this.context);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public RiskResult predict(int userId) {
        SensoryProfile profile = db.getProfileByUserId(userId);
        if (profile == null) return new RiskResult(0f, BAND_LOW);
        float[] f = buildFeatureVector(userId, profile);
        return runModel(f);
    }

    // ── Feature vector ────────────────────────────────────────────────────────

    private float[] buildFeatureVector(int userId, SensoryProfile profile) {
        Calendar cal   = Calendar.getInstance();
        int  hour      = cal.get(Calendar.HOUR_OF_DAY);
        int  dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        boolean weekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);

        float sNoise   = norm15(profile.getNoiseSensitivity());
        float sLight   = norm15(profile.getLightSensitivity());
        float sCrowd   = norm15(profile.getCrowdSensitivity());
        float sTexture = norm15(profile.getTextureSensitivity());
        float sSmell   = norm15(profile.getSmellSensitivity());
        float sChange  = norm15(profile.getChangeSensitivity());

        long since3h = System.currentTimeMillis() - 3L * 60 * 60 * 1000;
        long since1h = System.currentTimeMillis() -      60 * 60 * 1000;

        float eNoise   = norm05(db.getEventCountByTriggerSince(userId, AppConstants.TRIGGER_NOISE,   since3h));
        float eLight   = norm05(db.getEventCountByTriggerSince(userId, AppConstants.TRIGGER_LIGHT,   since3h));
        float eCrowd   = norm05(db.getEventCountByTriggerSince(userId, AppConstants.TRIGGER_CROWD,   since3h));
        float eTexture = norm05(db.getEventCountByTriggerSince(userId, AppConstants.TRIGGER_TEXTURE, since3h));
        float eSmell   = norm05(db.getEventCountByTriggerSince(userId, AppConstants.TRIGGER_SMELL,   since3h));
        float eChange  = norm05(db.getEventCountByTriggerSince(userId, AppConstants.TRIGGER_CHANGE,  since3h));

        int   total1h      = db.getEventCountSince(userId, since1h);
        int   total3h      = db.getEventCountSince(userId, since3h);
        float avgIntensity = db.getAverageSeveritySince(userId, since3h);
        float overallSens  = (sNoise + sLight + sCrowd + sTexture + sSmell + sChange) / 6.0f;

        return new float[]{
            hour / 23.0f,                                          // [0]
            weekend ? 1.0f : 0.0f,                                 // [1]
            sNoise   * eNoise,                                     // [2]
            sLight   * eLight,                                     // [3]
            sCrowd   * eCrowd,                                     // [4]
            sTexture * eTexture,                                   // [5]
            sSmell   * eSmell,                                     // [6]
            sChange  * eChange,                                    // [7]
            Math.min(total1h, 10) / 10.0f,                        // [8]
            Math.min(total3h, 20) / 20.0f,                        // [9]
            avgIntensity > 0 ? (avgIntensity - 1) / 4.0f : 0.0f, // [10]
            overallSens,                                           // [11]
        };
    }

    // ── Model: research-grounded rule-based scoring ───────────────────────────
    //
    // Formula grounded in:
    //   - WESAD stress arousal timing (hour weights)
    //   - Baranek 2006 multi-trigger non-linear synergy
    //   - Exposure averaged over ACTIVE dimensions only (not all 6)
    //   - Frequency normalised to /10 so even 3 events register clearly
    //
    private RiskResult runModel(float[] f) {

        // 1. Sum exposure across the 6 trigger dimensions
        float exposure = f[2] + f[3] + f[4] + f[5] + f[6] + f[7];

        // 2. Count active trigger types (exposure > threshold)
        int activeTriggers = 0;
        for (int i = 2; i <= 7; i++) if (f[i] > 0.05f) activeTriggers++;

        // 3. Average exposure over ACTIVE dimensions only
        //    (so 1 very active trigger scores as high as it should)
        float exposureAvg = activeTriggers > 0 ? exposure / activeTriggers : 0f;

        // 4. Multi-trigger synergy — Baranek 2006
        float synergy = 1.0f;
        if      (activeTriggers == 2) synergy = 1.20f;
        else if (activeTriggers == 3) synergy = 1.40f;
        else if (activeTriggers >= 4) synergy = 1.60f;

        // 5. Event frequency (f[9] = total3h / 10, capped so 3 events = 0.30)
        //    Re-scale: the feature vector stores /20, we convert back then /10
        float freq3h = Math.min(f[9] * 2.0f, 1.0f); // /20 * 2 = /10

        // 6. Base score — additive weights
        float baseScore = (0.40f * exposureAvg * synergy)
                        + (0.35f * freq3h)
                        + (0.25f * f[10]);   // f[10] = avg intensity norm

        // 7. Time-of-day — additive adjustment, not multiplicative
        //    Neutral at 0.5, adds up to +0.15 at peak hours, -0.08 at night
        int hour = (int) Math.round(f[0] * 23);
        float timeAdj = (HOUR_WEIGHT[Math.min(hour, 23)] - 0.50f) * 0.20f;
        float score = baseScore + timeAdj;

        // 8. Sensitivity floor — high sensitivity users never show 0%
        float sensitivityFloor = f[11] * 0.12f;
        score = Math.max(score, sensitivityFloor);

        score = Math.max(0f, Math.min(1f, score));
        return new RiskResult(score, scoreToBand(score));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private float norm15(int v) { return Math.max(0f, Math.min(1f, (v - 1) / 4.0f)); }
    private float norm05(int v) { return Math.min(v, 5) / 5.0f; }

    private int scoreToBand(float score) {
        if (score < THRESHOLD_LOW)      return BAND_LOW;
        if (score < THRESHOLD_MODERATE) return BAND_MODERATE;
        if (score < THRESHOLD_HIGH)     return BAND_HIGH;
        return BAND_CRITICAL;
    }

    public static String colorForBand(int band) { return COLORS[band]; }
    public static String labelForBand(int band)  { return LABELS[band]; }

    /** No-op — kept for API compatibility. */
    public void close() {}
}
