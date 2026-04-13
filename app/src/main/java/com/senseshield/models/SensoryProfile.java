package com.senseshield.models;

/**
 * SensoryProfile.java
 * Stores a user's individual sensory sensitivity levels and preferences.
 * Each sensitivity is rated 1–5 (maps to AppConstants.SENSITIVITY_LABELS).
 *
 * This is the heart of personalization — every recommendation and
 * prediction the AI makes is grounded in this profile.
 */
public class SensoryProfile {

    private int    id;
    private int    userId;

    // ─── Sensitivity levels (1 = minimal, 5 = extreme) ──────────────────────────
    private int noiseSensitivity;
    private int lightSensitivity;
    private int crowdSensitivity;
    private int textureSensitivity;
    private int smellSensitivity;
    private int changeSensitivity;     // Sensitivity to sudden changes / surprises

    // ─── Preferred calming tools (comma-separated AppConstants.CALM_* values) ────
    // e.g. "BREATHING,SOUND_THERAPY"
    private String preferredCalmTools;

    // ─── Preferred sound track ────────────────────────────────────────────────────
    private String preferredSoundTrack; // AppConstants.SOUND_* value

    // ─── Notification preferences ────────────────────────────────────────────────
    private boolean proactiveAlertsEnabled;
    private int     alertLeadTimeMinutes;   // How many minutes before predicted risk to alert

    // ─── Free text notes (caregiver or user can add context) ─────────────────────
    private String notes;

    private long updatedAt;

    // ─── Constructors ────────────────────────────────────────────────────────────

    public SensoryProfile() {
        // Defaults: moderate sensitivity on everything, all tools enabled
        this.noiseSensitivity       = 3;
        this.lightSensitivity       = 3;
        this.crowdSensitivity       = 3;
        this.textureSensitivity     = 2;
        this.smellSensitivity       = 2;
        this.changeSensitivity      = 3;
        this.preferredCalmTools     = "BREATHING,SOUND_THERAPY";
        this.preferredSoundTrack    = "sound_rain";
        this.proactiveAlertsEnabled = true;
        this.alertLeadTimeMinutes   = 15;
        this.updatedAt              = System.currentTimeMillis();
    }

    public SensoryProfile(int userId) {
        this();
        this.userId = userId;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────────

    public int getId()                          { return id; }
    public void setId(int id)                   { this.id = id; }

    public int getUserId()                      { return userId; }
    public void setUserId(int userId)           { this.userId = userId; }

    public int getNoiseSensitivity()            { return noiseSensitivity; }
    public void setNoiseSensitivity(int v)      { this.noiseSensitivity = clamp(v); }

    public int getLightSensitivity()            { return lightSensitivity; }
    public void setLightSensitivity(int v)      { this.lightSensitivity = clamp(v); }

    public int getCrowdSensitivity()            { return crowdSensitivity; }
    public void setCrowdSensitivity(int v)      { this.crowdSensitivity = clamp(v); }

    public int getTextureSensitivity()          { return textureSensitivity; }
    public void setTextureSensitivity(int v)    { this.textureSensitivity = clamp(v); }

    public int getSmellSensitivity()            { return smellSensitivity; }
    public void setSmellSensitivity(int v)      { this.smellSensitivity = clamp(v); }

    public int getChangeSensitivity()           { return changeSensitivity; }
    public void setChangeSensitivity(int v)     { this.changeSensitivity = clamp(v); }

    public String getPreferredCalmTools()                    { return preferredCalmTools; }
    public void setPreferredCalmTools(String tools)          { this.preferredCalmTools = tools; }

    public String getPreferredSoundTrack()                   { return preferredSoundTrack; }
    public void setPreferredSoundTrack(String track)         { this.preferredSoundTrack = track; }

    public boolean isProactiveAlertsEnabled()                { return proactiveAlertsEnabled; }
    public void setProactiveAlertsEnabled(boolean enabled)   { this.proactiveAlertsEnabled = enabled; }

    public int getAlertLeadTimeMinutes()                     { return alertLeadTimeMinutes; }
    public void setAlertLeadTimeMinutes(int minutes)         { this.alertLeadTimeMinutes = minutes; }

    public String getNotes()                    { return notes; }
    public void setNotes(String notes)          { this.notes = notes; }

    public long getUpdatedAt()                  { return updatedAt; }
    public void setUpdatedAt(long ts)           { this.updatedAt = ts; }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /** Returns overall sensitivity score (average of all six, rounded). */
    public int getOverallSensitivity() {
        return Math.round(
            (noiseSensitivity + lightSensitivity + crowdSensitivity
             + textureSensitivity + smellSensitivity + changeSensitivity) / 6.0f
        );
    }

    /** Returns true if the given calm tool is in the preferred list. */
    public boolean prefersTool(String toolConstant) {
        return preferredCalmTools != null && preferredCalmTools.contains(toolConstant);
    }

    /** Keeps sensitivity values within the valid 1–5 range. */
    private int clamp(int value) {
        return Math.max(1, Math.min(5, value));
    }
}
