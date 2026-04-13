package com.senseshield.models;

import java.util.Calendar;

/**
 * SensoryEvent.java
 * Records a single sensory overload event (or near-overload).
 * This is the raw data that feeds the AI pattern engine.
 *
 * Every time a user taps Emergency Calm or logs a manual event,
 * one of these is created. Over time, patterns emerge from these records.
 */
public class SensoryEvent {

    private int    id;
    private int    userId;

    // ─── What happened ───────────────────────────────────────────────────────────
    private String triggerType;     // AppConstants.TRIGGER_*
    private int    severity;        // 1–5 (AppConstants.SEVERITY_*)
    private String locationTag;     // AppConstants.LOCATION_*
    private String calmToolUsed;    // AppConstants.CALM_* — what they used to calm down
    private int    effectiveness;   // 1–5: how well the calming tool worked (0 = not rated)

    // ─── When it happened ────────────────────────────────────────────────────────
    private long   timestamp;       // Unix millis — full precision
    private int    hourOfDay;       // 0–23, extracted from timestamp for fast AI queries
    private int    dayOfWeek;       // 1=Sunday … 7=Saturday (Calendar constants)

    // ─── Optional notes ──────────────────────────────────────────────────────────
    private String notes;

    // ─── Was this triggered by a proactive alert? ────────────────────────────────
    private boolean fromProactiveAlert;

    // ─── Constructors ────────────────────────────────────────────────────────────

    public SensoryEvent() {}

    /**
     * Convenience constructor — automatically extracts hourOfDay and dayOfWeek
     * from the current time. Use this when logging a real-time event.
     */
    public SensoryEvent(int userId, String triggerType, int severity, String locationTag) {
        this.userId       = userId;
        this.triggerType  = triggerType;
        this.severity     = severity;
        this.locationTag  = locationTag;
        this.timestamp    = System.currentTimeMillis();
        this.calmToolUsed = "NONE";
        this.effectiveness = 0;

        // Extract time fields for AI analysis
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(this.timestamp);
        this.hourOfDay  = cal.get(Calendar.HOUR_OF_DAY);
        this.dayOfWeek  = cal.get(Calendar.DAY_OF_WEEK);
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────────

    public int getId()                               { return id; }
    public void setId(int id)                        { this.id = id; }

    public int getUserId()                           { return userId; }
    public void setUserId(int userId)                { this.userId = userId; }

    public String getTriggerType()                   { return triggerType; }
    public void setTriggerType(String triggerType)   { this.triggerType = triggerType; }

    public int getSeverity()                         { return severity; }
    public void setSeverity(int severity)            { this.severity = Math.max(1, Math.min(5, severity)); }

    public String getLocationTag()                   { return locationTag; }
    public void setLocationTag(String locationTag)   { this.locationTag = locationTag; }

    public String getCalmToolUsed()                  { return calmToolUsed; }
    public void setCalmToolUsed(String tool)         { this.calmToolUsed = tool; }

    public int getEffectiveness()                    { return effectiveness; }
    public void setEffectiveness(int effectiveness)  { this.effectiveness = effectiveness; }

    public long getTimestamp()                       { return timestamp; }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        this.hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
        this.dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
    }

    public int getHourOfDay()                        { return hourOfDay; }
    public void setHourOfDay(int hourOfDay)          { this.hourOfDay = hourOfDay; }

    public int getDayOfWeek()                        { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek)          { this.dayOfWeek = dayOfWeek; }

    public String getNotes()                         { return notes; }
    public void setNotes(String notes)               { this.notes = notes; }

    public boolean isFromProactiveAlert()                    { return fromProactiveAlert; }
    public void setFromProactiveAlert(boolean fromAlert)     { this.fromProactiveAlert = fromAlert; }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /** Returns a human-readable time slot label, e.g. "Afternoon (2pm–4pm)". */
    public String getTimeSlotLabel() {
        if (hourOfDay >= 5  && hourOfDay < 12) return "Morning";
        if (hourOfDay >= 12 && hourOfDay < 17) return "Afternoon";
        if (hourOfDay >= 17 && hourOfDay < 21) return "Evening";
        return "Night";
    }

    /** Returns true if the event was severe (severity >= 4). */
    public boolean isSevere() {
        return severity >= 4;
    }

    /** Returns true if a calming tool was rated effective (effectiveness >= 4). */
    public boolean wasToolEffective() {
        return effectiveness >= 4;
    }
}
