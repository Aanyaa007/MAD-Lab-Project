package com.senseshield.ui.onboarding;

import android.os.Bundle;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.slider.Slider;
import com.senseshield.R;
import com.senseshield.models.SensoryProfile;
import com.senseshield.utils.AppConstants;
import com.senseshield.utils.AccessibilityUtils;

/**
 * OnboardingSensitivityFragment.java
 * Step 4 of onboarding — the accessibility-first sensitivity setup screen.
 *
 * Each of the 6 sensory types is shown with:
 *   • A large emoji icon (visual, no literacy required)
 *   • A plain-language label
 *   • A Material Slider (1–5, step 1)
 *   • A live text label showing the current level name ("Moderate")
 *   • Full contentDescription for TalkBack
 *
 * Data is collected via buildProfile() when onboarding completes.
 */
public class OnboardingSensitivityFragment extends Fragment {

    // ─── Row data: icon, label, contentDescription ───────────────────────────
    private static final String[][] SENSITIVITY_ROWS = {
        // { emoji, label, contentDescription string key }
        { "🔊", "Loud sounds",       "cd_noise_slider"   },
        { "💡", "Bright lights",     "cd_light_slider"   },
        { "👥", "Crowded places",    "cd_crowd_slider"   },
        { "✋", "Touch & textures",  "cd_texture_slider" },
        { "👃", "Strong smells",     "cd_smell_slider"   },
        { "🔄", "Sudden changes",    "cd_change_slider"  },
    };

    private static final String[] LEVEL_LABELS = {
        "", // index 0 unused (slider is 1–5)
        "Not sensitive",
        "Slightly",
        "Moderate",
        "Very sensitive",
        "Extreme"
    };

    // Slider references in order matching SENSITIVITY_ROWS
    private final Slider[] sliders = new Slider[6];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(
            R.layout.fragment_onboarding_sensitivity, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        // The 6 slider rows included in the layout
        int[] rowIds = {
            R.id.slider_noise,
            R.id.slider_light,
            R.id.slider_crowd,
            R.id.slider_texture,
            R.id.slider_smell,
            R.id.slider_change
        };

        for (int i = 0; i < rowIds.length; i++) {
            View row = root.findViewById(rowIds[i]);
            setupRow(row, i);
        }
    }

    // ─── Row setup ────────────────────────────────────────────────────────────

    private void setupRow(View row, int index) {
        TextView tvIcon    = row.findViewById(R.id.tv_sensitivity_icon);
        TextView tvLabel   = row.findViewById(R.id.tv_sensitivity_label);
        TextView tvLevel   = row.findViewById(R.id.tv_sensitivity_level);
        Slider   slider    = row.findViewById(R.id.slider_sensitivity_value);

        String[] data = SENSITIVITY_ROWS[index];
        tvIcon.setText(data[0]);
        tvLabel.setText(data[1]);
        tvLevel.setText(LEVEL_LABELS[3]); // default = "Moderate" (value 3)

        // Accessibility: slider announces e.g. "Loud sounds sensitivity, 3 out of 5"
        slider.setContentDescription(data[1] + " sensitivity");

        // Live update of level label + accessibility announcement as slider moves
        slider.addOnChangeListener((s, value, fromUser) -> {
            int level = (int) value;
            tvLevel.setText(LEVEL_LABELS[level]);

            // Announce change to TalkBack
            Context context = getContext();
            if (fromUser && context != null && AccessibilityUtils.areAnimationsEnabled(context)) {
                AccessibilityUtils.announce(slider,
                    data[1] + ": " + LEVEL_LABELS[level]);
            }
        });

        sliders[index] = slider;
    }

    // ─── Data collection ──────────────────────────────────────────────────────

    /**
     * Called by OnboardingActivity.finishOnboarding() to build the SensoryProfile
     * from all 6 slider values.
     */
    public SensoryProfile buildProfile() {
        SensoryProfile profile = new SensoryProfile();
        if (sliders[0] != null) profile.setNoiseSensitivity(  (int) sliders[0].getValue());
        if (sliders[1] != null) profile.setLightSensitivity(  (int) sliders[1].getValue());
        if (sliders[2] != null) profile.setCrowdSensitivity(  (int) sliders[2].getValue());
        if (sliders[3] != null) profile.setTextureSensitivity((int) sliders[3].getValue());
        if (sliders[4] != null) profile.setSmellSensitivity(  (int) sliders[4].getValue());
        if (sliders[5] != null) profile.setChangeSensitivity( (int) sliders[5].getValue());
        return profile;
    }
}
