package com.senseshield.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.senseshield.models.SensoryEvent;
import com.senseshield.models.SensoryProfile;
import com.senseshield.models.User;
import com.senseshield.utils.AppConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseHelper.java
 * Central SQLite manager for SenseShield.
 *
 * Tables:
 *   users            — user identity + theme preference
 *   sensory_profile  — per-user sensitivity levels and preferences (1 row per user)
 *   sensory_events   — log of every overload event (many rows per user)
 *
 * All CRUD operations for all three tables live here.
 * Using a singleton pattern so there's only ever one DB connection open.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static DatabaseHelper instance;

    // ─── Column names: users ─────────────────────────────────────────────────────
    private static final String COL_U_ID               = "id";
    private static final String COL_U_NAME             = "name";
    private static final String COL_U_AVATAR_COLOR     = "avatar_color";
    private static final String COL_U_THEME            = "preferred_theme";
    private static final String COL_U_CAREGIVER_PIN    = "caregiver_pin_hash";
    private static final String COL_U_CREATED_AT       = "created_at";
    private static final String COL_U_MODE             = "mode";          // 0=person, 1=caregiver
    private static final String COL_U_CAREGIVER_ID     = "caregiver_id";  // links patient → caregiver

    // ─── Column names: sensory_profile ───────────────────────────────────────────
    private static final String COL_P_ID               = "id";
    private static final String COL_P_USER_ID          = "user_id";
    private static final String COL_P_NOISE            = "noise_sensitivity";
    private static final String COL_P_LIGHT            = "light_sensitivity";
    private static final String COL_P_CROWD            = "crowd_sensitivity";
    private static final String COL_P_TEXTURE          = "texture_sensitivity";
    private static final String COL_P_SMELL            = "smell_sensitivity";
    private static final String COL_P_CHANGE           = "change_sensitivity";
    private static final String COL_P_CALM_TOOLS       = "preferred_calm_tools";
    private static final String COL_P_SOUND_TRACK      = "preferred_sound_track";
    private static final String COL_P_ALERTS_ENABLED   = "proactive_alerts_enabled";
    private static final String COL_P_ALERT_LEAD       = "alert_lead_time_minutes";
    private static final String COL_P_NOTES            = "notes";
    private static final String COL_P_UPDATED_AT       = "updated_at";

    // ─── Column names: sensory_events ────────────────────────────────────────────
    private static final String COL_E_ID               = "id";
    private static final String COL_E_USER_ID          = "user_id";
    private static final String COL_E_TRIGGER          = "trigger_type";
    private static final String COL_E_SEVERITY         = "severity";
    private static final String COL_E_LOCATION         = "location_tag";
    private static final String COL_E_CALM_TOOL        = "calm_tool_used";
    private static final String COL_E_EFFECTIVENESS    = "effectiveness";
    private static final String COL_E_TIMESTAMP        = "timestamp";
    private static final String COL_E_HOUR             = "hour_of_day";
    private static final String COL_E_DAY              = "day_of_week";
    private static final String COL_E_NOTES            = "notes";
    private static final String COL_E_FROM_ALERT       = "from_proactive_alert";

    // ─── CREATE TABLE statements ─────────────────────────────────────────────────

    private static final String CREATE_USERS =
        "CREATE TABLE " + AppConstants.TABLE_USERS + " (" +
        COL_U_ID            + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_U_NAME          + " TEXT NOT NULL, " +
        COL_U_AVATAR_COLOR  + " TEXT DEFAULT '#7F77DD', " +
        COL_U_THEME         + " INTEGER DEFAULT 3, " +
        COL_U_CAREGIVER_PIN + " TEXT, " +
        COL_U_CREATED_AT    + " INTEGER NOT NULL, " +
        COL_U_MODE          + " INTEGER DEFAULT 0, " +
        COL_U_CAREGIVER_ID  + " INTEGER DEFAULT -1" +
        ");";

    private static final String CREATE_SENSORY_PROFILE =
        "CREATE TABLE " + AppConstants.TABLE_SENSORY_PROFILE + " (" +
        COL_P_ID             + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_P_USER_ID        + " INTEGER NOT NULL UNIQUE, " +   // One profile per user
        COL_P_NOISE          + " INTEGER DEFAULT 3, " +
        COL_P_LIGHT          + " INTEGER DEFAULT 3, " +
        COL_P_CROWD          + " INTEGER DEFAULT 3, " +
        COL_P_TEXTURE        + " INTEGER DEFAULT 2, " +
        COL_P_SMELL          + " INTEGER DEFAULT 2, " +
        COL_P_CHANGE         + " INTEGER DEFAULT 3, " +
        COL_P_CALM_TOOLS     + " TEXT DEFAULT 'BREATHING,SOUND_THERAPY', " +
        COL_P_SOUND_TRACK    + " TEXT DEFAULT 'sound_rain', " +
        COL_P_ALERTS_ENABLED + " INTEGER DEFAULT 1, " +         // 1 = true
        COL_P_ALERT_LEAD     + " INTEGER DEFAULT 15, " +
        COL_P_NOTES          + " TEXT, " +
        COL_P_UPDATED_AT     + " INTEGER NOT NULL, " +
        "FOREIGN KEY (" + COL_P_USER_ID + ") REFERENCES " + AppConstants.TABLE_USERS + "(" + COL_U_ID + ")" +
        ");";

    private static final String CREATE_SENSORY_EVENTS =
        "CREATE TABLE " + AppConstants.TABLE_SENSORY_EVENTS + " (" +
        COL_E_ID            + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_E_USER_ID       + " INTEGER NOT NULL, " +
        COL_E_TRIGGER       + " TEXT NOT NULL, " +
        COL_E_SEVERITY      + " INTEGER NOT NULL, " +
        COL_E_LOCATION      + " TEXT DEFAULT 'OTHER', " +
        COL_E_CALM_TOOL     + " TEXT DEFAULT 'NONE', " +
        COL_E_EFFECTIVENESS + " INTEGER DEFAULT 0, " +
        COL_E_TIMESTAMP     + " INTEGER NOT NULL, " +
        COL_E_HOUR          + " INTEGER NOT NULL, " +           // 0–23, for fast AI queries
        COL_E_DAY           + " INTEGER NOT NULL, " +           // 1–7 (Calendar.DAY_OF_WEEK)
        COL_E_NOTES         + " TEXT, " +
        COL_E_FROM_ALERT    + " INTEGER DEFAULT 0, " +          // 0 = false
        "FOREIGN KEY (" + COL_E_USER_ID + ") REFERENCES " + AppConstants.TABLE_USERS + "(" + COL_U_ID + ")" +
        ");";

    // ─── Singleton constructor ───────────────────────────────────────────────────

    private DatabaseHelper(Context context) {
        super(context.getApplicationContext(), AppConstants.DB_NAME, null, AppConstants.DB_VERSION);
    }

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context);
        }
        return instance;
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_USERS);
        db.execSQL(CREATE_SENSORY_PROFILE);
        db.execSQL(CREATE_SENSORY_EVENTS);
        Log.d(TAG, "Database created with all tables.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + AppConstants.TABLE_SENSORY_EVENTS);
        db.execSQL("DROP TABLE IF EXISTS " + AppConstants.TABLE_SENSORY_PROFILE);
        db.execSQL("DROP TABLE IF EXISTS " + AppConstants.TABLE_USERS);
        onCreate(db);
        Log.d(TAG, "Database upgraded from v" + oldVersion + " to v" + newVersion);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Allow downgrade by recreating tables
        onUpgrade(db, oldVersion, newVersion);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        // Enable foreign key enforcement
        db.setForeignKeyConstraintsEnabled(true);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // USER OPERATIONS
    // ════════════════════════════════════════════════════════════════════════════

    /** Inserts a new user. Returns the new row ID, or -1 on failure. */
    public long insertUser(User user) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_U_NAME,          user.getName());
        cv.put(COL_U_AVATAR_COLOR,  user.getAvatarColor());
        cv.put(COL_U_THEME,         user.getPreferredTheme());
        cv.put(COL_U_CAREGIVER_PIN, user.getCaregiverPinHash());
        cv.put(COL_U_CREATED_AT,    user.getCreatedAt());
        cv.put(COL_U_MODE,          user.getMode());
        cv.put(COL_U_CAREGIVER_ID,  user.getCaregiverId());
        long id = db.insert(AppConstants.TABLE_USERS, null, cv);
        Log.d(TAG, "Inserted user id=" + id + " mode=" + user.getMode());
        return id;
    }

    /** Returns a User by ID, or null if not found. */
    public User getUserById(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(
            AppConstants.TABLE_USERS,
            null,
            COL_U_ID + "=?",
            new String[]{String.valueOf(userId)},
            null, null, null
        );
        User user = null;
        if (c != null && c.moveToFirst()) {
            user = cursorToUser(c);
            c.close();
        }
        return user;
    }

    /** Returns all users (for multi-profile support). */
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(AppConstants.TABLE_USERS, null, null, null, null, null,
                            COL_U_CREATED_AT + " ASC");
        if (c != null) {
            while (c.moveToNext()) users.add(cursorToUser(c));
            c.close();
        }
        return users;
    }

    /** Updates an existing user. Returns rows affected. */
    public int updateUser(User user) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_U_NAME,          user.getName());
        cv.put(COL_U_AVATAR_COLOR,  user.getAvatarColor());
        cv.put(COL_U_THEME,         user.getPreferredTheme());
        cv.put(COL_U_CAREGIVER_PIN, user.getCaregiverPinHash());
        cv.put(COL_U_MODE,          user.getMode());
        cv.put(COL_U_CAREGIVER_ID,  user.getCaregiverId());
        return db.update(AppConstants.TABLE_USERS, cv, COL_U_ID + "=?",
                         new String[]{String.valueOf(user.getId())});
    }

    /** Returns all patients linked to a caregiver. */
    public List<User> getPatientsByCaregiver(int caregiverId) {
        List<User> patients = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(AppConstants.TABLE_USERS, null,
                COL_U_CAREGIVER_ID + "=? AND " + COL_U_MODE + "=?",
                new String[]{String.valueOf(caregiverId), String.valueOf(AppConstants.MODE_PERSON)},
                null, null, COL_U_CREATED_AT + " ASC");
        if (c != null) {
            while (c.moveToNext()) patients.add(cursorToUser(c));
            c.close();
        }
        return patients;
    }

    private User cursorToUser(Cursor c) {
        User user = new User();
        user.setId(         c.getInt(   c.getColumnIndexOrThrow(COL_U_ID)));
        user.setName(       c.getString(c.getColumnIndexOrThrow(COL_U_NAME)));
        user.setAvatarColor(c.getString(c.getColumnIndexOrThrow(COL_U_AVATAR_COLOR)));
        user.setPreferredTheme(c.getInt(c.getColumnIndexOrThrow(COL_U_THEME)));
        user.setCaregiverPinHash(c.getString(c.getColumnIndexOrThrow(COL_U_CAREGIVER_PIN)));
        user.setCreatedAt(  c.getLong(  c.getColumnIndexOrThrow(COL_U_CREATED_AT)));
        int modeIdx = c.getColumnIndex(COL_U_MODE);
        if (modeIdx >= 0) user.setMode(c.getInt(modeIdx));
        int cgIdx = c.getColumnIndex(COL_U_CAREGIVER_ID);
        if (cgIdx >= 0) user.setCaregiverId(c.getInt(cgIdx));
        return user;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // SENSORY PROFILE OPERATIONS
    // ════════════════════════════════════════════════════════════════════════════

    /** Inserts a new profile. Returns the new row ID. */
    public long insertProfile(SensoryProfile p) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = profileToContentValues(p);
        cv.put(COL_P_USER_ID, p.getUserId());
        return db.insert(AppConstants.TABLE_SENSORY_PROFILE, null, cv);
    }

    /** Returns the profile for a given user, or null. */
    public SensoryProfile getProfileByUserId(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(
            AppConstants.TABLE_SENSORY_PROFILE,
            null,
            COL_P_USER_ID + "=?",
            new String[]{String.valueOf(userId)},
            null, null, null
        );
        SensoryProfile profile = null;
        if (c != null && c.moveToFirst()) {
            profile = cursorToProfile(c);
            c.close();
        }
        return profile;
    }

    /** Updates an existing profile. Returns rows affected. */
    public int updateProfile(SensoryProfile p) {
        SQLiteDatabase db = getWritableDatabase();
        p.setUpdatedAt(System.currentTimeMillis());
        ContentValues cv = profileToContentValues(p);
        return db.update(AppConstants.TABLE_SENSORY_PROFILE, cv,
                         COL_P_USER_ID + "=?",
                         new String[]{String.valueOf(p.getUserId())});
    }

    private ContentValues profileToContentValues(SensoryProfile p) {
        ContentValues cv = new ContentValues();
        cv.put(COL_P_NOISE,           p.getNoiseSensitivity());
        cv.put(COL_P_LIGHT,           p.getLightSensitivity());
        cv.put(COL_P_CROWD,           p.getCrowdSensitivity());
        cv.put(COL_P_TEXTURE,         p.getTextureSensitivity());
        cv.put(COL_P_SMELL,           p.getSmellSensitivity());
        cv.put(COL_P_CHANGE,          p.getChangeSensitivity());
        cv.put(COL_P_CALM_TOOLS,      p.getPreferredCalmTools());
        cv.put(COL_P_SOUND_TRACK,     p.getPreferredSoundTrack());
        cv.put(COL_P_ALERTS_ENABLED,  p.isProactiveAlertsEnabled() ? 1 : 0);
        cv.put(COL_P_ALERT_LEAD,      p.getAlertLeadTimeMinutes());
        cv.put(COL_P_NOTES,           p.getNotes());
        cv.put(COL_P_UPDATED_AT,      System.currentTimeMillis());
        return cv;
    }

    private SensoryProfile cursorToProfile(Cursor c) {
        SensoryProfile p = new SensoryProfile();
        p.setId(                    c.getInt(   c.getColumnIndexOrThrow(COL_P_ID)));
        p.setUserId(                c.getInt(   c.getColumnIndexOrThrow(COL_P_USER_ID)));
        p.setNoiseSensitivity(      c.getInt(   c.getColumnIndexOrThrow(COL_P_NOISE)));
        p.setLightSensitivity(      c.getInt(   c.getColumnIndexOrThrow(COL_P_LIGHT)));
        p.setCrowdSensitivity(      c.getInt(   c.getColumnIndexOrThrow(COL_P_CROWD)));
        p.setTextureSensitivity(    c.getInt(   c.getColumnIndexOrThrow(COL_P_TEXTURE)));
        p.setSmellSensitivity(      c.getInt(   c.getColumnIndexOrThrow(COL_P_SMELL)));
        p.setChangeSensitivity(     c.getInt(   c.getColumnIndexOrThrow(COL_P_CHANGE)));
        p.setPreferredCalmTools(    c.getString(c.getColumnIndexOrThrow(COL_P_CALM_TOOLS)));
        p.setPreferredSoundTrack(   c.getString(c.getColumnIndexOrThrow(COL_P_SOUND_TRACK)));
        p.setProactiveAlertsEnabled(c.getInt(   c.getColumnIndexOrThrow(COL_P_ALERTS_ENABLED)) == 1);
        p.setAlertLeadTimeMinutes(  c.getInt(   c.getColumnIndexOrThrow(COL_P_ALERT_LEAD)));
        p.setNotes(                 c.getString(c.getColumnIndexOrThrow(COL_P_NOTES)));
        p.setUpdatedAt(             c.getLong(  c.getColumnIndexOrThrow(COL_P_UPDATED_AT)));
        return p;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // SENSORY EVENT OPERATIONS
    // ════════════════════════════════════════════════════════════════════════════

    /** Inserts a new sensory event. Returns the new row ID. */
    public long insertEvent(SensoryEvent event) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = eventToContentValues(event);
        cv.put(COL_E_USER_ID, event.getUserId());
        long id = db.insert(AppConstants.TABLE_SENSORY_EVENTS, null, cv);
        Log.d(TAG, "Logged sensory event id=" + id + " trigger=" + event.getTriggerType());
        return id;
    }

    /** Updates an existing event (e.g., to add effectiveness rating after calming). */
    public int updateEvent(SensoryEvent event) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = eventToContentValues(event);
        return db.update(AppConstants.TABLE_SENSORY_EVENTS, cv,
                         COL_E_ID + "=?",
                         new String[]{String.valueOf(event.getId())});
    }

    /** Returns all events for a user, most recent first. */
    public List<SensoryEvent> getEventsByUser(int userId) {
        return queryEvents(userId, null, null);
    }

    /** Returns events from the last N days (used by the AI engine). */
    public List<SensoryEvent> getRecentEvents(int userId, int days) {
        long cutoff = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
        return queryEvents(userId,
                           COL_E_TIMESTAMP + " >= ?",
                           new String[]{String.valueOf(cutoff)});
    }

    /**
     * Returns event counts grouped by (hour_of_day, day_of_week) for pattern analysis.
     * Returns a 2D array: [hour][day] = count.
     * Hours: 0–23, Days: 0–6 (mapped from Calendar 1–7).
     */
    public int[][] getEventCountByTimeSlot(int userId, int limitDays) {
        int[][] grid = new int[24][7];
        long cutoff = System.currentTimeMillis() - ((long) limitDays * 24 * 60 * 60 * 1000);

        SQLiteDatabase db = getReadableDatabase();
        String query =
            "SELECT " + COL_E_HOUR + ", " + COL_E_DAY + ", COUNT(*) as cnt " +
            "FROM " + AppConstants.TABLE_SENSORY_EVENTS + " " +
            "WHERE " + COL_E_USER_ID + "=? AND " + COL_E_TIMESTAMP + " >= ? " +
            "GROUP BY " + COL_E_HOUR + ", " + COL_E_DAY;

        Cursor c = db.rawQuery(query, new String[]{
            String.valueOf(userId), String.valueOf(cutoff)
        });

        if (c != null) {
            while (c.moveToNext()) {
                int hour  = c.getInt(0);
                int day   = c.getInt(1) - 1; // Calendar is 1–7, shift to 0–6
                int count = c.getInt(2);
                if (hour >= 0 && hour < 24 && day >= 0 && day < 7) {
                    grid[hour][day] = count;
                }
            }
            c.close();
        }
        return grid;
    }

    /**
     * Returns which calm tool has the highest average effectiveness for this user.
     * Used by the AI to recommend the best first tool.
     */
    public String getMostEffectiveCalmTool(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        String query =
            "SELECT " + COL_E_CALM_TOOL + ", AVG(" + COL_E_EFFECTIVENESS + ") as avg_eff " +
            "FROM " + AppConstants.TABLE_SENSORY_EVENTS + " " +
            "WHERE " + COL_E_USER_ID + "=? AND " + COL_E_EFFECTIVENESS + " > 0 " +
            "GROUP BY " + COL_E_CALM_TOOL + " " +
            "ORDER BY avg_eff DESC LIMIT 1";

        Cursor c = db.rawQuery(query, new String[]{String.valueOf(userId)});
        String bestTool = AppConstants.CALM_BREATHING; // default
        if (c != null && c.moveToFirst()) {
            bestTool = c.getString(0);
            c.close();
        }
        return bestTool;
    }

    /**
     * Returns which trigger type appeared most often for this user
     * in the last {@code days} days, excluding UNKNOWN events.
     * Returns null if no qualifying events exist.
     */
    public String getMostFrequentTrigger(int userId, int days) {
        long cutoff = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
        SQLiteDatabase db = getReadableDatabase();
        String query =
            "SELECT " + COL_E_TRIGGER + ", COUNT(*) AS cnt " +
            "FROM " + AppConstants.TABLE_SENSORY_EVENTS + " " +
            "WHERE " + COL_E_USER_ID + "=? " +
            "  AND " + COL_E_TIMESTAMP + " >= ? " +
            "  AND " + COL_E_TRIGGER + " != '" + AppConstants.TRIGGER_UNKNOWN + "' " +
            "GROUP BY " + COL_E_TRIGGER + " " +
            "ORDER BY cnt DESC LIMIT 1";
        Cursor c = db.rawQuery(query, new String[]{
            String.valueOf(userId), String.valueOf(cutoff)
        });
        String trigger = null;
        if (c != null && c.moveToFirst()) {
            trigger = c.getString(0);
            c.close();
        }
        return trigger;
    }

    /**
     * Returns the average severity of events in the last {@code days} days.
     * Returns 0 if there are no events in that window.
     */
    public float getAverageSeverity(int userId, int days) {
        long cutoff = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT AVG(" + COL_E_SEVERITY + ") FROM " + AppConstants.TABLE_SENSORY_EVENTS +
            " WHERE " + COL_E_USER_ID + "=? AND " + COL_E_TIMESTAMP + " >= ?",
            new String[]{String.valueOf(userId), String.valueOf(cutoff)}
        );
        float avg = 0f;
        if (c != null) {
            if (c.moveToFirst()) avg = c.getFloat(0);
            c.close();
        }
        return avg;
    }

    /**
     * Returns the number of events logged by this user in the last {@code days} days.
     */
    public int getEventCountForDays(int userId, int days) {
        long cutoff = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT COUNT(*) FROM " + AppConstants.TABLE_SENSORY_EVENTS +
            " WHERE " + COL_E_USER_ID + "=? AND " + COL_E_TIMESTAMP + " >= ?",
            new String[]{String.valueOf(userId), String.valueOf(cutoff)}
        );
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) count = c.getInt(0);
            c.close();
        }
        return count;
    }

    /**
     * Returns the number of events with a specific trigger type in the last {@code days} days.
     * Used by PatternAnalyzer for multi-trigger co-occurrence detection.
     */
    public int getEventCountByTrigger(int userId, String triggerType, int days) {
        long cutoff = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT COUNT(*) FROM " + AppConstants.TABLE_SENSORY_EVENTS +
            " WHERE " + COL_E_USER_ID + "=? AND " + COL_E_TRIGGER + "=? AND " +
            COL_E_TIMESTAMP + " >= ?",
            new String[]{String.valueOf(userId), triggerType, String.valueOf(cutoff)}
        );
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) count = c.getInt(0);
            c.close();
        }
        return count;
    }

    // ── ML / risk predictor helpers ──────────────────────────────────────────

    /**
     * Returns the number of events logged since {@code sinceTimestamp} (epoch ms).
     * Used by SensoryRiskPredictor for 1-hour and 3-hour event frequency features.
     */
    public int getEventCountSince(int userId, long sinceTimestamp) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT COUNT(*) FROM " + AppConstants.TABLE_SENSORY_EVENTS +
            " WHERE " + COL_E_USER_ID + "=? AND " + COL_E_TIMESTAMP + " >= ?",
            new String[]{String.valueOf(userId), String.valueOf(sinceTimestamp)}
        );
        int count = 0;
        if (c != null) { if (c.moveToFirst()) count = c.getInt(0); c.close(); }
        return count;
    }

    /**
     * Returns the number of events with a specific trigger type since {@code sinceTimestamp}.
     * Used to build per-dimension exposure features for the risk model.
     */
    public int getEventCountByTriggerSince(int userId, String triggerType, long sinceTimestamp) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT COUNT(*) FROM " + AppConstants.TABLE_SENSORY_EVENTS +
            " WHERE " + COL_E_USER_ID + "=? AND " + COL_E_TRIGGER + "=?" +
            " AND " + COL_E_TIMESTAMP + " >= ?",
            new String[]{String.valueOf(userId), triggerType, String.valueOf(sinceTimestamp)}
        );
        int count = 0;
        if (c != null) { if (c.moveToFirst()) count = c.getInt(0); c.close(); }
        return count;
    }

    /**
     * Returns the average severity of events since {@code sinceTimestamp}.
     * Returns 0 if there are no events in that window.
     */
    public float getAverageSeveritySince(int userId, long sinceTimestamp) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT AVG(" + COL_E_SEVERITY + ") FROM " + AppConstants.TABLE_SENSORY_EVENTS +
            " WHERE " + COL_E_USER_ID + "=? AND " + COL_E_TIMESTAMP + " >= ?",
            new String[]{String.valueOf(userId), String.valueOf(sinceTimestamp)}
        );
        float avg = 0f;
        if (c != null) { if (c.moveToFirst()) avg = c.getFloat(0); c.close(); }
        return avg;
    }

    /** Returns the total number of events logged by this user. */
    public int getEventCount(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT COUNT(*) FROM " + AppConstants.TABLE_SENSORY_EVENTS + " WHERE " + COL_E_USER_ID + "=?",
            new String[]{String.valueOf(userId)}
        );
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) count = c.getInt(0);
            c.close();
        }
        return count;
    }

    // ─── Private query helper ────────────────────────────────────────────────────

    private List<SensoryEvent> queryEvents(int userId, String extraWhere, String[] extraArgs) {
        List<SensoryEvent> events = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String where = COL_E_USER_ID + "=?";
        String[] args = {String.valueOf(userId)};

        if (extraWhere != null) {
            where += " AND " + extraWhere;
            String[] merged = new String[extraArgs.length + 1];
            merged[0] = String.valueOf(userId);
            System.arraycopy(extraArgs, 0, merged, 1, extraArgs.length);
            args = merged;
        }

        Cursor c = db.query(
            AppConstants.TABLE_SENSORY_EVENTS,
            null, where, args, null, null,
            COL_E_TIMESTAMP + " DESC"
        );

        if (c != null) {
            while (c.moveToNext()) events.add(cursorToEvent(c));
            c.close();
        }
        return events;
    }

    private ContentValues eventToContentValues(SensoryEvent e) {
        ContentValues cv = new ContentValues();
        cv.put(COL_E_TRIGGER,       e.getTriggerType());
        cv.put(COL_E_SEVERITY,      e.getSeverity());
        cv.put(COL_E_LOCATION,      e.getLocationTag());
        cv.put(COL_E_CALM_TOOL,     e.getCalmToolUsed());
        cv.put(COL_E_EFFECTIVENESS, e.getEffectiveness());
        cv.put(COL_E_TIMESTAMP,     e.getTimestamp());
        cv.put(COL_E_HOUR,          e.getHourOfDay());
        cv.put(COL_E_DAY,           e.getDayOfWeek());
        cv.put(COL_E_NOTES,         e.getNotes());
        cv.put(COL_E_FROM_ALERT,    e.isFromProactiveAlert() ? 1 : 0);
        return cv;
    }

    private SensoryEvent cursorToEvent(Cursor c) {
        SensoryEvent e = new SensoryEvent();
        e.setId(              c.getInt(   c.getColumnIndexOrThrow(COL_E_ID)));
        e.setUserId(          c.getInt(   c.getColumnIndexOrThrow(COL_E_USER_ID)));
        e.setTriggerType(     c.getString(c.getColumnIndexOrThrow(COL_E_TRIGGER)));
        e.setSeverity(        c.getInt(   c.getColumnIndexOrThrow(COL_E_SEVERITY)));
        e.setLocationTag(     c.getString(c.getColumnIndexOrThrow(COL_E_LOCATION)));
        e.setCalmToolUsed(    c.getString(c.getColumnIndexOrThrow(COL_E_CALM_TOOL)));
        e.setEffectiveness(   c.getInt(   c.getColumnIndexOrThrow(COL_E_EFFECTIVENESS)));
        e.setTimestamp(       c.getLong(  c.getColumnIndexOrThrow(COL_E_TIMESTAMP)));
        e.setHourOfDay(       c.getInt(   c.getColumnIndexOrThrow(COL_E_HOUR)));
        e.setDayOfWeek(       c.getInt(   c.getColumnIndexOrThrow(COL_E_DAY)));
        e.setNotes(           c.getString(c.getColumnIndexOrThrow(COL_E_NOTES)));
        e.setFromProactiveAlert(c.getInt( c.getColumnIndexOrThrow(COL_E_FROM_ALERT)) == 1);
        return e;
    }
}
