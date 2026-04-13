package com.senseshield.ai;

import android.content.Context;

import com.senseshield.database.DatabaseHelper;
import com.senseshield.utils.AppConstants;

import java.util.Calendar;

/**
 * PatternAnalyzer.java
 * Lightweight rule-based AI engine for SenseShield.
 *
 * Core idea:
 *   Store every overload event with (hour_of_day, day_of_week).
 *   Build a 24x7 frequency grid from the last 30 days.
 *   If the current slot (or adjacent hour) scores >= AI_RISK_THRESHOLD, flag as risky.
 *   Adjacent hours contribute at 30% weight for a broader time-window effect.
 *
 * A contextual message is generated using:
 *   - Time of day (morning / afternoon / evening / night)
 *   - Day of week label
 *   - Most frequent trigger type from recent history
 */
public class PatternAnalyzer {

    private final Context        context;
    private final int            userId;
    private final DatabaseHelper db;

    public PatternAnalyzer(Context context, int userId) {
        this.context = context;
        this.userId  = userId;
        this.db      = DatabaseHelper.getInstance(context);
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns true if the CURRENT hour + day-of-week slot is a predicted
     * risk slot for this user, based on historical event frequency.
     * Requires at least AI_MIN_EVENTS_FOR_PREDICTION events in history.
     */
    public boolean isCurrentTimeSlotRisky() {
        if (db.getEventCount(userId) < AppConstants.AI_MIN_EVENTS_FOR_PREDICTION) {
            return false;
        }

        int[][] grid = db.getEventCountByTimeSlot(userId, AppConstants.AI_ANALYSIS_WINDOW_DAYS);
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int day  = cal.get(Calendar.DAY_OF_WEEK) - 1; // Calendar 1–7 → 0–6

        return computeWeightedScore(grid, hour, day) >= AppConstants.AI_RISK_THRESHOLD;
    }

    /**
     * Returns a human-readable description of why the current slot is risky.
     * e.g. "Morning on Mondays can feel hard, especially with loud sounds."
     */
    public String getRiskSlotDescription() {
        Calendar cal    = Calendar.getInstance();
        int hour        = cal.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek   = cal.get(Calendar.DAY_OF_WEEK);

        String timeLabel = getTimeOfDayLabel(hour);
        String dayLabel  = getDayLabel(dayOfWeek);

        // Try to personalise with the most common trigger
        String trigger      = db.getMostFrequentTrigger(userId, AppConstants.AI_ANALYSIS_WINDOW_DAYS);
        String triggerPhrase = triggerToPhrase(trigger);

        if (triggerPhrase != null) {
            return timeLabel + " on " + dayLabel + "s can feel hard, especially with "
                    + triggerPhrase + ". Take a moment?";
        }
        return timeLabel + " on " + dayLabel + "s has felt hard before. "
                + "Want to try something calming?";
    }

    /**
     * Returns a detailed explanation of WHY this time slot is risky.
     * Shows event count and most common trigger — the "show your work" for the AI.
     * e.g. "Based on 6 overload events on Sunday mornings in the past month,
     *       mostly triggered by loud sounds."
     */
    public String getRiskSlotExplanation() {
        Calendar cal  = Calendar.getInstance();
        int hour      = cal.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int day       = dayOfWeek - 1;

        int[][] grid  = db.getEventCountByTimeSlot(userId, AppConstants.AI_ANALYSIS_WINDOW_DAYS);
        int slotCount = (hour >= 0 && hour < 24 && day >= 0 && day < 7) ? grid[hour][day] : 0;

        String timeLabel = getTimeOfDayLabel(hour);
        String dayLabel  = getDayLabel(dayOfWeek);
        String trigger   = db.getMostFrequentTrigger(userId, AppConstants.AI_ANALYSIS_WINDOW_DAYS);
        String triggerPhrase = triggerToPhrase(trigger);

        StringBuilder sb = new StringBuilder();
        sb.append("Based on ").append(slotCount)
          .append(slotCount == 1 ? " event" : " events")
          .append(" on ").append(dayLabel).append(" ").append(timeLabel.toLowerCase())
          .append("s in the past month");
        if (triggerPhrase != null) {
            sb.append(", often triggered by ").append(triggerPhrase);
        }
        sb.append(".");
        return sb.toString();
    }

    /**
     * Returns the calm tool that has worked best for this user based on
     * logged effectiveness ratings. Falls back to CALM_BREATHING if no data.
     * Used to show an adaptive recommendation in the proactive alert banner.
     */
    public String getBestCalmTool() {
        String best = db.getMostEffectiveCalmTool(userId);
        return (best != null && !best.isEmpty()) ? best : AppConstants.CALM_BREATHING;
    }

    /**
     * Detects if multiple sensory triggers are frequently co-occurring.
     * Returns an insight string if TRIGGER_MULTIPLE makes up >= 30% of events,
     * or if two individual triggers together dominate (>= 50%).
     * Returns null if no notable multi-trigger pattern exists.
     */
    public String getMultiTriggerInsight() {
        int total = db.getEventCount(userId);
        if (total < AppConstants.AI_MIN_EVENTS_FOR_PREDICTION) return null;

        int multiCount = db.getEventCountByTrigger(userId,
                AppConstants.TRIGGER_MULTIPLE, AppConstants.AI_ANALYSIS_WINDOW_DAYS);
        if (multiCount == 0) return null;

        int windowTotal = db.getEventCountForDays(userId, AppConstants.AI_ANALYSIS_WINDOW_DAYS);
        if (windowTotal == 0) return null;

        float ratio = (float) multiCount / windowTotal;
        if (ratio >= 0.30f) {
            return "💡 You often experience multiple sensory inputs together ("
                    + Math.round(ratio * 100) + "% of events). "
                    + "Moving to a quieter, less stimulating space early may help.";
        }
        return null;
    }

    /** Human-readable label for a calm tool constant. */
    public static String calmToolToLabel(String tool) {
        if (tool == null) return "Breathing";
        switch (tool) {
            case AppConstants.CALM_BREATHING: return "Breathing";
            case AppConstants.CALM_SOUND:     return "Sound therapy";
            case AppConstants.CALM_VISUAL:    return "Visual grounding";
            case AppConstants.CALM_COUNTDOWN: return "Countdown timer";
            default:                          return "Breathing";
        }
    }

    // ─── Score computation ────────────────────────────────────────────────────

    /**
     * Weighted score: current slot counts fully, adjacent ±1 hours at 30%.
     * This smooths the grid so a cluster of events near an hour still triggers.
     */
    private float computeWeightedScore(int[][] grid, int hour, int day) {
        if (hour < 0 || hour >= 24 || day < 0 || day >= 7) return 0f;
        float score = grid[hour][day];
        if (hour > 0)  score += grid[hour - 1][day] * 0.30f;
        if (hour < 23) score += grid[hour + 1][day] * 0.30f;
        return score;
    }

    // ─── Label helpers ────────────────────────────────────────────────────────

    private String getTimeOfDayLabel(int hour) {
        if (hour >= 5  && hour < 12) return "Morning";
        if (hour >= 12 && hour < 17) return "Afternoon";
        if (hour >= 17 && hour < 21) return "Evening";
        return "Night";
    }

    private String getDayLabel(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY:    return "Monday";
            case Calendar.TUESDAY:   return "Tuesday";
            case Calendar.WEDNESDAY: return "Wednesday";
            case Calendar.THURSDAY:  return "Thursday";
            case Calendar.FRIDAY:    return "Friday";
            case Calendar.SATURDAY:  return "Saturday";
            case Calendar.SUNDAY:    return "Sunday";
            default:                 return "this day";
        }
    }

    private String triggerToPhrase(String trigger) {
        if (trigger == null) return null;
        switch (trigger) {
            case AppConstants.TRIGGER_NOISE:    return "loud sounds";
            case AppConstants.TRIGGER_LIGHT:    return "bright lights";
            case AppConstants.TRIGGER_CROWD:    return "crowds";
            case AppConstants.TRIGGER_CHANGE:   return "unexpected changes";
            case AppConstants.TRIGGER_TEXTURE:  return "textures";
            case AppConstants.TRIGGER_SMELL:    return "strong smells";
            case AppConstants.TRIGGER_MULTIPLE: return "multiple sensory inputs";
            default:                            return null;
        }
    }
}
