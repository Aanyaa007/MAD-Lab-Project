package com.senseshield.models;

/**
 * User.java
 * Represents a SenseShield user (the person with autism, or the profile being managed).
 * Kept intentionally minimal — no biometric data, no medical data.
 */
public class User {

    private int    id;
    private String name;
    private String avatarColor;     // Hex color string for the avatar circle (e.g. "#7F77DD")
    private int    preferredTheme;  // Maps to AppConstants.THEME_* values
    private String caregiverPinHash; // SHA-256 hash of caregiver PIN; null if not set
    private long   createdAt;       // Unix timestamp
    private int    mode;            // AppConstants.MODE_PERSON or MODE_CAREGIVER
    private int    caregiverId;     // ID of linked caregiver user; -1 if none

    // ─── Constructors ────────────────────────────────────────────────────────────

    public User() { this.caregiverId = -1; }

    public User(String name, String avatarColor, int preferredTheme) {
        this.name           = name;
        this.avatarColor    = avatarColor;
        this.preferredTheme = preferredTheme;
        this.createdAt      = System.currentTimeMillis();
        this.caregiverId    = -1;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────────

    public int getId()                        { return id; }
    public void setId(int id)                 { this.id = id; }

    public String getName()                   { return name; }
    public void setName(String name)          { this.name = name; }

    public String getAvatarColor()            { return avatarColor; }
    public void setAvatarColor(String color)  { this.avatarColor = color; }

    public int getPreferredTheme()                   { return preferredTheme; }
    public void setPreferredTheme(int preferredTheme){ this.preferredTheme = preferredTheme; }

    public String getCaregiverPinHash()               { return caregiverPinHash; }
    public void setCaregiverPinHash(String hash)      { this.caregiverPinHash = hash; }

    public long getCreatedAt()                { return createdAt; }
    public void setCreatedAt(long createdAt)  { this.createdAt = createdAt; }

    public int getMode()                      { return mode; }
    public void setMode(int mode)             { this.mode = mode; }

    public int getCaregiverId()               { return caregiverId; }
    public void setCaregiverId(int id)        { this.caregiverId = id; }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /** Returns initials (up to 2 chars) for the avatar circle. */
    public String getInitials() {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[1].charAt(0)).toUpperCase();
    }

    public boolean hasCaregiverPin() {
        return caregiverPinHash != null && !caregiverPinHash.isEmpty();
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', theme=" + preferredTheme + "}";
    }
}
