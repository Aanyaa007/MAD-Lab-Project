package com.senseshield.ui.profile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.senseshield.R;
import com.senseshield.SenseShieldApp;
import com.senseshield.ui.SplashActivity;
import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.SensoryProfile;
import com.senseshield.models.User;
import com.senseshield.utils.AppConstants;
import com.senseshield.utils.UiClickUtils;

/**
 * ProfileActivity.java
 *
 * Shared by both MODE_PERSON and MODE_CAREGIVER — the same XML layout
 * (activity_profile.xml) is inflated for both, but when the logged-in
 * user is a caregiver this activity:
 *
 *   • Sets the page title to "Caregiver Profile"
 *   • Shows a subtitle: "Your account — manage patients from the dashboard"
 *   • Hides the entire sensitivity section (ll_sensitivity_section)
 *     because caregivers set PATIENT sensitivities from PatientDetailActivity
 *   • Rewrites the alerts toggle description to caregiver-facing copy
 *   • Skips loading / saving sensitivity slider values
 */
public class ProfileActivity extends AppCompatActivity {

    private static final String[] AVATAR_COLORS = {
            "#7F77DD", "#1D9E75", "#D85A30", "#378ADD", "#BA7517", "#D4537E"
    };
    private static final String[] COLOR_NAMES = {
            "Purple", "Teal", "Coral", "Blue", "Amber", "Pink"
    };
    private static final String[] LEVEL_LABELS = {
            "", "Not sensitive", "Slightly", "Moderate", "Very sensitive", "Extreme"
    };

    // ── Views ──────────────────────────────────────────────────────────────────
    private TextInputEditText etName;
    private TextView          tvAvatarLarge;
    private LinearLayout      llColorPicker;
    private ChipGroup         cgThemes;
    private SwitchMaterial    switchAlerts;
    private MaterialButton    btnSave;

    // Sensitivity sliders — only populated / used for MODE_PERSON
    private final Slider[] sensitivitySliders = new Slider[6];
    private View[]         colorCircles;

    private String selectedColor;
    private int    selectedTheme;

    // ── Data ───────────────────────────────────────────────────────────────────
    private DatabaseHelper db;
    private int            userId = -1;
    private User           user;
    private SensoryProfile profile;
    private boolean        isCaregiver = false;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(SenseShieldApp.getThemeResId(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        db = DatabaseHelper.getInstance(this);
        userId = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
                .getInt(AppConstants.PREF_CURRENT_USER_ID, -1);

        bindViews();
        loadData();
        applyModeUI();       // must come before populateUI so sliders exist
        populateUI();
        setupColorPicker();
        setupThemeChips();
        setupSaveButton();

        TextView btnBack = findViewById(R.id.btn_back);
        UiClickUtils.setSafeClickListener(btnBack, v -> finish());
        UiClickUtils.setSafeClickListener(findViewById(R.id.btn_reset), v -> confirmReset());
    }

    // ── Mode-aware UI ──────────────────────────────────────────────────────────

    /**
     * Adjusts the layout for the user's mode.
     * For caregivers: hide sensitivity sliders, update title + subtitle + alert copy.
     * For persons:    nothing extra — the layout defaults are already correct.
     */
    private void applyModeUI() {
        // ── Title ──────────────────────────────────────────────────────────────
        TextView tvTitle    = findViewById(R.id.tv_profile_title);
        TextView tvSubtitle = findViewById(R.id.tv_profile_subtitle);

        if (isCaregiver) {
            if (tvTitle != null) {
                tvTitle.setText("Caregiver Profile");
            }
            if (tvSubtitle != null) {
                tvSubtitle.setText("Your account — manage patients from the dashboard");
                tvSubtitle.setVisibility(View.VISIBLE);
            }
        }
        // else: defaults ("My Profile", subtitle gone) are already set in XML

        // ── Sensitivity section ────────────────────────────────────────────────
        View sensitivitySection = findViewById(R.id.ll_sensitivity_section);
        if (sensitivitySection != null) {
            // Caregivers set patient sensitivities from PatientDetailActivity
            sensitivitySection.setVisibility(isCaregiver ? View.GONE : View.VISIBLE);
        }

        // ── Alerts toggle description ──────────────────────────────────────────
        TextView tvAlertDesc = findViewById(R.id.tv_alert_description);
        if (tvAlertDesc != null && isCaregiver) {
            tvAlertDesc.setText("Get notified before your patient's predicted difficult times");
        }
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private void bindViews() {
        etName        = findViewById(R.id.et_name);
        tvAvatarLarge = findViewById(R.id.tv_avatar_large);
        llColorPicker = findViewById(R.id.ll_color_picker);
        cgThemes      = findViewById(R.id.cg_themes);
        switchAlerts  = findViewById(R.id.switch_alerts);
        btnSave       = findViewById(R.id.btn_save_profile);

        // Sliders are inside ll_sensitivity_section which may be GONE for caregivers,
        // but we still try to bind them — they'll simply be null-safe throughout.
        sensitivitySliders[0] = getSliderFromRow(R.id.slider_noise);
        sensitivitySliders[1] = getSliderFromRow(R.id.slider_light);
        sensitivitySliders[2] = getSliderFromRow(R.id.slider_crowd);
        sensitivitySliders[3] = getSliderFromRow(R.id.slider_texture);
        sensitivitySliders[4] = getSliderFromRow(R.id.slider_smell);
        sensitivitySliders[5] = getSliderFromRow(R.id.slider_change);
    }

    private Slider getSliderFromRow(int rowId) {
        View row = findViewById(rowId);
        if (row == null) return null;
        return row.findViewById(R.id.slider_sensitivity_value);
    }

    private void loadData() {
        if (userId == -1) return;
        user    = db.getUserById(userId);
        profile = db.getProfileByUserId(userId);
        if (profile == null) profile = new SensoryProfile(userId);

        selectedColor = (user != null) ? user.getAvatarColor() : AVATAR_COLORS[0];
        selectedTheme = (user != null) ? user.getPreferredTheme() : AppConstants.THEME_STANDARD;
        isCaregiver   = (user != null) && (user.getMode() == AppConstants.MODE_CAREGIVER);
    }

    private void populateUI() {
        if (profile == null) profile = new SensoryProfile(userId == -1 ? 0 : userId);

        // Name field
        if (user != null && etName != null) {
            etName.setText(user.getName());
            etName.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                public void onTextChanged(CharSequence s, int st, int b, int c) {}
                public void afterTextChanged(Editable s) { updateAvatarPreview(s.toString()); }
            });
        }
        updateAvatarPreview(user != null ? user.getName() : "");

        if (switchAlerts != null) {
            switchAlerts.setChecked(profile.isProactiveAlertsEnabled());
        }

        // Sensitivity sliders — only for persons; caregivers see them hidden
        if (!isCaregiver) {
            int[] values = {
                    profile.getNoiseSensitivity(),   profile.getLightSensitivity(),
                    profile.getCrowdSensitivity(),   profile.getTextureSensitivity(),
                    profile.getSmellSensitivity(),   profile.getChangeSensitivity()
            };
            String[] icons  = { "🔊", "💡", "👥", "✋", "👃", "🔄" };
            String[] labels = {
                    "Loud sounds", "Bright lights", "Crowded places",
                    "Touch", "Strong smells", "Sudden changes"
            };
            int[] rowIds = {
                    R.id.slider_noise, R.id.slider_light, R.id.slider_crowd,
                    R.id.slider_texture, R.id.slider_smell, R.id.slider_change
            };

            for (int i = 0; i < sensitivitySliders.length; i++) {
                if (sensitivitySliders[i] == null) continue;
                sensitivitySliders[i].setValue(values[i]);

                View row = findViewById(rowIds[i]);
                if (row == null) continue;
                TextView tvIcon  = row.findViewById(R.id.tv_sensitivity_icon);
                TextView tvLabel = row.findViewById(R.id.tv_sensitivity_label);
                TextView tvLevel = row.findViewById(R.id.tv_sensitivity_level);
                if (tvIcon  != null) tvIcon.setText(icons[i]);
                if (tvLabel != null) tvLabel.setText(labels[i]);
                if (tvLevel != null) tvLevel.setText(LEVEL_LABELS[values[i]]);

                final TextView levelView = tvLevel;
                sensitivitySliders[i].addOnChangeListener((s, value, fromUser) -> {
                    if (levelView != null) levelView.setText(LEVEL_LABELS[(int) value]);
                });
            }
        }
    }

    // ── Avatar ─────────────────────────────────────────────────────────────────

    private void updateAvatarPreview(String name) {
        if (tvAvatarLarge == null) return;
        String[] parts = name.trim().split("\\s+");
        String initials;
        if (name.trim().isEmpty()) {
            initials = "?";
        } else if (parts.length == 1) {
            initials = parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        } else {
            initials = (parts[0].charAt(0) + "" + parts[1].charAt(0)).toUpperCase();
        }
        tvAvatarLarge.setText(initials);
        try {
            tvAvatarLarge.getBackground().setTint(Color.parseColor(selectedColor));
        } catch (Exception ignored) {}
    }

    // ── Color picker ───────────────────────────────────────────────────────────

    private void setupColorPicker() {
        if (llColorPicker == null) return;
        colorCircles = new View[AVATAR_COLORS.length];
        for (int i = 0; i < AVATAR_COLORS.length; i++) {
            TextView circle = new TextView(this);
            int sizePx = (int) (48 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
            circle.setLayoutParams(lp);
            circle.setGravity(android.view.Gravity.CENTER);
            circle.setBackgroundResource(R.drawable.bg_avatar_circle);
            circle.getBackground().setTint(Color.parseColor(AVATAR_COLORS[i]));
            circle.setContentDescription(COLOR_NAMES[i] + " avatar color");
            circle.setClickable(true);
            circle.setFocusable(true);

            final int index = i;
            circle.setOnClickListener(v -> selectColor(index));
            colorCircles[i] = circle;
            llColorPicker.addView(circle);
        }
        for (int i = 0; i < AVATAR_COLORS.length; i++) {
            if (AVATAR_COLORS[i].equalsIgnoreCase(selectedColor)) {
                selectColor(i);
                break;
            }
        }
    }

    private void selectColor(int index) {
        for (int i = 0; i < colorCircles.length; i++) {
            if (colorCircles[i] == null) continue;
            colorCircles[i].setScaleX(i == index ? 1.2f : 1.0f);
            colorCircles[i].setScaleY(i == index ? 1.2f : 1.0f);
        }
        selectedColor = AVATAR_COLORS[index];
        updateAvatarPreview(
                etName != null && etName.getText() != null ? etName.getText().toString() : "");
    }

    // ── Theme chips ────────────────────────────────────────────────────────────

    private void setupThemeChips() {
        if (cgThemes == null) return;
        int[] chipIds = {
                R.id.chip_theme_warm, R.id.chip_theme_cool,
                R.id.chip_theme_dark, R.id.chip_theme_standard
        };
        int[] themeConstants = {
                AppConstants.THEME_WARM_DIM, AppConstants.THEME_COOL_MUTED,
                AppConstants.THEME_HIGH_CONTRAST, AppConstants.THEME_STANDARD
        };
        for (int i = 0; i < themeConstants.length; i++) {
            if (themeConstants[i] == selectedTheme) {
                cgThemes.check(chipIds[i]);
                break;
            }
        }
        cgThemes.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if      (id == R.id.chip_theme_warm)     selectedTheme = AppConstants.THEME_WARM_DIM;
            else if (id == R.id.chip_theme_cool)     selectedTheme = AppConstants.THEME_COOL_MUTED;
            else if (id == R.id.chip_theme_dark)     selectedTheme = AppConstants.THEME_HIGH_CONTRAST;
            else                                     selectedTheme = AppConstants.THEME_STANDARD;
        });
    }

    // ── Save ───────────────────────────────────────────────────────────────────

    private void setupSaveButton() {
        UiClickUtils.setSafeClickListener(btnSave, v -> saveProfile());
    }

    private void saveProfile() {
        if (userId == -1 || user == null || profile == null) return;

        // Name + avatar
        String name = (etName != null && etName.getText() != null)
                ? etName.getText().toString().trim() : "Friend";
        if (name.isEmpty()) name = "Friend";
        user.setName(name);
        user.setAvatarColor(selectedColor);
        user.setPreferredTheme(selectedTheme);
        db.updateUser(user);

        // Alerts toggle — applies to both modes
        if (switchAlerts != null) {
            profile.setProactiveAlertsEnabled(switchAlerts.isChecked());
        }

        // Sensitivity sliders — only save for persons; skip entirely for caregivers
        if (!isCaregiver) {
            if (sensitivitySliders[0] != null) profile.setNoiseSensitivity(   (int) sensitivitySliders[0].getValue());
            if (sensitivitySliders[1] != null) profile.setLightSensitivity(   (int) sensitivitySliders[1].getValue());
            if (sensitivitySliders[2] != null) profile.setCrowdSensitivity(   (int) sensitivitySliders[2].getValue());
            if (sensitivitySliders[3] != null) profile.setTextureSensitivity( (int) sensitivitySliders[3].getValue());
            if (sensitivitySliders[4] != null) profile.setSmellSensitivity(   (int) sensitivitySliders[4].getValue());
            if (sensitivitySliders[5] != null) profile.setChangeSensitivity(  (int) sensitivitySliders[5].getValue());
        }
        db.updateProfile(profile);

        SenseShieldApp.saveTheme(this, selectedTheme);

        if (btnSave != null) {
            btnSave.setText("Saved ✓");
            btnSave.postDelayed(this::recreate, 600);
        }
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Logout?")
                .setMessage("You'll be taken back to the welcome screen.")
                .setPositiveButton("Logout", (d, w) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        SenseShieldApp.saveTheme(this, AppConstants.THEME_STANDARD);
        getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE).edit().clear().commit();
        Intent intent = new Intent(ProfileActivity.this, SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }
}