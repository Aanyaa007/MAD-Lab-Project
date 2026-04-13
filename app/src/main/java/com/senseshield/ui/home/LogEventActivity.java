package com.senseshield.ui.home;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.senseshield.R;
import com.senseshield.SenseShieldApp;
import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.SensoryEvent;
import com.senseshield.utils.AccessibilityUtils;
import com.senseshield.utils.AppConstants;
import com.senseshield.utils.UiClickUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class LogEventActivity extends AppCompatActivity {

    /** Pass this extra to log an event on behalf of a different user (e.g. caregiver logging for patient). */
    public static final String EXTRA_USER_ID = "extra_user_id";

    private MaterialCardView cardNoise, cardLight, cardCrowd,
                             cardTexture, cardSmell, cardChange;
    private TextView sev1, sev2, sev3, sev4, sev5;
    private TextView tvSeverityLabel;
    private ChipGroup cgLocation;
    private TextInputEditText etNotes;
    private MaterialButton btnSave;

    private String selectedTrigger   = AppConstants.TRIGGER_UNKNOWN;
    private int    selectedSeverity  = 3;
    private String selectedLocation  = AppConstants.LOCATION_HOME;
    private long   selectedTimestamp = System.currentTimeMillis(); // defaults to now

    private TextView         tvSelectedTime;
    private MaterialCardView cardTimePicker;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    private static final String[] SEVERITY_LABELS = {
        "", "Very mild", "Mild", "Moderate", "Strong", "Extreme"
    };
    private static final int[] SEVERITY_COLORS = {
        0, R.color.severity_1, R.color.severity_2,
        R.color.severity_3, R.color.severity_4, R.color.severity_5
    };

    private DatabaseHelper db;
    private int userId = -1;

    private int appliedThemeResId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedThemeResId = SenseShieldApp.getThemeResId(this);
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_event);

        db = DatabaseHelper.getInstance(this);
        // Allow caregiver to log on behalf of a patient by passing EXTRA_USER_ID
        userId = getIntent().getIntExtra(EXTRA_USER_ID, -1);
        if (userId == -1) {
            userId = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
                         .getInt(AppConstants.PREF_CURRENT_USER_ID, -1);
        }

        bindViews();
        setupTriggerCards();
        setupSeverityButtons();
        setupLocationChips();
        setupTimePicker();
        setupSaveButton();
        updateSeverityUI(3);

        TextView btnBack = findViewById(R.id.btn_back);
        UiClickUtils.setSafeClickListener(btnBack, v -> finish());
    }

    private void bindViews() {
        tvSelectedTime  = findViewById(R.id.tv_selected_time);
        cardTimePicker  = findViewById(R.id.card_time_picker);
        cardNoise       = findViewById(R.id.card_trigger_noise);
        cardLight   = findViewById(R.id.card_trigger_light);
        cardCrowd   = findViewById(R.id.card_trigger_crowd);
        cardTexture = findViewById(R.id.card_trigger_texture);
        cardSmell   = findViewById(R.id.card_trigger_smell);
        cardChange  = findViewById(R.id.card_trigger_change);
        sev1 = findViewById(R.id.sev_1); sev2 = findViewById(R.id.sev_2);
        sev3 = findViewById(R.id.sev_3); sev4 = findViewById(R.id.sev_4);
        sev5 = findViewById(R.id.sev_5);
        tvSeverityLabel = findViewById(R.id.tv_severity_label);
        cgLocation = findViewById(R.id.cg_location);
        etNotes    = findViewById(R.id.et_notes);
        btnSave    = findViewById(R.id.btn_save_event);
    }

    private void setupTriggerCards() {
        MaterialCardView[] cards = {
            cardNoise, cardLight, cardCrowd, cardTexture, cardSmell, cardChange };
        String[] triggers = {
            AppConstants.TRIGGER_NOISE, AppConstants.TRIGGER_LIGHT,
            AppConstants.TRIGGER_CROWD, AppConstants.TRIGGER_TEXTURE,
            AppConstants.TRIGGER_SMELL, AppConstants.TRIGGER_CHANGE };
        String[] labels = {
            "Loud sounds","Bright lights","Crowded","Touch","Strong smell","Sudden change"};

        for (int i = 0; i < cards.length; i++) {
            if (cards[i] == null) continue;
            final String trigger = triggers[i];
            final String label   = labels[i];
            final MaterialCardView card = cards[i];
            UiClickUtils.setSafeClickListener(card, v -> {
                for (MaterialCardView c : cards) {
                    if (c != null) { c.setStrokeWidth(0); c.setCardElevation(2f); }
                }
                card.setStrokeColor(getColor(R.color.standard_primary));
                card.setStrokeWidth(3);
                card.setCardElevation(8f);
                selectedTrigger = trigger;
                AccessibilityUtils.announce(card, label + " selected");
            });
        }
    }

    private void setupSeverityButtons() {
        TextView[] sevViews = { sev1, sev2, sev3, sev4, sev5 };
        for (int i = 0; i < sevViews.length; i++) {
            if (sevViews[i] == null) continue;
            final int severity = i + 1;
            final TextView view = sevViews[i];
            UiClickUtils.setSafeClickListener(view, v -> {
                selectedSeverity = severity;
                updateSeverityUI(severity);
                AccessibilityUtils.announce(view, SEVERITY_LABELS[severity] + " selected");
            });
        }
    }

    private void updateSeverityUI(int severity) {
        TextView[] sevViews = { sev1, sev2, sev3, sev4, sev5 };
        for (int i = 0; i < sevViews.length; i++) {
            if (sevViews[i] == null) continue;
            float scale = (i + 1 == severity) ? 1.3f : 1.0f;
            sevViews[i].setScaleX(scale);
            sevViews[i].setScaleY(scale);
        }
        if (tvSeverityLabel != null && severity >= 1 && severity <= 5) {
            tvSeverityLabel.setText(SEVERITY_LABELS[severity]);
            try { tvSeverityLabel.setTextColor(getColor(SEVERITY_COLORS[severity])); }
            catch (Exception ignored) {}
        }
    }

    private void setupLocationChips() {
        if (cgLocation == null) return;
        cgLocation.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if      (id == R.id.chip_loc_home)   selectedLocation = AppConstants.LOCATION_HOME;
            else if (id == R.id.chip_loc_school) selectedLocation = AppConstants.LOCATION_SCHOOL;
            else if (id == R.id.chip_loc_public) selectedLocation = AppConstants.LOCATION_PUBLIC;
            else                                 selectedLocation = AppConstants.LOCATION_OTHER;
        });
    }

    private void setupTimePicker() {
        if (cardTimePicker == null) return;
        UiClickUtils.setSafeClickListener(cardTimePicker, v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selectedTimestamp);
            int hour   = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);

            new TimePickerDialog(this, (view, h, m) -> {
                // Build a timestamp for today at the chosen h:m
                Calendar picked = Calendar.getInstance();
                picked.set(Calendar.HOUR_OF_DAY, h);
                picked.set(Calendar.MINUTE, m);
                picked.set(Calendar.SECOND, 0);
                picked.set(Calendar.MILLISECOND, 0);

                // If the chosen time is in the future, assume it was yesterday
                if (picked.getTimeInMillis() > System.currentTimeMillis()) {
                    picked.add(Calendar.DAY_OF_YEAR, -1);
                }

                selectedTimestamp = picked.getTimeInMillis();

                // Update label
                long now = System.currentTimeMillis();
                long diffMin = (now - selectedTimestamp) / 60000;
                String label;
                if (diffMin < 2)        label = "Right now";
                else if (diffMin < 60)  label = diffMin + " min ago";
                else                    label = timeFmt.format(picked.getTime());

                if (tvSelectedTime != null) tvSelectedTime.setText(label);
            }, hour, minute, false).show();
        });
    }

    private void setupSaveButton() {
        UiClickUtils.setSafeClickListener(btnSave, v -> {
            if (userId == -1) { finish(); return; }

            if (selectedTrigger.equals(AppConstants.TRIGGER_UNKNOWN)) {
                btnSave.setText("Select a trigger first");
                return;
            }
            SensoryEvent event = new SensoryEvent(
                userId, selectedTrigger, selectedSeverity, selectedLocation);
            // Use the user-chosen timestamp (defaults to now)
            event.setTimestamp(selectedTimestamp);
            // Re-derive hour/day from the chosen timestamp for the AI grid
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selectedTimestamp);
            event.setHourOfDay(cal.get(Calendar.HOUR_OF_DAY));
            event.setDayOfWeek(cal.get(Calendar.DAY_OF_WEEK));
            if (etNotes != null && etNotes.getText() != null) {
                String notes = etNotes.getText().toString().trim();
                if (!notes.isEmpty()) event.setNotes(notes);
            }
            db.insertEvent(event);
            btnSave.setText("Saved ✓");
            btnSave.postDelayed(this::finish, 600);
        });
    }
}
