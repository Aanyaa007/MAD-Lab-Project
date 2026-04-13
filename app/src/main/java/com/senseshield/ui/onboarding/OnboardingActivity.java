package com.senseshield.ui.onboarding;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.button.MaterialButton;
import com.senseshield.R;
import com.senseshield.SenseShieldApp;
import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.SensoryProfile;
import com.senseshield.models.User;
import com.senseshield.ui.home.HomeActivity;
import com.senseshield.ui.caregiver.CaregiverHomeActivity;
import com.senseshield.utils.AppConstants;
import com.senseshield.utils.UiClickUtils;

public class OnboardingActivity extends AppCompatActivity {

    public static final int TOTAL_STEPS_PERSON    = 6;
    public static final int TOTAL_STEPS_CAREGIVER = 4;

    private ProgressBar    progressBar;
    private TextView       tvStepLabel;
    private MaterialButton btnNext;
    private MaterialButton btnBackStep;
    private View[]         dots = new View[6];

    private int  currentStep = 0;
    private int  userMode    = AppConstants.MODE_PERSON;
    private int  totalSteps  = TOTAL_STEPS_PERSON;
    private boolean isFinishingOnboarding = false;

    private OnboardingWelcomeFragment     welcomeFragment;
    private OnboardingModeFragment        modeFragment;
    private OnboardingNameFragment        nameFragment;
    private OnboardingThemeFragment       themeFragment;
    private OnboardingSensitivityFragment sensitivityFragment;
    private OnboardingToolsFragment       toolsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setTheme(SenseShieldApp.getThemeResId(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        // Restore step AND mode if returning from recreate() after theme change
        SharedPreferences prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE);
        currentStep = prefs.getInt("onboarding_step", 0);
        userMode    = prefs.getInt("onboarding_mode", AppConstants.MODE_PERSON);
        totalSteps  = (userMode == AppConstants.MODE_CAREGIVER)
            ? TOTAL_STEPS_CAREGIVER : TOTAL_STEPS_PERSON;

        bindViews();
        createFragments();
        showStep(currentStep);
        setupButtons();
    }

    private void bindViews() {
        progressBar = findViewById(R.id.onboarding_progress);
        tvStepLabel = findViewById(R.id.tv_step_label);
        btnNext     = findViewById(R.id.btn_next);
        btnBackStep = findViewById(R.id.btn_back_step);
        int[] dotIds = {R.id.dot_0, R.id.dot_1, R.id.dot_2,
                        R.id.dot_3, R.id.dot_4, R.id.dot_5};
        for (int i = 0; i < dotIds.length; i++) dots[i] = findViewById(dotIds[i]);
    }

    private void createFragments() {
        welcomeFragment     = new OnboardingWelcomeFragment();
        modeFragment        = new OnboardingModeFragment();
        nameFragment        = new OnboardingNameFragment();
        themeFragment       = new OnboardingThemeFragment();
        sensitivityFragment = new OnboardingSensitivityFragment();
        toolsFragment       = new OnboardingToolsFragment();
    }

    private void showStep(int step) {
        currentStep = step;
        getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt("onboarding_step", step)
            .putInt("onboarding_mode", userMode)
            .apply();

        Fragment fragment;
        switch (step) {
            case 0:  fragment = welcomeFragment;     break;
            case 1:  fragment = modeFragment;        break;
            case 2:  fragment = nameFragment;        break;
            case 3:  fragment = themeFragment;       break;
            case 4:  fragment = sensitivityFragment; break;
            case 5:  fragment = toolsFragment;       break;
            default: fragment = welcomeFragment;
        }

        getSupportFragmentManager().beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.onboarding_container, fragment)
            .commit();

        updateUI(step);
    }

    private void updateUI(int step) {
        // Only query fragment if it has been shown (step >= 1);
        // otherwise rely on the persisted userMode from SharedPreferences
        if (step >= 1 && modeFragment.getView() != null) {
            userMode = modeFragment.getSelectedMode();
        }
        totalSteps = (userMode == AppConstants.MODE_CAREGIVER)
            ? TOTAL_STEPS_CAREGIVER : TOTAL_STEPS_PERSON;

        // Progress
        progressBar.setProgress((int)(((step + 1) / (float) totalSteps) * 100));
        tvStepLabel.setText((step + 1) + " / " + totalSteps);

        // Dots
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] == null) continue;
            boolean active = (i == step);
            dots[i].setVisibility(i < totalSteps ? View.VISIBLE : View.GONE);
            dots[i].setBackgroundResource(active ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
            android.view.ViewGroup.LayoutParams lp = dots[i].getLayoutParams();
            lp.width  = dpToPx(active ? 14 : 8);
            lp.height = dpToPx(active ? 14 : 8);
            dots[i].setLayoutParams(lp);
        }

        // Back button
        btnBackStep.setVisibility(step == 0 ? View.INVISIBLE : View.VISIBLE);

        // Next button text
        boolean isLast = step == totalSteps - 1;
        if (step == 0)     btnNext.setText("Let's begin →");
        else if (isLast)   btnNext.setText("All done! 🎉");
        else               btnNext.setText("Next →");
    }

    private void setupButtons() {
        UiClickUtils.setSafeClickListener(btnNext, v -> {
            if (isFinishingOnboarding) return;
            if (currentStep == 1) {
                userMode   = modeFragment.getSelectedMode();
                totalSteps = (userMode == AppConstants.MODE_CAREGIVER)
                    ? TOTAL_STEPS_CAREGIVER : TOTAL_STEPS_PERSON;
                // Persist mode so it survives theme-change recreate
                getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
                    .edit().putInt("onboarding_mode", userMode).apply();
            }
            if (currentStep < totalSteps - 1) showStep(currentStep + 1);
            else finishOnboarding();
        });

        UiClickUtils.setSafeClickListener(btnBackStep, v -> {
            if (currentStep > 0) {
                // If going back to mode selection, reset to person defaults
                if (currentStep == 2) {
                    userMode   = AppConstants.MODE_PERSON;
                    totalSteps = TOTAL_STEPS_PERSON;
                }
                showStep(currentStep - 1);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (currentStep > 0) showStep(currentStep - 1);
        else super.onBackPressed();
    }

    private void finishOnboarding() {
        isFinishingOnboarding = true;
        btnNext.setEnabled(false);

        // Use persisted userMode — modeFragment view may not exist after recreate
        if (modeFragment.getView() != null) {
            userMode = modeFragment.getSelectedMode();
        }

        try {
            String name  = nameFragment.getEnteredName();
            String color = nameFragment.getSelectedAvatarColor();
            int    theme = themeFragment.getSelectedTheme();

            boolean isPerson = (userMode == AppConstants.MODE_PERSON);
            String tools = isPerson ? toolsFragment.getSelectedTools() : AppConstants.CALM_BREATHING;
            String sound = isPerson ? toolsFragment.getSelectedSound() : AppConstants.SOUND_RAIN;
            SensoryProfile profile = isPerson ? sensitivityFragment.buildProfile() : new SensoryProfile();

            User user = new User(name, color, theme);
            user.setMode(userMode);
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            long userId = db.insertUser(user);

            profile.setUserId((int) userId);
            profile.setPreferredCalmTools(tools);
            profile.setPreferredSoundTrack(sound);
            db.insertProfile(profile);

            SenseShieldApp.saveTheme(this, theme);

            getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(AppConstants.PREF_ONBOARDING_DONE, true)
                .putInt(AppConstants.PREF_CURRENT_USER_ID, (int) userId)
                .putInt(AppConstants.PREF_USER_MODE, userMode)
                .putInt("onboarding_step", 0)
                .remove("onboarding_mode")  // clean up temp key
                .apply();

            Class<?> destination = (userMode == AppConstants.MODE_CAREGIVER)
                    ? CaregiverHomeActivity.class
                    : HomeActivity.class;
            startActivity(new Intent(this, destination));
            finish();
        } catch (Exception e) {
            // Prevent silent close if any unexpected onboarding state slips through.
            Toast.makeText(this, "Couldn't finish setup. Please tap All done again.", Toast.LENGTH_SHORT).show();
            btnNext.setEnabled(true);
            isFinishingOnboarding = false;
        }
    }

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }
}
