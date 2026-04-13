package com.senseshield.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.senseshield.R;
import com.senseshield.SenseShieldApp;
import com.senseshield.utils.AppConstants;

/**
 * OnboardingThemeFragment
 * Saves theme selection when a card is tapped.
 * We avoid Activity.recreate() here to keep onboarding smooth on low-resource devices.
 */
public class OnboardingThemeFragment extends Fragment {

    private static final int[] CARD_IDS = {
        R.id.card_theme_warm, R.id.card_theme_cool,
        R.id.card_theme_dark, R.id.card_theme_standard,
    };
    private static final int[] CHECK_IDS = {
        R.id.iv_warm_check, R.id.iv_cool_check,
        R.id.iv_dark_check, R.id.iv_standard_check,
    };
    private static final int[] THEME_CONSTANTS = {
        AppConstants.THEME_WARM_DIM, AppConstants.THEME_COOL_MUTED,
        AppConstants.THEME_HIGH_CONTRAST, AppConstants.THEME_STANDARD,
    };
    private static final String[] THEME_NAMES = {
        "Warm and dim", "Cool and muted", "High contrast dark", "Standard",
    };

    // Stroke colors matching each theme's primary
    private static final String[] STROKE_COLORS = {
        "#BA7517", "#185FA5", "#A78BFA", "#534AB7"
    };

    private View[] cards;
    private View[] checkmarks;
    private int    selectedTheme = AppConstants.THEME_STANDARD;
    private int    selectedIndex = 3;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_theme, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        cards      = new View[CARD_IDS.length];
        checkmarks = new View[CHECK_IDS.length];

        for (int i = 0; i < CARD_IDS.length; i++) {
            cards[i]      = root.findViewById(CARD_IDS[i]);
            checkmarks[i] = root.findViewById(CHECK_IDS[i]);
            if (cards[i] == null) continue;
            cards[i].setContentDescription(THEME_NAMES[i] + " theme");
            cards[i].setFocusable(true);
            final int index = i;
            cards[i].setOnClickListener(v -> selectTheme(index));
        }

        // Read previously saved theme and pre-select it
        if (getContext() != null) {
            int saved = getContext().getSharedPreferences(
                AppConstants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getInt(AppConstants.PREF_THEME, AppConstants.THEME_STANDARD);
            for (int i = 0; i < THEME_CONSTANTS.length; i++) {
                if (THEME_CONSTANTS[i] == saved) { selectedIndex = i; selectedTheme = saved; break; }
            }
        }
        applySelectionUI(selectedIndex);
    }

    private void selectTheme(int index) {
        if (getContext() == null) return;
        selectedTheme = THEME_CONSTANTS[index];
        selectedIndex = index;

        // 1. Save immediately
        SenseShieldApp.saveTheme(getContext(), selectedTheme);

        // 2. Update selection UI
        applySelectionUI(index);
        requireActivity().recreate();
    }

    private void applySelectionUI(int index) {
        for (int i = 0; i < cards.length; i++) {
            if (cards[i] == null) continue;
            boolean selected = (i == index);
            ((MaterialCardView) cards[i]).setCardElevation(selected ? 10f : 1f);
            ((MaterialCardView) cards[i]).setStrokeWidth(selected ? 4 : 1);
            if (selected) {
                try {
                    ((MaterialCardView) cards[i]).setStrokeColor(
                        android.graphics.Color.parseColor(STROKE_COLORS[i]));
                } catch (Exception ignored) {}
            }
            if (checkmarks[i] != null)
                checkmarks[i].setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public int getSelectedTheme() { return selectedTheme; }
}
